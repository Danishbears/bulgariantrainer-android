package com.danishbears.bulgariantrainer.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.*
import com.danishbears.bulgariantrainer.review.StudyScope
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
import java.time.*
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var backup: BackupRepository
    private val name="backup-${UUID.randomUUID()}.db"
    private val clock=Clock.fixed(Instant.parse("2026-10-20T12:30:00Z"),ZoneOffset.UTC)
    @Before fun setup() { context=ApplicationProvider.getApplicationContext();open() }
    private fun open() { db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build();backup=BackupRepository(context,db,clock) }
    @After fun teardown() { db.close();context.deleteDatabase(name) }
    private suspend fun word(bg: String="къща")=VocabularyRepository(db,clock).create(WordInput(bg,listOf(TranslationInput("дом"),TranslationInput("жилище")),notes="Кириллица\n\"цитата\""))
    private fun request()=UUID.randomUUID().toString()
    private fun signed(root: JsonObject)=JsonObject(root+("sha256" to JsonPrimitive(BackupFormat.digest(root)))).toString().toByteArray()
    private suspend fun change(table: String, update: (JsonObject)->JsonObject): ByteArray {
        val root=BackupFormat.decode(backup.snapshot());val tables=root.getValue("tables").jsonObject
        val rows=tables.getValue(table).jsonArray.toMutableList();rows[0]=update(rows.first().jsonObject)
        return signed(JsonObject(root+("tables" to JsonObject(tables+(table to JsonArray(rows))))))
    }
    private suspend fun reject(bytes: ByteArray,code: BackupError) {
        val before=backup.snapshot()
        try { backup.prepare(bytes);fail("accepted malformed backup") }catch(e: BackupException){assertEquals(code,e.code)}
        assertArrayEquals(before,backup.snapshot())
    }
    @Test fun emptyRoundTripIncludesEveryTableAndUsesDefaultsWithoutInventingRecords()=runBlocking<Unit> {
        val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        assertEquals(BackupFormat.tables.toSet(),prepared.document.getValue("tables").jsonObject.keys)
        assertEquals(0,prepared.summary.words);assertFalse(prepared.summary.preferences)
        word();backup.restore(prepared);assertArrayEquals(bytes,backup.snapshot())
    }
    @Test fun fullRoundTripPreservesFourModeProgressFeedbackRawAnswersSettingsAndRelationships()=runBlocking<Unit> {
        repeat(4){word("дума$it")}
        SentenceRepository(db,clock).create(SentenceInput("Я здесь.","Тук съм.",listOf("Аз съм тук."),explanation="Пояснение\nна две строки",difficulty=2,topic="Разговор"))
        val flash=FlashcardRepository(db,clock,Random(3));var f=flash.start(request(),FlashcardSettings(2,FlashcardDirection.RU_TO_BG))
        flash.reveal(f.id,f.question!!.id);f=flash.rate(f.id,f.question!!.id,true);f=flash.reveal(f.id,f.question!!.id)
        val typing=TypingRepository(db,clock,Random(3));var t=typing.start(request(),true)
        val stored=db.typingDao().question(t.id,t.question!!.id)!!
        val answer=if(stored.direction=="BG_TO_RU")Json.parseToJsonElement(stored.russianJson).jsonArray.first().jsonPrimitive.content else stored.bulgarian
        t=typing.submit(t.id,t.question!!.id,"  $answer  ")
        val sentence=SentencePracticeRepository(db,clock,Random(3));var s=sentence.start(request(),1)
        s=sentence.submit(s.id,s.question!!.id," Аз тука \n ")
        val prefs=TrainerPreferencesRepository(db);prefs.flashcardSize(7);prefs.flashcardDirection(FlashcardDirection.MIXED);prefs.typingScope(StudyScope.DUE);prefs.sentenceScope(StudyScope.NEW)
        val stats=StatisticsRepository(db,clock).overview();val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        assertEquals(3,prepared.summary.sessions);assertEquals(3,prepared.summary.attempts);assertTrue(prepared.summary.preferences)
        flash.rate(f.id,f.question!!.id,false);typing.next(t.id,t.question!!.id);sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL)
        word("лишнее");prefs.flashcardSize(99)
        backup.restore(prepared)
        assertArrayEquals(bytes,backup.snapshot());assertEquals(f,flash.get(f.id));assertEquals(t,typing.get(t.id));assertEquals(s,sentence.get(s.id))
        assertEquals(stats,StatisticsRepository(db,clock).overview());assertEquals(7,prefs.get().flashcardSize)
        // Resume the same pending sentence; one final assessment, not a second submitted attempt.
        sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL);sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL)
        assertEquals(stats.reviewAttempts+1,StatisticsRepository(db,clock).overview().reviewAttempts)
        assertEquals(1,db.sentenceDao().progress(db.sentenceDao().question(s.id,s.question!!.id)!!.sentenceId)!!.attempts)
    }
    @Test fun failedLateInsertRollsBackDeletedContentSettingsSchedulesAndSqliteSequences()=runBlocking<Unit> {
        word();val original=backup.prepare(backup.snapshot());word("новое");TrainerPreferencesRepository(db).flashcardSize(42)
        val before=backup.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON trainer_preferences BEGIN SELECT RAISE(ABORT,'injected restore failure'); END")
        // Include settings in the replacement so failure occurs after multiple earlier tables were inserted.
        val withSettings=JsonObject(original.document+("tables" to JsonObject(original.document.getValue("tables").jsonObject+("trainer_preferences" to BackupFormat.decode(before).getValue("tables").jsonObject.getValue("trainer_preferences")))))
        val prepared=backup.prepare(signed(withSettings))
        try{backup.restore(prepared);fail("expected rollback")}catch(e:BackupException){assertEquals(BackupError.RESTORE_FAILED,e.code)}
        assertArrayEquals(before,backup.snapshot())
    }
    @Test fun invalidForeignKeyIsRejectedInStagingBeforeAnyLocalDeletion()=runBlocking<Unit> {
        word();reject(change("translations"){JsonObject(it+("word_id" to JsonPrimitive(99999)))},BackupError.INVALID_DATA)
    }
    @Test fun invalidBooleanAndDuplicateIdsAreRejectedDespiteRecomputedChecksum()=runBlocking<Unit> {
        word();reject(change("words"){JsonObject(it+("archived" to JsonPrimitive(2)))},BackupError.INVALID_DATA)
        val root=BackupFormat.decode(backup.snapshot());val tables=root.getValue("tables").jsonObject;val rows=tables.getValue("words").jsonArray
        reject(signed(JsonObject(root+("tables" to JsonObject(tables+("words" to JsonArray(rows+rows)))))),BackupError.INVALID_DATA)
    }
    @Test fun malformedSnapshotAndOutOfRangeProgressNeverReachLiveData()=runBlocking<Unit> {
        word();val repo=FlashcardRepository(db,clock);val f=repo.start(request(),FlashcardSettings(1));repo.reveal(f.id,f.question!!.id);repo.rate(f.id,f.question!!.id,true)
        reject(change("flashcard_cards"){JsonObject(it+("russian_json" to JsonPrimitive("[broken")))},BackupError.INVALID_DATA)
        reject(change("recognition_progress"){JsonObject(it+("attempts" to JsonPrimitive(-1)))},BackupError.INVALID_DATA)
        reject(change("word_review_schedules"){JsonObject(it+("interval_days" to JsonPrimitive(366)))},BackupError.INVALID_DATA)
    }
    @Test fun deeplyNestedEncodedAnswerJsonCannotBypassTheOuterDocumentDepthLimit()=runBlocking<Unit> {
        word();FlashcardRepository(db,clock).start(request(),FlashcardSettings(1))
        val deep="[".repeat(50_000)+"0"+"]".repeat(50_000)
        reject(change("flashcard_cards"){JsonObject(it+("russian_json" to JsonPrimitive(deep)))},BackupError.INVALID_FORMAT)
    }
    @Test fun csvImportedSentenceZeroBasedAlternativePositionsAndSourceReceiptsRoundTripUnchanged()=runBlocking<Unit> {
        val transfer=com.danishbears.bulgariantrainer.transfer.ContentTransferRepository(db,clock)
        val csv="id,russian,bulgarian,alternativeTranslations,grammaticalExplanation,difficulty,topic\n1,Я здесь.,Тук съм.,\"[\"\"Аз съм тук.\"\"]\",Пояснение,2,Разговор"
        val preview=transfer.preview(com.danishbears.bulgariantrainer.transfer.ContentKind.SENTENCES,"import.csv",csv.toByteArray())
        transfer.confirm(preview.copy(rows=preview.rows.map{it.copy(selected=true)}))
        val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        val alternatives=prepared.document.getValue("tables").jsonObject.getValue("sentence_alternatives").jsonArray
        assertEquals(0L,alternatives.single().jsonObject.getValue("position").jsonPrimitive.long)
        assertEquals(1,prepared.document.getValue("tables").jsonObject.getValue("import_receipts").jsonArray.size)
        backup.restore(prepared);assertArrayEquals(bytes,backup.snapshot())
    }
    @Test fun invalidSessionEnumAndNonContiguousQueueAreRejected()=runBlocking<Unit> {
        word();FlashcardRepository(db,clock).start(request(),FlashcardSettings(1))
        reject(change("flashcard_sessions"){JsonObject(it+("status" to JsonPrimitive("INVALID")))},BackupError.INVALID_DATA)
        reject(change("flashcard_cards"){JsonObject(it+("position" to JsonPrimitive(2)))},BackupError.INVALID_DATA)
    }
    @Test fun missingTableUnknownColumnAndSqliteNumericTypeCoercionAreRejected()=runBlocking<Unit> {
        word();val root=BackupFormat.decode(backup.snapshot());val tables=root.getValue("tables").jsonObject
        reject(signed(JsonObject(root+("tables" to JsonObject(tables-"translations")))),BackupError.INVALID_FORMAT)
        reject(change("words"){JsonObject(it+("unexpected" to JsonPrimitive("value")))},BackupError.INVALID_DATA)
        reject(change("words"){JsonObject(it+("id" to JsonPrimitive("1")))},BackupError.INVALID_DATA)
    }
    @Test fun sequenceHighWaterMarksSurviveDeletionBackupRestoreAndNewInserts()=runBlocking<Unit> {
        val a=word();val deleted=word("удалить");VocabularyRepository(db).delete(deleted.id)
        val prepared=backup.prepare(backup.snapshot());word("лишнее");backup.restore(prepared)
        val next=word("после восстановления");assertTrue(next.id>deleted.id);assertTrue(next.id>a.id)
    }
    @Test fun invalidSequenceCannotCauseIdentifierReuse()=runBlocking<Unit> {
        word();val root=BackupFormat.decode(backup.snapshot());val seq=root.getValue("sequences").jsonObject
        reject(signed(JsonObject(root+("sequences" to JsonObject(seq+("words" to JsonPrimitive(0)))))),BackupError.INVALID_DATA)
    }
    @Test fun unknownLegacyDirectionAndIncompleteHistoricalLogsArePreservedWithoutFabrication()=runBlocking<Unit> {
        val w=word();db.openHelper.writableDatabase.execSQL("INSERT INTO typing_progress VALUES(?,'UNKNOWN',4,2,0,1000)",arrayOf(w.id))
        db.openHelper.writableDatabase.execSQL("INSERT INTO recognition_progress VALUES(?,20,10,0,1000)",arrayOf(w.id))
        val bytes=backup.snapshot();backup.restore(backup.prepare(bytes));assertArrayEquals(bytes,backup.snapshot())
        assertEquals(4,db.typingDao().progress(w.id,"UNKNOWN")!!.attempts)
        assertEquals(0L,StatisticsRepository(db,clock).overview().reviewAttempts)
    }
    @Test fun consistentWalSnapshotWaitsForAnEntireRelatedWriteTransaction()=runBlocking<Unit> {
        val a=word();val b=word("котка");val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writing=launch(Dispatchers.IO){db.withTransaction {
            db.openHelper.writableDatabase.execSQL("UPDATE words SET notes='both' WHERE id=?",arrayOf(a.id));entered.complete(Unit);release.await()
            db.openHelper.writableDatabase.execSQL("UPDATE words SET notes='both' WHERE id=?",arrayOf(b.id))
        }}
        entered.await();val exporting=async(Dispatchers.IO){backup.snapshot()};release.complete(Unit);writing.join()
        val prepared=backup.prepare(exporting.await());assertTrue(prepared.document.getValue("tables").jsonObject.getValue("words").jsonArray.all{it.jsonObject["notes"]==JsonPrimitive("both")})
        assertEquals("wal",db.openHelper.writableDatabase.query("PRAGMA journal_mode").use{it.moveToFirst();it.getString(0)})
    }
    @Test fun queuedOldMutationCannotWriteIntoReplacementGeneration()=runBlocking<Unit> {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val called=CompletableDeferred<Unit>()
        val holder=launch(Dispatchers.IO){db.withTransaction { entered.complete(Unit);release.await() }};entered.await()
        val old=async(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED){try{db.withContentTransaction { called.complete(Unit) };false}catch(_:IllegalStateException){true}}
        db.contentEpoch.incrementAndGet();release.complete(Unit);holder.join();assertTrue(old.await());assertFalse(called.isCompleted)
    }
    @Test fun preparedPreviewRetainsTheValidatedBytesNotLaterChangesToInput()=runBlocking<Unit> {
        word();val bytes=backup.snapshot();val prepared=backup.prepare(bytes);bytes.fill(0)
        word("другое");backup.restore(prepared);assertEquals(1,VocabularyRepository(db).list().size)
    }
    @Test fun restoredPendingSentenceMetadataAndAlternativesSurvivePhysicalDatabaseReopen()=runBlocking<Unit> {
        SentenceRepository(db,clock).create(SentenceInput("Я здесь.","Тук съм.",listOf("Аз съм тук."),difficulty=null,topic="Тема"))
        val repo=SentencePracticeRepository(db,clock);val s=repo.start(request(),1);val pending=repo.submit(s.id,s.question!!.id,"Неутверждённый вариант")
        val bytes=backup.snapshot();backup.restore(backup.prepare(bytes));db.close();open()
        assertEquals(pending,SentencePracticeRepository(db,clock).restore());assertEquals(0L,StatisticsRepository(db,clock).overview().reviewAttempts)
    }
}
