package com.danishbears.bulgariantrainer.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.data.*
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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectBackupIntegrationTest {
    private lateinit var context:Context
    private lateinit var db:TrainerDatabase
    private lateinit var content:AspectRepository
    private lateinit var practice:AspectPracticeRepository
    private lateinit var backup:BackupRepository
    private val filename="aspect-backup-${UUID.randomUUID()}.db"
    private val clock=Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"),ZoneOffset.UTC)
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){
        db=Room.databaseBuilder(context,TrainerDatabase::class.java,filename).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).addMigrations(MIGRATION_8_9).build()
        content=AspectRepository(db,clock);practice=AspectPracticeRepository(db,clock,Random(9));backup=BackupRepository(context,db,clock)
    }
    @After fun close(){db.close();context.deleteDatabase(filename)}
    private fun source()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.use{it.readBytes()}
    private fun request()=UUID.randomUUID().toString()
    private suspend fun seed(){val p=content.preview("fixture","bulgarian_aspect_pairs_100.json",source());assertTrue(p.issues.isEmpty());content.confirm(p,p.changes.map{it.externalId}.toSet())}
    private suspend fun start(size:Int=2,direction:AspectDirection=AspectDirection.MIXED)=practice.start(request(),AspectSettings(size,listOf(1,2,3),direction=direction,scope=StudyScope.MIXED,namespace="fixture"))
    private suspend fun answer(s:AspectPracticeState):AspectPracticeState {
        val q=s.question!!;val stored=db.aspectPracticeDao().graph(s.id)!!.questions.single{it.question.id==q.id}.question
        val e=AspectContent.strict(stored.snapshot).jsonObject
        val raw=AspectContent.answers(e,if(q.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)"acceptedPerfective" else "acceptedImperfective").last()
        return practice.submit(s.id,q.id," \t$raw\n")
    }
    private fun sign(root:JsonObject)=JsonObject(root+("sha256" to JsonPrimitive(BackupFormat.digest(root)))).toString().toByteArray()
    private fun historical(root:JsonObject,version:Int):ByteArray {
        val tables=if(version==1)BackupFormat.legacyTables else BackupFormat.legacyTables+BackupFormat.aspectTables
        val sequences=if(version==1)BackupFormat.legacySequences else BackupFormat.legacySequences+BackupFormat.aspectSequences
        return sign(JsonObject(root+mapOf("formatVersion" to JsonPrimitive(version),"schemaVersion" to JsonPrimitive(if(version==1)7 else 8),
            "tables" to JsonObject(root.getValue("tables").jsonObject.filterKeys{it in tables}),"sequences" to JsonObject(root.getValue("sequences").jsonObject.filterKeys{it in sequences}))))
    }
    @Test fun all100ReimportAndContentRoundTripRetainExternalIdentityAndLearningHistory()=runBlocking<Unit>{
        seed();val ids=content.list(AspectFilter(namespace="fixture"),limit=100).rows.associate{AspectContent.string(it.entry,"id") to it.id}
        assertEquals(100,ids.size);val s=answer(start());val schedules=db.aspectPracticeDao().schedules()
        val p=content.preview("fixture","same.json",source());assertEquals(100,p.changes.count{it.kind=="UNCHANGED"})
        content.confirm(p,p.changes.map{it.externalId}.toSet());assertEquals(ids,content.list(AspectFilter(namespace="fixture"),limit=100).rows.associate{AspectContent.string(it.entry,"id") to it.id})
        assertEquals(s,practice.get(s.id));assertEquals(schedules,db.aspectPracticeDao().schedules())
        val exported=content.export("fixture",true);val parsed=AspectContent.parse(exported);assertEquals(100,parsed.entries.size);assertTrue(parsed.issues.isEmpty())
        val other=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build()
        try {
            val target=AspectRepository(other,clock);val preview=target.preview("fixture","roundtrip.json",exported);target.confirm(preview,preview.changes.map{it.externalId}.toSet())
            assertEquals(AspectContent.canonical(AspectContent.strict(exported.toString(Charsets.UTF_8))),AspectContent.canonical(AspectContent.strict(target.export("fixture",true).toString(Charsets.UTF_8))))
            assertTrue(other.aspectPracticeDao().allIds().isEmpty());assertTrue(other.aspectPracticeDao().progressRows().isEmpty())
        }finally{other.close()}
    }
    @Test fun fullV3RoundTripReopensAllAspectGraphsMetadataExamplesSettingsAndIndependentProgress()=runBlocking<Unit>{
        seed();VocabularyRepository(db,clock).create(WordInput("дума",listOf(TranslationInput("слово"))));TrainerPreferencesRepository(db).flashcardSize(17)
        var mixed=answer(start());mixed=practice.next(mixed.id,mixed.question!!.id);mixed=practice.reveal(mixed.id,mixed.question!!.id)
        val reverse=start(direction=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE);val pending=practice.submit(reverse.id,reverse.question!!.id,"  Не совпало  ")
        val forward=start(direction=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)
        val graph=db.aspectPracticeDao().graph(mixed.id)!!
        assertEquals(setOf(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE.name,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE.name),graph.questions.map{it.question.direction}.toSet())
        assertEquals(2,graph.questions.map{it.question.pairId}.distinct().size)
        val pair=content.get(graph.questions.first().question.pairId)
        val edited=content.update(pair.id,pair.version,JsonObject(pair.entry+("note" to JsonPrimitive("Изменено после старта"))));content.archive(edited.id,edited.version,true)
        val expectedContent=content.export("fixture",true);val beforeStats=AspectStatisticsRepository(db,clock).overview();val beforeLegacy=StatisticsRepository(db,clock).overview()
        val bytes=backup.snapshot();val root=BackupFormat.decode(bytes);val prepared=backup.prepare(bytes)
        assertEquals(JsonPrimitive(3),root["formatVersion"]);assertEquals(JsonPrimitive(9),root["schemaVersion"]);assertEquals(29,root.getValue("tables").jsonObject.size);assertEquals(19,root.getValue("sequences").jsonObject.size)
        assertEquals(100,prepared.summary.aspectPairs);assertEquals(3,prepared.summary.sessions);assertEquals(3,prepared.summary.attempts);assertEquals(3,prepared.summary.sourceFormatVersion)
        answer(forward);TrainerPreferencesRepository(db).flashcardSize(99);VocabularyRepository(db,clock).create(WordInput("лишнее",listOf(TranslationInput("лишнее"))))
        backup.restore(prepared);assertArrayEquals(bytes,backup.snapshot());db.close();open()
        assertArrayEquals(bytes,backup.snapshot());assertArrayEquals(expectedContent,content.export("fixture",true))
        assertEquals(mixed,practice.get(mixed.id));assertEquals(pending,practice.get(pending.id));assertEquals(forward,practice.restore())
        assertEquals(graph,db.aspectPracticeDao().graph(mixed.id));assertEquals(beforeStats,AspectStatisticsRepository(db,clock).overview());assertEquals(beforeLegacy,StatisticsRepository(db,clock).overview());assertEquals(17,TrainerPreferencesRepository(db).get().flashcardSize)
        practice.submit(pending.id,pending.question!!.id,"  Не совпало  ");assertEquals(beforeStats,AspectStatisticsRepository(db,clock).overview())
        assertEquals(0,AspectContent.parse(content.export("fixture",true)).issues.size)
    }
    @Test fun oldV1AndV2AreExplicitReplacementsWithOriginalVersionInPreview()=runBlocking<Unit>{
        seed();answer(start());val full=BackupFormat.decode(backup.snapshot());val current=backup.prepare(backup.snapshot())
        for(version in listOf(1,2)) {
            val old=backup.prepare(historical(full,version));assertEquals(version,old.summary.sourceFormatVersion)
            assertEquals(if(version==1)0 else 100,old.summary.aspectPairs)
            assertEquals(0,old.document.getValue("tables").jsonObject.getValue("aspect_attempts").jsonArray.size)
            backup.restore(old);assertEquals(if(version==1)0L else 100L,content.list().total);assertNull(practice.restore())
            val stats=AspectStatisticsRepository(db,clock).overview();assertEquals(0L,stats.answered);assertEquals(0L,stats.dueForReview)
            backup.restore(current)
        }
    }
    @Test fun futureFormatsAndRelabelledNewerDataCannotBeSilentlyDropped()=runBlocking<Unit>{
        seed();answer(start());val before=backup.snapshot();val root=BackupFormat.decode(before)
        val invalid=listOf(
            sign(JsonObject(root+mapOf("formatVersion" to JsonPrimitive(4),"futureSetting" to JsonPrimitive("must retain")))),
            sign(JsonObject(root+("schemaVersion" to JsonPrimitive(10)))),
            sign(JsonObject(root+mapOf("formatVersion" to JsonPrimitive(2),"schemaVersion" to JsonPrimitive(8)))),
            sign(JsonObject(root+("futureSetting" to JsonPrimitive("must retain")))),
            content.export("fixture",true)
        )
        for((i,bytes)in invalid.withIndex()) {
            try{backup.prepare(bytes);fail("unsupported or relabelled backup")}catch(e:BackupException){assertEquals(if(i<2)BackupError.UNSUPPORTED_VERSION else BackupError.INVALID_FORMAT,e.code)}
            assertArrayEquals(before,backup.snapshot())
        }
        val old=historical(root,1).toString(Charsets.UTF_8).replace("\"createdAt\":\"2026-11-02T12:00:00Z\"","\"createdAt\":\"2026-11-01T12:00:00Z\"").toByteArray()
        try{backup.prepare(old);fail("original legacy checksum")}catch(e:BackupException){assertEquals(BackupError.CHECKSUM_MISMATCH,e.code)}
        assertArrayEquals(before,backup.snapshot())
    }
    @Test fun malformedAspectSnapshotsAndRelationshipsNeverChangeLiveData()=runBlocking<Unit>{
        seed();answer(start());val before=backup.snapshot();val root=BackupFormat.decode(before);val tables=root.getValue("tables").jsonObject
        for((field,value)in listOf("snapshot" to JsonPrimitive("[broken"),"pairId" to JsonPrimitive(999999),"contentHash" to JsonPrimitive("wrong"))) {
            val questions=tables.getValue("aspect_questions").jsonArray.toMutableList();questions[0]=JsonObject(questions[0].jsonObject+(field to value))
            val bad=sign(JsonObject(root+("tables" to JsonObject(tables+("aspect_questions" to JsonArray(questions))))))
            try{backup.prepare(bad);fail("invalid aspect graph")}catch(e:BackupException){assertEquals(BackupError.INVALID_DATA,e.code)}
            assertArrayEquals(before,backup.snapshot())
        }
    }
    @Test fun lateAspectScheduleFailureRollsBackAllDeletedRowsAndSequenceChanges()=runBlocking<Unit>{
        seed();answer(start());val prepared=backup.prepare(backup.snapshot());answer(start());TrainerPreferencesRepository(db).flashcardSize(42)
        val before=backup.snapshot();val stats=AspectStatisticsRepository(db,clock).overview()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aspect_restore BEFORE INSERT ON aspect_review_schedules BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{backup.restore(prepared);fail("rollback required")}catch(e:BackupException){assertEquals(BackupError.RESTORE_FAILED,e.code)}
        assertArrayEquals(before,backup.snapshot());assertEquals(stats,AspectStatisticsRepository(db,clock).overview())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aspect_restore")
        backup.restore(prepared);assertEquals(1L,AspectStatisticsRepository(db,clock).overview().answered)
    }
    @Test fun committedWalContentAndSchedulesAreSnapshottedAsOneTransaction()=runBlocking<Unit>{
        seed();answer(start());val id=content.list(limit=1).rows.first().id
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO){db.withTransaction{
            db.openHelper.writableDatabase.execSQL("UPDATE aspect_pairs SET note='together' WHERE id=?",arrayOf(id));entered.complete(Unit);release.await()
            // Restore the valid hash-bearing row before commit; both writes remain invisible to snapshot.
            val pair=db.aspectDao().all().single{it.id==id};val record=content.get(id)
            db.aspectDao().update(pair.copy(contentHash=AspectContent.hash(record.entry)))
        }}
        entered.await();val snapshot=async(Dispatchers.IO){backup.snapshot()};release.complete(Unit);writer.join()
        val prepared=backup.prepare(snapshot.await());val pair=prepared.document.getValue("tables").jsonObject.getValue("aspect_pairs").jsonArray.single{it.jsonObject["id"]==JsonPrimitive(id)}
        assertEquals(JsonPrimitive("together"),pair.jsonObject["note"])
        assertEquals("wal",db.openHelper.writableDatabase.query("PRAGMA journal_mode").use{it.moveToFirst();it.getString(0)})
        backup.restore(prepared);assertEquals(1L,AspectStatisticsRepository(db,clock).overview().answered)
    }
}
