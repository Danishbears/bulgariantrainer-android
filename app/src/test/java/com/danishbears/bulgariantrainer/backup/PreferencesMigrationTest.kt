package com.danishbears.bulgariantrainer.backup

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PreferencesMigrationTest {
    @Test fun exportedV6UpgradeAddsOnlyEmptyPreferencesAndRetainsContentAndHighWater()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="preferences-${UUID.randomUUID()}.db"
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { sql ->
            val fixture=javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/6.json")!!.bufferedReader().use{it.readText()}
            val entities=Json.parseToJsonElement(fixture).jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
            for(e in entities){val row=e.jsonObject;val table=row.getValue("tableName").jsonPrimitive.content;sql.execSQL(row.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table));row["indices"]?.jsonArray?.forEach{sql.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))}}
            sql.execSQL("INSERT INTO words(id,bulgarian_text,normalized_text,archived,created_at,updated_at) VALUES(7,'котка','котка',0,1000,1000)");sql.version=6
        }
        val db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).addMigrations(MIGRATION_6_7).build()
        try {assertEquals(7,db.openHelper.writableDatabase.version);assertEquals(7L,VocabularyRepository(db).list().single().id);assertNull(db.preferencesDao().get());assertEquals(20,TrainerPreferencesRepository(db).get().flashcardSize)}
        finally {db.close();context.deleteDatabase(name)}
    }
}
