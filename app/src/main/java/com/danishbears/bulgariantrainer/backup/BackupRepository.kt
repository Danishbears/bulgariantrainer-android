package com.danishbears.bulgariantrainer.backup

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class BackupRepository internal constructor(context: Context, private val database: TrainerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val application=context.applicationContext
    suspend fun snapshot(): ByteArray = withContext(Dispatchers.IO) {
        database.withTransaction {
            val sql=database.openHelper.writableDatabase
            val coroutine=currentCoroutineContext()
            var rowCount=0
            var encodedSize=0L
            val data=buildJsonObject { for(table in BackupFormat.tables) {
                coroutine.ensureActive()
                val columns=columns(sql,table);val order=columns.filter{it.pk>0}.sortedBy{it.pk}.joinToString(","){"`${it.name}`"}
                val rows=mutableListOf<JsonElement>()
                sql.query("SELECT * FROM `$table` ORDER BY $order").use { c -> while(c.moveToNext()) {
                    rows+=buildJsonObject { for(col in columns) { val i=c.getColumnIndexOrThrow(col.name);put(col.name,when(c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> JsonNull
                        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(c.getString(i))
                        else -> BackupFormat.fail(BackupError.INVALID_DATA)
                    }) } }
                    rowCount++;encodedSize+=rows.last().toString().toByteArray(Charsets.UTF_8).size
                    if(rowCount>BackupFormat.MAX_ROWS || encodedSize>BackupFormat.MAX_BYTES) BackupFormat.fail(BackupError.TOO_LARGE)
                } }
                put(table,JsonArray(rows))
            } }
            if(data.values.sumOf{it.jsonArray.size.toLong()}>BackupFormat.MAX_ROWS) BackupFormat.fail(BackupError.TOO_LARGE)
            val sequences=buildJsonObject { for(table in BackupFormat.sequences) {
                val value=sql.query("SELECT seq FROM sqlite_sequence WHERE name=?",arrayOf(table)).use{if(it.moveToFirst())it.getLong(0) else 0L}
                put(table,value)
            } }
            BackupFormat.encode(clock.instant(),data,sequences)
        }
    }
    /** Opaque preview retains validated content, never reopens a mutable provider URI at confirmation. */
    suspend fun prepare(bytes: ByteArray): PreparedBackup = withContext(Dispatchers.IO) {
        val root=BackupFormat.decode(bytes)
        val staging=Room.inMemoryDatabaseBuilder(application,TrainerDatabase::class.java).build()
        try {
            staging.withTransaction { install(staging.openHelper.writableDatabase,root); BackupValidation.validate(staging,root) }
            PreparedBackup(root,BackupFormat.summary(root))
        } catch(e: CancellationException) { throw e }
        catch(e: BackupException) { throw e }
        catch(_: Exception) { BackupFormat.fail(BackupError.INVALID_DATA) }
        finally { staging.close() }
    }
    /** Call only after the preview's explicit replacement confirmation. No merge/replay/regrading. */
    suspend fun restore(prepared: PreparedBackup) = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                // Invalidates mutations that entered before replacement but were waiting for this transaction.
                database.contentEpoch.incrementAndGet()
                val sql=database.openHelper.writableDatabase
                for(table in BackupFormat.tables.asReversed()) sql.execSQL("DELETE FROM `$table`")
                install(sql,prepared.document)
                BackupValidation.validate(database,prepared.document)
            }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { BackupFormat.fail(BackupError.RESTORE_FAILED) }
    }
    private data class Column(val name: String,val type: String,val required: Boolean,val pk: Int)
    private fun columns(sql: SupportSQLiteDatabase,table: String): List<Column> = sql.query("PRAGMA table_info(`$table`)").use { c ->
        buildList { while(c.moveToNext()) add(Column(c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("type")),c.getInt(c.getColumnIndexOrThrow("notnull"))!=0,c.getInt(c.getColumnIndexOrThrow("pk")))) }
    }
    private suspend fun install(sql: SupportSQLiteDatabase,root: JsonObject) {
        for(table in BackupFormat.tables) {
            currentCoroutineContext().ensureActive()
            val cols=columns(sql,table);val names=cols.map{it.name}.toSet()
            val statement=sql.compileStatement("INSERT INTO `$table` (${cols.joinToString(","){"`${it.name}`"}}) VALUES (${cols.joinToString(","){"?"}})")
            statement.use { for(element in root.getValue("tables").jsonObject.getValue(table).jsonArray) {
                val row=element as? JsonObject ?: BackupFormat.fail(BackupError.INVALID_DATA)
                if(row.keys!=names) BackupFormat.fail(BackupError.INVALID_DATA)
                statement.clearBindings()
                cols.forEachIndexed { index,col ->
                    val value=row.getValue(col.name);val i=index+1
                    when {
                        value==JsonNull && !col.required -> statement.bindNull(i)
                        value is JsonPrimitive && col.type=="TEXT" && value.isString -> statement.bindString(i,value.content)
                        value is JsonPrimitive && col.type=="INTEGER" && !value.isString && value.longOrNull!=null -> statement.bindLong(i,value.long)
                        else -> BackupFormat.fail(BackupError.INVALID_DATA)
                    }
                }
                statement.executeInsert()
            } }
        }
        for(table in BackupFormat.sequences) {
            val seq=root.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long
            val max=sql.query("SELECT COALESCE(MAX(id),0) FROM `$table`").use{it.moveToFirst();it.getLong(0)}
            if(seq<max) BackupFormat.fail(BackupError.INVALID_DATA)
            sql.execSQL("DELETE FROM sqlite_sequence WHERE name=?",arrayOf(table))
            sql.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)",arrayOf(table,seq))
        }
        sql.query("PRAGMA foreign_key_check").use{if(it.moveToFirst()) BackupFormat.fail(BackupError.INVALID_DATA)}
    }
}
