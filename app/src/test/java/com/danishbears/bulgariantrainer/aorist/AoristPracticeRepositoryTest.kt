package com.danishbears.bulgariantrainer.aorist

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.review.*
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
class AoristPracticeRepositoryTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase
    private lateinit var content:AoristRepository;private lateinit var practice:AoristPracticeRepository
    private val name="aorist-practice-${UUID.randomUUID()}.db"
    private var at=Instant.parse("2026-11-19T12:00:00Z")
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=this;override fun instant()=at}
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).addMigrations(MIGRATION_10_11).build();content=AoristRepository(db,clock);practice=AoristPracticeRepository(db,clock,Random(42))}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun request()=UUID.randomUUID().toString()
    private fun root()=AoristContent.strict(javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.bufferedReader().use{it.readText()}).jsonObject
    private fun entry(lemma:String)=root().getValue("entries").jsonArray.single{it.jsonObject["lemma"]==JsonPrimitive(lemma)}.jsonObject
    private suspend fun seed(entries:List<JsonObject>,dataset:String="fixture") {
        val r=JsonObject(root()+mapOf("datasetId" to JsonPrimitive(dataset),"entries" to JsonArray(entries)))
        val p=content.preview("fixture.json",r.toString().toByteArray());assertTrue(p.issues.toString(),p.issues.isEmpty());content.confirm(p,p.changes.map{it.externalId}.toSet())
    }
    private fun settings(dataset:String="fixture",person:String="1sg",type:AoristExercise=AoristExercise.TYPED,size:Int=1,scope:StudyScope=StudyScope.MIXED)=AoristSettings(size,listOf(person),type,scope=scope,datasetId=dataset)
    private suspend fun start(s:AoristSettings=settings(),shorter:Boolean=false,fewer:Boolean=false)=practice.start(request(),s,shorter,fewer)
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("must reject")}catch(_:AoristException){}}
    private suspend fun snapshot()=BackupRepository(context,db,clock).snapshot()
    private fun accepted(q:AoristQuestion)=AoristContent.answers(entry(q.prompt.lemma),q.prompt.person)

    @Test fun requiredVerbsAndPersonsUseOnlyTheirExactAcceptedArray()=runBlocking<Unit> {
        data class Case(val lemma:String,val person:String,val correct:String,val wrong:String)
        val cases=listOf(Case("чета","1pl","четохме","прочетохме"),Case("прочета","1pl","прочетохме","четохме"),
            Case("взема","1sg","взех","вземах"),Case("вземам","1sg","вземах","взех"),Case("видя","1pl","видяхме","видехме"),
            Case("изляза","3sg","излезе","излязе"),Case("облека се","1sg","облякох се","облякох"))
        for((i,c)in cases.withIndex()) {
            val dataset="case-$i";val e=entry(c.lemma);seed(listOf(e),dataset)
            val config=settings(dataset,c.person);val s=start(config);val q=s.question!!
            assertEquals(c.lemma,q.prompt.lemma);assertEquals(AoristContent.text(e,"russian"),q.prompt.russian);assertEquals(c.person,q.prompt.person)
            val result=practice.submitTyped(s.id,q.id," \t${c.correct.uppercase()}\n")
            assertEquals(1,result.correct);assertEquals(1,result.answered);assertEquals(" \t${c.correct.uppercase()}\n",result.question!!.feedback!!.submitted)
            assertTrue(result.active);assertEquals(1,result.position) // feedback waits for Next, including last item
            assertEquals(AoristSessionStatus.COMPLETED,practice.next(s.id,q.id).status)
            val wrong=start(config);val feedback=practice.submitTyped(wrong.id,wrong.question!!.id,c.wrong)
            assertEquals(AoristOutcome.INCORRECT,feedback.question!!.feedback!!.outcome)
        }
    }
    @Test fun alternativesStressWhitespaceAndExactBulgarianSpellingAreEvaluatedInRealSessions()=runBlocking<Unit> {
        val original=entry("чета");val forms=original.getValue("forms").jsonObject
        val e=JsonObject(original+mapOf("note" to JsonPrimitive("Synthetic approved-alternative fixture"),"forms" to JsonObject(forms+("1pl" to JsonArray(listOf(JsonPrimitive("четохме"),JsonPrimitive("одобрен синтетичен вариант")))))))
        seed(listOf(e));val config=settings(person="1pl")
        for(raw in listOf(" ЧЕ\u0301ТОХМЕ\u00a0\t", "одобрен\u2003синтетичен\nвариант")){val s=start(config);assertEquals(1,practice.submitTyped(s.id,s.question!!.id,raw).correct)}
        for(raw in listOf("прочетохме","четох","chetоhme","четохмe","четохме!")){val s=start(config);assertEquals(1,practice.submitTyped(s.id,s.question!!.id,raw).incorrect)}
        val s=start(config);val before=snapshot();rejected{practice.submitTyped(s.id,s.question!!.id,"\u0301 \t")};rejected{practice.submitTyped(s.id,s.question!!.id,"\uD800")};rejected{practice.saveDraft(s.id,s.question!!.id,"\uD800",null,1)};assertArrayEquals(before,snapshot())
        val letters=JsonObject(original+mapOf("id" to JsonPrimitive("letters"),"note" to JsonPrimitive("Synthetic Unicode fixture, not linguistic material"),
            "forms" to buildJsonObject{for(person in AoristContent.persons)put(person,JsonArray(listOf(JsonPrimitive("ѝ"))))}))
        seed(listOf(letters),"letters")
        for(raw in listOf("и","й")){val item=start(settings("letters"));assertEquals(1,practice.submitTyped(item.id,item.question!!.id,raw).incorrect)}
        val composed=start(settings("letters"));assertEquals(1,practice.submitTyped(composed.id,composed.question!!.id,"и\u0300").correct)
        seed(listOf(entry("облека се")),"reflexive")
        for(raw in listOf("облякох си","облякох cе","облякох")){val item=start(settings("reflexive"));assertEquals(1,practice.submitTyped(item.id,item.question!!.id,raw).incorrect)}
    }
    @Test fun defaultAzFiltersAllPersonsArchiveAndShortageNeverRepeatTargets()=runBlocking<Unit> {
        seed(root().getValue("entries").jsonArray.map{it.jsonObject})
        assertEquals(AoristExercise.TYPED,practice.preferences().exercise);assertEquals(listOf("1sg"),practice.preferences().persons)
        assertEquals(40,practice.availability(AoristSettings()).eligible)
        val config=AoristSettings(size=100,persons=AoristContent.persons,studyGroup="1_ah")
        assertEquals(12,practice.availability(config).eligible);rejected{start(config)}
        val s=start(config,shorter=true);assertEquals(12,s.total)
        val questions=db.aoristPracticeDao().graph(s.id)!!.questions
        assertEquals(12,questions.map{it.question.verbId to it.question.person}.distinct().size)
        assertEquals(AoristContent.persons.toSet(),questions.map{it.question.person}.toSet())
        assertEquals(66,practice.availability(AoristSettings(size=100,persons=AoristContent.persons,aspect="imperfective")).eligible)
        val first=content.list().rows.first();content.archive(first.id,first.version,true)
        assertEquals(39,practice.availability(AoristSettings()).eligible)
        assertEquals(0,practice.availability(AoristSettings(studyGroup="not-present")).eligible)
        val empty=AoristSettings(studyGroup="not-present");val count=db.aoristPracticeDao().allIds().size;rejected{start(empty,true)};assertEquals(count,db.aoristPracticeDao().allIds().size)
    }
    @Test fun choicesHaveExactlyOneApprovedOptionAndNoAlternativeOrHomographAsWrongAnswer()=runBlocking<Unit> {
        seed(listOf(entry("чета")))
        val config=settings(person="2sg",type=AoristExercise.CHOICE)
        val s=start(config);val q=s.question!!;assertEquals(4,q.options.size)
        val keys=q.options.map{AoristContent.key(it.text)};assertEquals(4,keys.distinct().size);assertEquals(1,keys.count{it=="чете"})
        val correct=q.options.single{it.text=="чете"}
        val before=snapshot();rejected{practice.submitChoice(s.id,q.id,"foreign-option")};rejected{practice.submitTyped(s.id,q.id,"чете")};assertArrayEquals(before,snapshot())
        assertEquals(1,practice.submitChoice(s.id,q.id,correct.id).correct)
        val next=start(config);assertEquals(1,practice.submitChoice(next.id,next.question!!.id,next.question!!.options.first{it.text!="чете"}.id).incorrect)
    }
    @Test fun scarceChoiceOptionsRequireConsentAndOfferTypedWithoutSilentConversion()=runBlocking<Unit> {
        val original=entry("чета")
        fun fixture(second:Boolean)=JsonObject(original+mapOf("note" to JsonPrimitive("Synthetic distractor-count fixture"),"forms" to buildJsonObject{
            for(person in AoristContent.persons)put(person,JsonArray(listOf(JsonPrimitive(if(second&&person=="1pl")"друг синтетичен отговор" else "еднакъв синтетичен отговор"))))}))
        seed(listOf(fixture(false)))
        val choice=settings(type=AoristExercise.CHOICE);val a=practice.availability(choice)
        assertEquals(0,a.eligible);assertEquals(1,a.unavailableChoices);rejected{start(choice)}
        assertEquals(1,practice.availability(settings()).eligible);assertEquals(AoristExercise.TYPED,start().settings.exercise)
        val record=content.list().rows.single();content.update(record.id,record.version,fixture(true))
        assertEquals(1,practice.availability(choice).fewerOptions);rejected{start(choice)}
        val s=start(choice,fewer=true);assertEquals(2,s.question!!.options.size);assertEquals(AoristExercise.CHOICE,s.question!!.exercise)
    }
    @Test fun revealIsTerminalFailureAndNextViewingAndRetriesDoNotAddCredit()=runBlocking<Unit> {
        seed(listOf(entry("взема")));val s=start();val q=s.question!!
        practice.get(s.id);practice.restore();assertNull(practice.progress(q.verbId,"1sg",AoristExercise.TYPED))
        val revealed=practice.reveal(s.id,q.id);assertEquals(0,revealed.correct);assertEquals(0,revealed.answered);assertEquals(1,revealed.revealed)
        assertEquals(revealed,practice.reveal(s.id,q.id));rejected{practice.submitTyped(s.id,q.id,"взех")}
        val p=practice.progress(q.verbId,"1sg",AoristExercise.TYPED)!!
        assertEquals(1,p.attempts);assertEquals(1,p.revealed);assertEquals(0,p.schedule.streak);assertEquals(at.plus(Duration.ofDays(1)),p.schedule.nextReview)
        val done=practice.next(s.id,q.id);assertEquals(done,practice.next(s.id,q.id));assertEquals(p,practice.progress(q.verbId,"1sg",AoristExercise.TYPED))
        assertNull(practice.progress(q.verbId,"1sg",AoristExercise.CHOICE));assertNull(practice.progress(q.verbId,"1pl",AoristExercise.TYPED))
    }
    @Test fun concurrentSubmissionsRevealRacesAndStaleCallbacksCreateOneAtomicAttempt()=runBlocking<Unit> {
        seed(listOf(entry("видя")));val s=start(settings(person="1pl"));val q=s.question!!
        val results=coroutineScope{listOf(async(Dispatchers.Default){practice.submitTyped(s.id,q.id,"видяхме")},async(Dispatchers.Default){practice.submitTyped(s.id,q.id,"видяхме")}).awaitAll()}
        assertEquals(results[0],results[1]);assertEquals(1,practice.progress(q.verbId,"1pl",AoristExercise.TYPED)!!.attempts)
        rejected{practice.submitTyped(s.id,q.id,"видехме")};val done=practice.next(s.id,q.id)
        assertEquals(done,practice.submitTyped(s.id,q.id,"видяхме"));rejected{practice.reveal(s.id,q.id)}
        val race=start(settings(person="1pl"));val raceQuestion=race.question!!
        val outcomes=coroutineScope{listOf(async(Dispatchers.Default){runCatching{practice.submitTyped(race.id,raceQuestion.id,"видяхме")}},async(Dispatchers.Default){runCatching{practice.reveal(race.id,raceQuestion.id)}}).awaitAll()}
        assertEquals(1,outcomes.count{it.isSuccess});assertEquals(2,practice.progress(q.verbId,"1pl",AoristExercise.TYPED)!!.attempts)
    }
    @Test fun failedScheduleWriteRollsBackAttemptProgressFeedbackAndPosition()=runBlocking<Unit> {
        seed(listOf(entry("чета")));val s=start();val before=snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aorist_schedule BEFORE INSERT ON aorist_review_schedules BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{practice.submitTyped(s.id,s.question!!.id,"четох");fail("rollback required")}catch(_:android.database.SQLException){}
        assertArrayEquals(before,snapshot());assertEquals(s,practice.get(s.id));assertTrue(db.aoristPracticeDao().progressRows().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aorist_schedule")
        assertEquals(1,practice.submitTyped(s.id,s.question!!.id,"четох").correct)
    }
    @Test fun reopeningRestoresTypedDraftChoiceOrderFeedbackAndExplicitNext()=runBlocking<Unit> {
        seed(listOf(entry("видя")));var s=start(settings(person="1pl"));val q=s.question!!
        practice.saveDraft(s.id,q.id,"видях",null,2);practice.saveDraft(s.id,q.id,"вид",null,1)
        db.close();open();s=practice.restore()!!;assertEquals("видях",s.question!!.draft);assertEquals(q.id,s.question!!.id)
        s=practice.submitTyped(s.id,q.id,"видяхме");val progress=practice.progress(q.verbId,"1pl",AoristExercise.TYPED)
        db.close();open();assertEquals(s,practice.restore());practice.saveDraft(s.id,q.id,"late draft",null,99);assertEquals(s,practice.get(s.id))
        practice.next(s.id,q.id);assertEquals(progress,practice.progress(q.verbId,"1pl",AoristExercise.TYPED))
        var c=start(settings(type=AoristExercise.CHOICE));val options=c.question!!.options;val option=options.first()
        practice.saveDraft(c.id,c.question!!.id,"",option.id,1);db.close();open();c=practice.restore()!!
        assertEquals(options,c.question!!.options);assertEquals(option.id,c.question!!.selectedDraft)
        c=practice.submitChoice(c.id,c.question!!.id,option.id);db.close();open();assertEquals(c,practice.restore())
    }
    @Test fun contentEditsAndArchivingCannotChangeAnExistingSnapshotOrGrade()=runBlocking<Unit> {
        seed(listOf(entry("чета")));val s=start();val q=s.question!!;val old=content.get(q.verbId)
        val forms=old.entry.getValue("forms").jsonObject;val updated=content.update(old.id,old.version,JsonObject(old.entry+mapOf("note" to JsonPrimitive("Synthetic edit after session creation"),"forms" to JsonObject(forms+("1sg" to JsonArray(listOf(JsonPrimitive("нов синтетичен отговор"))))))))
        content.archive(updated.id,updated.version,true);assertEquals(0,practice.availability(settings()).eligible)
        db.close();open();val result=practice.submitTyped(s.id,q.id,"четох")
        assertEquals(1,result.correct);assertEquals(listOf("четох"),result.question!!.feedback!!.accepted)
        assertEquals(old.entry,result.question!!.feedback!!.content)
    }
    @Test fun personVerbAndExerciseSchedulesStayIndependentOfEveryOtherLearningMode()=runBlocking<Unit> {
        val e=entry("взема");val other=entry("вземам")
        seed(listOf(JsonObject(e+("studyGroup" to JsonPrimitive("focus"))),JsonObject(other+("studyGroup" to JsonPrimitive("other")))))
        VocabularyRepository(db,clock).create(WordInput("дума",listOf(TranslationInput("слово"))))
        val before=BackupFormat.decode(snapshot()).getValue("tables").jsonObject.filterKeys{it !in BackupFormat.aoristPracticeTables};val stats=StatisticsRepository(db,clock).overview()
        val typedConfig=settings().copy(studyGroup="focus");val typed=start(typedConfig);val q=typed.question!!
        practice.submitTyped(typed.id,q.id,"взех");val p=practice.progress(q.verbId,"1sg",AoristExercise.TYPED)!!
        val choice=start(typedConfig.copy(exercise=AoristExercise.CHOICE));val c=choice.question!!;practice.submitChoice(choice.id,c.id,c.options.single{it.text=="взех"}.id)
        assertEquals(p,practice.progress(q.verbId,"1sg",AoristExercise.TYPED));assertEquals(1,practice.progress(q.verbId,"1sg",AoristExercise.CHOICE)!!.correct)
        assertNull(practice.progress(q.verbId,"1pl",AoristExercise.TYPED));assertNull(practice.progress(content.list().rows.single{it.entry["lemma"]==JsonPrimitive("вземам")}.id,"1sg",AoristExercise.TYPED))
        assertEquals(before,BackupFormat.decode(snapshot()).getValue("tables").jsonObject.filterKeys{it !in BackupFormat.aoristPracticeTables});assertEquals(stats,StatisticsRepository(db,clock).overview())
    }
    @Test fun newDueMixedMasteryFailureAndClockBoundariesReuseTheExistingScheduler()=runBlocking<Unit> {
        seed(listOf(entry("чета")));val config=settings();var id=0L
        for(interval in listOf(1,3,7,14,28)) {
            val s=start(config);id=s.question!!.verbId;practice.submitTyped(s.id,s.question!!.id,"четох");practice.next(s.id,s.question!!.id)
            val p=practice.progress(id,"1sg",AoristExercise.TYPED)!!;assertEquals(interval,p.schedule.intervalDays);assertEquals(at.plus(Duration.ofDays(interval.toLong())),p.schedule.nextReview)
        }
        assertEquals(LearningCategory.MASTERED,practice.progress(id,"1sg",AoristExercise.TYPED)!!.category)
        assertEquals(0,practice.availability(config.copy(scope=StudyScope.NEW)).eligible);assertEquals(0,practice.availability(config.copy(scope=StudyScope.DUE)).eligible)
        val deadline=practice.progress(id,"1sg",AoristExercise.TYPED)!!.schedule.nextReview!!;at=deadline.minusMillis(1)
        assertEquals(0,practice.availability(config.copy(scope=StudyScope.DUE)).eligible);at=deadline
        assertEquals(1,practice.availability(config.copy(scope=StudyScope.DUE)).eligible)
        val failure=start(config.copy(scope=StudyScope.DUE));practice.reveal(failure.id,failure.question!!.id)
        val p=practice.progress(id,"1sg",AoristExercise.TYPED)!!;assertEquals(0,p.schedule.streak);assertEquals(1,p.schedule.intervalDays);assertEquals(5,p.correct)
        at=at.minus(Duration.ofDays(400));assertEquals(p.schedule,practice.progress(id,"1sg",AoristExercise.TYPED)!!.schedule)
        BackupRepository(context,db,clock).prepare(snapshot()) // Backwards time never invalidates saved logical history.
    }
    @Test fun startAndEarlyCompletionAreIdempotentAndSkippedFormsHaveNoProgress()=runBlocking<Unit> {
        seed(listOf(entry("чета")));val config=AoristSettings(size=6,persons=AoristContent.persons);val request=request()
        val s=practice.start(request,config);assertEquals(s,practice.start(request,config));rejected{practice.start(request,config.copy(size=5))}
        val q=s.question!!;practice.submitTyped(s.id,q.id,accepted(q).first());val done=practice.complete(s.id)
        assertEquals(AoristSessionStatus.ABANDONED,done.status);assertEquals(5,done.skipped);assertEquals(1,done.answered)
        assertEquals(done,practice.complete(s.id));assertEquals(1,db.aoristPracticeDao().progressRows().size)
        assertEquals(5,done.review.count{it.feedback==null});assertTrue(done.review.filter{it.feedback==null}.all{it.prompt.lemma=="чета"})
        val skipped=db.aoristPracticeDao().graph(s.id)!!.questions.first{it.attempt==null}.question
        rejected{practice.submitTyped(s.id,skipped.id,"чете")};val before=snapshot();practice.saveDraft(s.id,skipped.id,"late",null,99);assertArrayEquals(before,snapshot())
    }
}
