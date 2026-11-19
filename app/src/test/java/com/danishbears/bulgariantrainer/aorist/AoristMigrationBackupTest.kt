package com.danishbears.bulgariantrainer.aorist

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.sentences.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.transfer.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristMigrationBackupTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase;private lateinit var backup:BackupRepository
    private val name="aorist-migration-${UUID.randomUUID()}.db"
    private val clock=Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"),ZoneOffset.UTC)
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).addMigrations(MIGRATION_9_10, MIGRATION_10_11).build();backup=BackupRepository(context,db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun source(file:String)=javaClass.classLoader!!.getResourceAsStream(file)!!.use{it.readBytes()}
    private fun request()=UUID.randomUUID().toString()
    private suspend fun aorist(){val repo=AoristRepository(db,clock);val p=repo.preview("aorist.json",source("bulgarian_aorist_40.json"));repo.confirm(p,p.changes.map{it.externalId}.toSet())}
    private suspend fun seedOtherModes() {
        repeat(2){VocabularyRepository(db,clock).create(WordInput("дума$it",listOf(TranslationInput("слово$it"))))}
        val transfer=ContentTransferRepository(db);val preview=transfer.preview(ContentKind.WORDS,"words.txt","къща - дом".toByteArray())
        transfer.confirm(preview.copy(rows=preview.rows.map{it.copy(selected=it.selectable)}))
        val flash=FlashcardRepository(db,clock);val f=flash.start(request(),FlashcardSettings(1));flash.reveal(f.id,f.question!!.id);flash.rate(f.id,f.question!!.id,true)
        val typing=TypingRepository(db,clock);val t=typing.start(request(),true);typing.submit(t.id,t.question!!.id,"Ошибка")
        SentenceRepository(db,clock).create(SentenceInput("Я здесь","Тук съм",difficulty=1))
        val sentence=SentencePracticeRepository(db,clock);val s=sentence.start(request(),1);sentence.submit(s.id,s.question!!.id,"Другой вариант");sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL)
        TrainerPreferencesRepository(db).flashcardSize(7)
        val aspects=AspectRepository(db,clock);val p=aspects.preview("fixture","aspects.json",source("bulgarian_aspect_pairs_100.json"));aspects.confirm(p,p.changes.map{it.externalId}.toSet())
        val practice=AspectPracticeRepository(db,clock);val a=practice.start(request(),AspectSettings(size=2));practice.reveal(a.id,a.question!!.id)
    }
    private fun sign(r:JsonObject)=JsonObject(r+("sha256" to JsonPrimitive(BackupFormat.digest(r)))).toString().toByteArray()
    private suspend fun root()=BackupFormat.decode(backup.snapshot())
    @Test fun schemaNineUpgradePreservesAllTwentyNineOldTablesSequencesAndResumableHistory()=runBlocking<Unit> {
        seedOtherModes();val before=root();db.close();context.deleteDatabase(name)
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use{sql->
            val schema=AoristContent.strict(source("com.danishbears.bulgariantrainer.data.TrainerDatabase/9.json").toString(Charsets.UTF_8)).jsonObject.getValue("database").jsonObject
            for(value in schema.getValue("entities").jsonArray){val e=value.jsonObject;val table=e.getValue("tableName").jsonPrimitive.content
                sql.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                for(index in e["indices"]?.jsonArray.orEmpty())sql.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
            }
            for(table in BackupFormat.previousTables)for(v in before.getValue("tables").jsonObject.getValue(table).jsonArray) {
                val row=v.jsonObject;val cols=row.keys.toList();val values=cols.map{key->val p=row.getValue(key);if(p==JsonNull)null else if(p.jsonPrimitive.isString)p.jsonPrimitive.content else p.jsonPrimitive.long}.toTypedArray()
                sql.execSQL("INSERT INTO `$table` (${cols.joinToString(","){"`$it`"}}) VALUES (${cols.joinToString(","){"?"}})",values)
            }
            for(table in BackupFormat.previousSequences){val old=before.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long
                sql.execSQL("DELETE FROM sqlite_sequence WHERE name=?",arrayOf(table));sql.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)",arrayOf(table,old+1000))}
            sql.version=9
        }
        open();assertEquals(11,db.openHelper.writableDatabase.version)
        val after=root();assertEquals(before.getValue("tables").jsonObject.filterKeys{it in BackupFormat.previousTables},after.getValue("tables").jsonObject.filterKeys{it in BackupFormat.previousTables})
        for(table in BackupFormat.previousSequences)assertEquals(before.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long+1000,after.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long)
        assertTrue(db.aoristDao().datasets().isEmpty());assertNotNull(TypingRepository(db,clock).restore());assertNotNull(AspectPracticeRepository(db,clock).restore())
        aorist();assertEquals(40L,AoristRepository(db,clock).list().total);db.close();open();assertEquals(40L,AoristRepository(db,clock).list().total)
    }
    @Test fun fullV4RoundTripReopensAoristContentWithoutChangingAnyOtherModes()=runBlocking<Unit> {
        seedOtherModes();val old=root().getValue("tables").jsonObject.filterKeys{it in BackupFormat.previousTables};aorist()
        val repo=AoristRepository(db,clock);val first=repo.list().rows.first();repo.archive(first.id,first.version,true)
        val bytes=backup.snapshot();val prepared=backup.prepare(bytes);assertEquals(5,prepared.summary.sourceFormatVersion);assertEquals(40,prepared.summary.aoristVerbs);assertEquals(1,prepared.summary.aoristDatasets)
        val document=prepared.document;assertEquals(40,document.getValue("tables").jsonObject.size);assertEquals(26,document.getValue("sequences").jsonObject.size)
        val content=repo.export("bg-aorist-core-40",true);val forms=db.aoristDao().forms(first.id)
        repo.create("manual",JsonObject(first.entry-"id"));backup.restore(prepared);db.close();open()
        assertArrayEquals(bytes,backup.snapshot());assertArrayEquals(content,AoristRepository(db,clock).export("bg-aorist-core-40",true))
        assertEquals(forms,db.aoristDao().forms(first.id));assertTrue(AoristRepository(db,clock).get(first.id).archived)
        assertEquals(old,root().getValue("tables").jsonObject.filterKeys{it in BackupFormat.previousTables})
    }
    @Test fun originalV3AuthenticatesBeforeExplicitReplacementAndNeverIgnoresNewTables()=runBlocking<Unit> {
        seedOtherModes();aorist();val current=root();val original=JsonObject(current+mapOf("formatVersion" to JsonPrimitive(3),"schemaVersion" to JsonPrimitive(9),
            "tables" to JsonObject(current.getValue("tables").jsonObject.filterKeys{it in BackupFormat.previousTables}),
            "sequences" to JsonObject(current.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.previousSequences})))
        val before=backup.snapshot();val prepared=backup.prepare(sign(original));assertEquals(3,prepared.summary.sourceFormatVersion);assertEquals(0,prepared.summary.aoristVerbs)
        assertArrayEquals(before,backup.snapshot()) // Preview has not replaced anything.
        val relabelled=sign(JsonObject(current+mapOf("formatVersion" to JsonPrimitive(3),"schemaVersion" to JsonPrimitive(9))))
        try{backup.prepare(relabelled);fail("must not trim new tables")}catch(e:BackupException){assertEquals(BackupError.INVALID_FORMAT,e.code)}
        val changed=JsonObject(original+("createdAt" to JsonPrimitive("2026-11-17T12:00:00Z")))
        val bad=JsonObject(changed+("sha256" to JsonPrimitive(BackupFormat.digest(original)))).toString().toByteArray()
        try{backup.prepare(bad);fail("original checksum")}catch(e:BackupException){assertEquals(BackupError.CHECKSUM_MISMATCH,e.code)}
        assertArrayEquals(before,backup.snapshot());backup.restore(prepared)
        assertEquals(0L,AoristRepository(db,clock).list().total);assertNotNull(AspectPracticeRepository(db,clock).restore())
    }
    @Test fun badAoristRelationshipsAndLateRestoreFailureLeaveExistingDataIntact()=runBlocking<Unit> {
        seedOtherModes();aorist();val bytes=backup.snapshot();val prepared=backup.prepare(bytes);val root=prepared.document;val tables=root.getValue("tables").jsonObject
        val answers=tables.getValue("aorist_answers").jsonArray.toMutableList();answers[0]=JsonObject(answers[0].jsonObject+("formId" to JsonPrimitive(999999)))
        val bad=sign(JsonObject(root+("tables" to JsonObject(tables+("aorist_answers" to JsonArray(answers))))))
        try{backup.prepare(bad);fail("missing parent")}catch(e:BackupException){assertEquals(BackupError.INVALID_DATA,e.code)}
        assertArrayEquals(bytes,backup.snapshot())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aorist_restore BEFORE INSERT ON aorist_receipts BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{backup.restore(prepared);fail("transaction rollback")}catch(e:BackupException){assertEquals(BackupError.RESTORE_FAILED,e.code)}
        assertArrayEquals(bytes,backup.snapshot())
    }
    @Test fun walSnapshotWaitsForCommittedRelatedAoristChanges()=runBlocking<Unit> {
        aorist();val repo=AoristRepository(db,clock);val old=repo.list().rows.first();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO){db.withTransaction{repo.update(old.id,old.version,JsonObject(old.entry+("note" to JsonPrimitive("same transaction"))));entered.complete(Unit);release.await()}}
        entered.await();val saving=async(Dispatchers.IO){backup.snapshot()};release.complete(Unit);writer.join()
        val prepared=backup.prepare(saving.await());val row=prepared.document.getValue("tables").jsonObject.getValue("aorist_verbs").jsonArray.first().jsonObject
        assertEquals(JsonPrimitive("same transaction"),row["note"]);backup.restore(prepared)
        assertEquals("wal",db.openHelper.writableDatabase.query("PRAGMA journal_mode").use{it.moveToFirst();it.getString(0)})
    }
}
