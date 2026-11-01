package com.danishbears.bulgariantrainer.aspect

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.review.*
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
class AspectPracticeRepositoryTest {
    private lateinit var context:Context
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectPracticeRepository
    private lateinit var content:AspectRepository
    private val filename="practice-"+UUID.randomUUID()+".db"
    private var at=Instant.parse("2026-11-01T12:00:00Z")
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId):Clock=this;override fun instant()=at}
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,filename).addMigrations(MIGRATION_8_9).build();repo=AspectPracticeRepository(db,clock,Random(12));content=AspectRepository(db,clock)}
    @After fun teardown(){db.close();context.deleteDatabase(filename)}
    private fun uuid()=UUID.randomUUID().toString()
    private fun source()=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.bufferedReader().use{it.readText()}).jsonObject
    private suspend fun seed(ids:List<String>,namespace:String="fixture") {
        val source=source();val entries=source.getValue("entries").jsonArray.filter{it.jsonObject.getValue("id").jsonPrimitive.content in ids}
        val p=content.preview(namespace,"fixture.json",JsonObject(source+("entries" to JsonArray(entries))).toString().toByteArray())
        assertTrue(p.issues.isEmpty());content.confirm(p,p.changes.map{it.externalId}.toSet())
    }
    private fun settings(size:Int=1,direction:AspectDirection=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,namespace:String="fixture",scope:StudyScope=StudyScope.MIXED)=AspectSettings(size,listOf(1,2,3),direction=direction,scope=scope,namespace=namespace)
    private suspend fun start(settings:AspectSettings=settings(),shorter:Boolean=false)=repo.start(uuid(),settings,shorter)
    private fun count(table:String)=db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use{it.moveToFirst();it.getInt(0)}
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("expected rejection")}catch(_:AspectException){}}
    private suspend fun legacy()=BackupFormat.decode(BackupRepository(context,db,clock).snapshot()).getValue("tables").jsonObject.filterKeys{it !in BackupFormat.practiceTables}
    @Test fun requiredDirectionsAlternativesParticlesSpellingAndMeaningCases()=runBlocking<Unit> {
        data class Case(val id:String,val d:AspectDirection,val raw:String,val correct:Boolean)
        val f=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE;val r=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE
        val cases=listOf(Case("bgasp-057",f,"купя",true),Case("bgasp-057",r,"купувам",true),Case("bgasp-043",r,"чета",true),Case("bgasp-043",r,"прочитам",true),
            Case("bgasp-010",r,"изпявам",true),Case("bgasp-021",f,"опитам",false),Case("bgasp-021",f,"опитам се",true),Case("bgasp-047",r,"седя",false),
            Case("bgasp-050",r,"стоя",false),Case("bgasp-066",f,"спомня",false),Case("bgasp-066",f,"спомня си",true),Case("bgasp-057",f,"куп",false),
            Case("bgasp-057",f,"kупя",false),Case("bgasp-057",f,"купя!",false),Case("bgasp-057",f," \tКУ\u0301ПЯ\u00a0\n",true))
        for((i,c)in cases.withIndex()) {
            val ns="case-$i";seed(listOf(c.id),ns);val s=start(settings(direction=c.d,namespace=ns));val q=s.question!!
            assertNull(q.feedback)
            val result=repo.submit(s.id,q.id,c.raw)
            assertEquals(if(c.correct)AspectOutcome.CORRECT else AspectOutcome.INCORRECT,result.question!!.feedback!!.outcome)
            assertEquals(c.raw,result.question!!.feedback!!.submitted);assertEquals(1,result.answered)
        }
    }
    @Test fun nfcLettersRemainDistinctInActualSessionEvaluation()=runBlocking<Unit> {
        for((i,target)in listOf("и","й","ѝ").withIndex()) {
            val e=source().getValue("entries").jsonArray.first().jsonObject
            val input=JsonObject(e+mapOf("id" to JsonPrimitive("letter"),"imperfective" to JsonPrimitive("стимул"),"perfective" to JsonPrimitive(target),
                "acceptedImperfective" to JsonArray(listOf(JsonPrimitive("стимул"))),"acceptedPerfective" to JsonArray(listOf(JsonPrimitive(target))),"provenance" to buildJsonObject{put("kind","added")}))
            content.create("letter-$i",input)
            for(raw in listOf("и","й","ѝ","и\u0300","и\u0306")) {
                val s=start(settings(namespace="letter-$i"));val expected=AspectContent.key(target)==AspectContent.key(raw)
                assertEquals(expected,repo.submit(s.id,s.question!!.id,raw).correct==1)
            }
        }
    }
    @Test fun freshStageOneFiltersArchiveAndShortageNeverPadOrCreateEmptySessions()=runBlocking<Unit> {
        seed(listOf("bgasp-057","bgasp-043","bgasp-028"))
        val available=repo.availability(AspectSettings())
        assertEquals(1,available.eligible);assertTrue(available.shorterRequired)
        rejected{start(AspectSettings())};assertEquals(0,count("aspect_sessions"))
        val short=start(AspectSettings(),true);assertEquals(1,short.total);assertEquals(listOf(1),short.settings.stages)
        val all=start(settings(10,AspectDirection.MIXED),true);assertEquals(3,all.total)
        assertEquals(3,db.aspectPracticeDao().graph(all.id)!!.questions.map{it.question.pairId}.distinct().size)
        val first=content.list(AspectFilter(namespace="fixture")).rows.first();content.archive(first.id,first.version,true)
        assertEquals(2,repo.availability(settings(10)).eligible)
        rejected{start(settings().copy(topic="Несуществующая тема"))}
        rejected{start(settings().copy(stages=emptyList()))}
    }
    @Test fun retriesConcurrentAnswersAndStaleActionsProduceOneAtomicAttemptAndSchedule()=runBlocking<Unit> {
        seed(listOf("bgasp-057"));val before=legacy();val request=uuid();val initial=repo.start(request,settings())
        val q=initial.question!!;val raw="купя"
        val results=coroutineScope{(1..8).map{async(Dispatchers.Default){repo.submit(initial.id,q.id,raw)}}.awaitAll()}
        assertTrue(results.all{it.correct==1});assertEquals(1,count("aspect_attempts"));assertEquals(1,count("aspect_progress"));assertEquals(1,count("aspect_review_schedules"))
        assertEquals(repo.get(initial.id),repo.start(request,settings()))
        rejected{repo.start(request,settings(direction=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE))}
        rejected{repo.submit(initial.id,q.id,"КУПЯ")};rejected{repo.reveal(initial.id,q.id)}
        repo.next(initial.id,q.id);val next=repo.next(initial.id,q.id);assertNull(next.question)
        assertEquals(next,repo.submit(initial.id,q.id,raw));assertEquals(before,legacy())
    }
    @Test fun revealIsTerminalFailedRecallAndIndependentDirectionIsUntouched()=runBlocking<Unit> {
        seed(listOf("bgasp-057"));val intervals=listOf(1,3,7,14,28)
        for(interval in intervals){val s=start();repo.submit(s.id,s.question!!.id,"купя");assertEquals(interval,db.aspectPracticeDao().schedules().single().intervalDays)}
        val s=start();val revealed=repo.reveal(s.id,s.question!!.id);assertEquals(0,revealed.answered);assertEquals(0,revealed.correct);assertEquals(1,revealed.revealed);assertNull(revealed.accuracy)
        assertEquals(revealed,repo.reveal(s.id,s.question!!.id));rejected{repo.submit(s.id,s.question!!.id,"купя")}
        val schedule=db.aspectPracticeDao().schedules().single();assertEquals(6,schedule.attempts);assertEquals(5,schedule.successes);assertEquals(0,schedule.streak);assertEquals(1,schedule.intervalDays)
        assertEquals(at.plus(Duration.ofDays(1)).toEpochMilli(),schedule.nextReview)
        val reverse=start(settings(direction=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE));repo.submit(reverse.id,reverse.question!!.id,"купувам")
        assertEquals(2,db.aspectPracticeDao().schedules().size);assertEquals(schedule,db.aspectPracticeDao().schedule(schedule.pairId,schedule.direction))
        assertEquals(0,repo.availability(settings(scope=StudyScope.DUE)).eligible);at=at.plus(Duration.ofDays(1))
        assertEquals(1,repo.availability(settings(scope=StudyScope.DUE)).eligible)
        assertEquals(0,repo.availability(settings(scope=StudyScope.NEW)).eligible)
    }
    @Test fun editArchiveAndDatabaseReopenPreserveSnapshotRawFeedbackAndOrder()=runBlocking<Unit> {
        seed(listOf("bgasp-057","bgasp-058"));var s=start(settings(2,AspectDirection.MIXED));val graph=db.aspectPracticeDao().graph(s.id)!!
        val first=graph.questions.single{it.question.id==s.question!!.id};val old=content.get(first.question.pairId)
        val e=JsonObject(old.entry+mapOf("imperfective" to JsonPrimitive("друга дума"),"perfective" to JsonPrimitive("друга форма"),
            "acceptedImperfective" to JsonArray(listOf(JsonPrimitive("друга дума"))),"acceptedPerfective" to JsonArray(listOf(JsonPrimitive("друга форма"))),"note" to JsonPrimitive("Changed")))
        val changed=content.update(old.id,old.version,e);content.archive(changed.id,changed.version,true)
        val snapshot=AspectContent.strict(first.question.snapshot).jsonObject
        val raw=AspectContent.answers(snapshot,if(first.question.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE.name)"acceptedPerfective" else "acceptedImperfective").last()
        s=repo.submit(s.id,s.question!!.id," $raw ");assertEquals(old.entry,s.question!!.feedback!!.content)
        db.close();open();assertEquals(s,repo.restore());assertEquals(graph.questions.map{it.question.id},db.aspectPracticeDao().graph(s.id)!!.questions.map{it.question.id})
        assertEquals(s,repo.submit(s.id,s.question!!.id," $raw "))
        s=repo.next(s.id,s.question!!.id);assertNull(s.question!!.feedback)
        db.close();open();assertEquals(s,repo.restore())
    }
    @Test fun answerRevealRaceHasOneWinnerAndEarlySkippedTargetsStayHidden()=runBlocking<Unit> {
        seed(listOf("bgasp-057","bgasp-058"));val s=start(settings(2));val q=s.question!!
        coroutineScope{listOf(async(Dispatchers.Default){runCatching{repo.submit(s.id,q.id,"купя")}},async(Dispatchers.Default){runCatching{repo.reveal(s.id,q.id)}}).awaitAll()}
        assertEquals(1,count("aspect_attempts"));assertEquals(1,db.aspectPracticeDao().schedules().single().attempts)
        val completed=repo.complete(s.id);assertEquals(AspectSessionStatus.ABANDONED,completed.status);assertEquals(1,completed.remaining)
        val skipped=completed.review.single{it.feedback==null};assertNull(skipped.feedback)
        assertNull(completed.question);assertEquals(completed,repo.complete(s.id))
        val unanswered=db.aspectPracticeDao().graph(s.id)!!.questions.single{it.attempt==null}
        rejected{repo.submit(s.id,unanswered.question.id,"ответ")}
    }
    @Test fun transactionFailureRollsBackAttemptProgressScheduleAndPosition()=runBlocking<Unit> {
        seed(listOf("bgasp-057"));val s=start();val before=BackupRepository(context,db,clock).snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aspect BEFORE UPDATE ON aspect_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{repo.submit(s.id,s.question!!.id,"купя");fail("expected rollback")}catch(_:android.database.SQLException){}
        assertArrayEquals(before,BackupRepository(context,db,clock).snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aspect")
        assertEquals(1,repo.submit(s.id,s.question!!.id,"купя").correct)
    }
    @Test fun backupRoundTripRestoresActiveFeedbackSchedulesAndOldV2ContentWithoutFabrication()=runBlocking<Unit> {
        seed(listOf("bgasp-057","bgasp-058"));val s=start(settings(2));val feedback=repo.reveal(s.id,s.question!!.id)
        val backup=BackupRepository(context,db,clock);val bytes=backup.snapshot();val preview=backup.prepare(bytes)
        repo.next(s.id,s.question!!.id);backup.restore(preview);assertEquals(feedback,repo.restore());assertArrayEquals(bytes,backup.snapshot())
        val root=BackupFormat.decode(bytes)
        val old=JsonObject(root+mapOf("formatVersion" to JsonPrimitive(2),"schemaVersion" to JsonPrimitive(8),
            "tables" to JsonObject(root.getValue("tables").jsonObject.filterKeys{it !in BackupFormat.practiceTables}),
            "sequences" to JsonObject(root.getValue("sequences").jsonObject.filterKeys{it !in BackupFormat.practiceSequences})))
        val signed=JsonObject(old+("sha256" to JsonPrimitive(BackupFormat.digest(old)))).toString().toByteArray()
        val oldPreview=backup.prepare(signed);backup.restore(oldPreview)
        assertNull(repo.restore());assertEquals(2L,content.list().total);assertEquals(0,count("aspect_progress"))
    }
    @Test fun schema8UpgradeKeepsAspectContentIdsReceiptsAndAllLegacyRows()=runBlocking<Unit> {
        seed(listOf("bgasp-057"));VocabularyRepository(db,clock).create(WordInput("дума",listOf(TranslationInput("слово"))))
        val before=legacy();val beforeSeq=BackupFormat.decode(BackupRepository(context,db,clock).snapshot()).getValue("sequences").jsonObject.filterKeys{it !in BackupFormat.practiceSequences}
        db.openHelper.writableDatabase.apply {
            for(table in BackupFormat.practiceTables.asReversed())execSQL("DROP TABLE `$table`")
            execSQL("PRAGMA user_version = 8")
        }
        db.close();open()
        assertEquals(9,db.openHelper.writableDatabase.version);assertEquals(before,legacy())
        assertEquals(beforeSeq,BackupFormat.decode(BackupRepository(context,db,clock).snapshot()).getValue("sequences").jsonObject.filterKeys{it !in BackupFormat.practiceSequences})
        assertEquals(1,start().total)
    }
}
