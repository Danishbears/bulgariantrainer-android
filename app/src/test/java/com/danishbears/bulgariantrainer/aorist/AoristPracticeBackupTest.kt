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
class AoristPracticeBackupTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase
    private lateinit var practice:AoristPracticeRepository;private lateinit var content:AoristRepository;private lateinit var backup:BackupRepository
    private val name="aorist-practice-backup-${UUID.randomUUID()}.db"
    private val clock=Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),ZoneOffset.UTC)
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).addMigrations(MIGRATION_10_11).build()
        practice=AoristPracticeRepository(db,clock);content=AoristRepository(db,clock);backup=BackupRepository(context,db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun request()=UUID.randomUUID().toString()
    private fun source(file:String)=javaClass.classLoader!!.getResourceAsStream(file)!!.use{it.readBytes()}
    private suspend fun seed() {
        val p=content.preview("aorist.json",source("bulgarian_aorist_40.json"));content.confirm(p,p.changes.map{it.externalId}.toSet())
        repeat(2){VocabularyRepository(db,clock).create(WordInput("дума$it",listOf(TranslationInput("слово$it"))))}
        val flash=FlashcardRepository(db,clock);val f=flash.start(request(),FlashcardSettings(1));flash.reveal(f.id,f.question!!.id);flash.rate(f.id,f.question!!.id,true)
        val typing=TypingRepository(db,clock);val t=typing.start(request(),true);typing.submit(t.id,t.question!!.id,"Ошибка")
        SentenceRepository(db,clock).create(SentenceInput("Я здесь","Тук съм",difficulty=1))
        val sentence=SentencePracticeRepository(db,clock);val s=sentence.start(request(),1);sentence.submit(s.id,s.question!!.id,"Другой вариант");sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL)
        val aspects=AspectRepository(db,clock);val a=aspects.preview("fixture","aspects.json",source("bulgarian_aspect_pairs_100.json"));aspects.confirm(a,a.changes.map{it.externalId}.toSet())
        val aspectPractice=AspectPracticeRepository(db,clock);val started=aspectPractice.start(request(),AspectSettings(size=2));aspectPractice.reveal(started.id,started.question!!.id)
        TrainerPreferencesRepository(db).flashcardSize(7)
    }
    private suspend fun answer(s:AoristPracticeState):AoristPracticeState {
        val q=s.question!!;val e=content.get(q.verbId).entry
        return if(q.exercise==AoristExercise.TYPED)practice.submitTyped(s.id,q.id,AoristContent.answers(e,q.prompt.person).first())
        else practice.submitChoice(s.id,q.id,q.options.single{AoristContent.key(it.text) in AoristContent.answers(e,q.prompt.person).map(AoristContent::key)}.id)
    }
    private fun sign(r:JsonObject)=JsonObject(r+("sha256" to JsonPrimitive(BackupFormat.digest(r)))).toString().toByteArray()
    private suspend fun root()=BackupFormat.decode(backup.snapshot())
    @Test fun actualSchemaTenMigrationPreservesAllThirtyFourTablesHistorySettingsAndHighWaterSequences()=runBlocking<Unit> {
        seed();val before=root();db.close();context.deleteDatabase(name)
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use{sql->
            val schema=AoristContent.strict(source("com.danishbears.bulgariantrainer.data.TrainerDatabase/10.json").toString(Charsets.UTF_8)).jsonObject.getValue("database").jsonObject
            for(value in schema.getValue("entities").jsonArray){val e=value.jsonObject;val table=e.getValue("tableName").jsonPrimitive.content
                sql.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                for(index in e["indices"]?.jsonArray.orEmpty())sql.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))}
            for(table in BackupFormat.contentTables)for(v in before.getValue("tables").jsonObject.getValue(table).jsonArray){
                val row=v.jsonObject;val cols=row.keys.toList();val values:Array<Any?> = cols.map{k->val p=row.getValue(k);if(p==JsonNull)null else if(p.jsonPrimitive.isString)p.jsonPrimitive.content else p.jsonPrimitive.long}.toTypedArray()
                sql.execSQL("INSERT INTO `$table` (${cols.joinToString(","){"`$it`"}}) VALUES (${cols.joinToString(","){"?"}})",values)}
            for(table in BackupFormat.contentSequences){val value=before.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long
                sql.execSQL("DELETE FROM sqlite_sequence WHERE name=?",arrayOf(table));sql.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)",arrayOf<Any>(table,value+200))}
            sql.version=10
        }
        open();assertEquals(11,db.openHelper.writableDatabase.version);val after=root()
        assertEquals(before.getValue("tables").jsonObject.filterKeys{it in BackupFormat.contentTables},after.getValue("tables").jsonObject.filterKeys{it in BackupFormat.contentTables})
        for(table in BackupFormat.contentSequences)assertEquals(before.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long+200,after.getValue("sequences").jsonObject.getValue(table).jsonPrimitive.long)
        assertTrue(db.aoristPracticeDao().allIds().isEmpty());assertNull(db.aoristPracticeDao().preferences())
        assertNotNull(TypingRepository(db,clock).restore());assertNotNull(AspectPracticeRepository(db,clock).restore())
        val s=practice.start(request());assertEquals(20,s.total);assertEquals(1,answer(s).correct)
    }
    @Test fun fullV5ReopensTypedAndChoiceDraftsFeedbackSnapshotsSchedulesSettingsAndAllEarlierModes()=runBlocking<Unit> {
        seed();val beforeOld=root().getValue("tables").jsonObject.filterKeys{it in BackupFormat.contentTables}
        var typed=answer(practice.start(request(),AoristSettings(size=2)));typed=practice.next(typed.id,typed.question!!.id)
        practice.saveDraft(typed.id,typed.question!!.id,"Черновик без отправки",null,1);typed=practice.get(typed.id)
        val choice=answer(practice.start(request(),AoristSettings(size=2,exercise=AoristExercise.CHOICE)))
        val reveal=practice.start(request(),AoristSettings(size=1,persons=listOf("3sg")));practice.reveal(reveal.id,reveal.question!!.id);practice.next(reveal.id,reveal.question!!.id)
        val progress=db.aoristPracticeDao().progressRows();val schedules=db.aoristPracticeDao().schedules();val preferences=practice.preferences()
        val statistics=AoristStatisticsRepository(db,clock).overview()
        val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        assertEquals(240,prepared.summary.aoristForms);assertEquals(240,prepared.summary.aoristAnswers)
        assertEquals(progress.size,prepared.summary.aoristProgress);assertEquals(schedules.size,prepared.summary.aoristSchedules)
        assertEquals(5,prepared.summary.sourceFormatVersion);assertEquals(3,prepared.summary.aoristSessions);assertEquals(3,prepared.summary.aoristAttempts)
        assertEquals(40,prepared.document.getValue("tables").jsonObject.size);assertEquals(26,prepared.document.getValue("sequences").jsonObject.size)
        practice.submitTyped(typed.id,typed.question!!.id,"Другой ответ");TrainerPreferencesRepository(db).flashcardSize(99)
        backup.restore(prepared);db.close();open()
        assertArrayEquals(bytes,backup.snapshot());assertEquals(statistics,AoristStatisticsRepository(db,clock).overview());assertEquals(typed,practice.get(typed.id));assertEquals(choice,practice.get(choice.id));assertEquals(preferences,practice.preferences())
        assertEquals(progress.toSet(),db.aoristPracticeDao().progressRows().toSet());assertEquals(schedules.toSet(),db.aoristPracticeDao().schedules().toSet())
        assertEquals(beforeOld,root().getValue("tables").jsonObject.filterKeys{it in BackupFormat.contentTables})
        practice.submitChoice(choice.id,choice.question!!.id,choice.question!!.feedback!!.optionId!!);assertArrayEquals(bytes,backup.snapshot())
    }
    @Test fun originalV4ChecksumsAndExactInventoryAreValidatedBeforeExplicitContentOnlyReplacement()=runBlocking<Unit> {
        seed();answer(practice.start(request(),AoristSettings(size=1)));val current=root();val before=backup.snapshot()
        val old=JsonObject(current+mapOf("formatVersion" to JsonPrimitive(4),"schemaVersion" to JsonPrimitive(10),
            "tables" to JsonObject(current.getValue("tables").jsonObject.filterKeys{it in BackupFormat.contentTables}),
            "sequences" to JsonObject(current.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.contentSequences})))
        val prepared=backup.prepare(sign(old));assertEquals(4,prepared.summary.sourceFormatVersion);assertEquals(40,prepared.summary.aoristVerbs);assertEquals(0,prepared.summary.aoristSessions)
        assertArrayEquals(before,backup.snapshot())
        val mislabelled=sign(JsonObject(current+mapOf("formatVersion" to JsonPrimitive(4),"schemaVersion" to JsonPrimitive(10))))
        try{backup.prepare(mislabelled);fail("new tables cannot disappear")}catch(e:BackupException){assertEquals(BackupError.INVALID_FORMAT,e.code)}
        val changed=JsonObject(old+("createdAt" to JsonPrimitive("2026-11-18T12:00:00Z")))
        val wrongChecksum=JsonObject(changed+("sha256" to JsonPrimitive(BackupFormat.digest(old)))).toString().toByteArray()
        try{backup.prepare(wrongChecksum);fail("original checksum")}catch(e:BackupException){assertEquals(BackupError.CHECKSUM_MISMATCH,e.code)}
        assertArrayEquals(before,backup.snapshot());backup.restore(prepared)
        assertEquals(40L,content.list().total);assertNull(practice.restore());assertEquals(AoristSettings(),practice.preferences());assertNotNull(AspectPracticeRepository(db,clock).restore())
    }
    @Test fun malformedSnapshotsChoicesCountersAndPolicyVersionsNeverModifyLiveData()=runBlocking<Unit> {
        seed();answer(practice.start(request(),AoristSettings(size=1,exercise=AoristExercise.CHOICE)));val before=backup.snapshot();val current=root();val tables=current.getValue("tables").jsonObject
        val mutations=listOf(Triple("aorist_sessions","policyVersion",JsonPrimitive(2)),Triple("aorist_questions","snapshot",JsonPrimitive("[broken")),
            Triple("aorist_questions","formId",JsonPrimitive(999999)),Triple("aorist_questions","options",JsonPrimitive("[]")),Triple("aorist_progress","attempts",JsonPrimitive(99)))
        for((table,field,value)in mutations){val rows=tables.getValue(table).jsonArray.toMutableList();rows[0]=JsonObject(rows[0].jsonObject+(field to value))
            val invalid=sign(JsonObject(current+("tables" to JsonObject(tables+(table to JsonArray(rows))))))
            try{backup.prepare(invalid);fail("invalid $table.$field")}catch(e:BackupException){assertEquals(BackupError.INVALID_DATA,e.code)}
            assertArrayEquals(before,backup.snapshot())}
    }
    @Test fun lateRestoreScheduleFailureRollsBackAndWalSnapshotIncludesOneCommittedGradingTransaction()=runBlocking<Unit> {
        seed();val s=practice.start(request(),AoristSettings(size=1));val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO){db.withTransaction{answer(s);entered.complete(Unit);release.await()}}
        entered.await();val saving=async(Dispatchers.IO){backup.snapshot()};release.complete(Unit);writer.join()
        val prepared=backup.prepare(saving.await());assertEquals(1,prepared.summary.aoristAttempts);val before=backup.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aorist_restore_schedule BEFORE INSERT ON aorist_review_schedules BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{backup.restore(prepared);fail("rollback all modes")}catch(e:BackupException){assertEquals(BackupError.RESTORE_FAILED,e.code)}
        assertArrayEquals(before,backup.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aorist_restore_schedule")
        backup.restore(prepared);assertEquals(1,practice.restore()!!.correct)
        assertEquals("wal",db.openHelper.writableDatabase.query("PRAGMA journal_mode").use{it.moveToFirst();it.getString(0)})
    }
}
