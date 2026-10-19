package com.danishbears.bulgariantrainer.review

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.*
import com.danishbears.bulgariantrainer.statistics.*
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

internal class MutableStudyClock(var current: Instant = Instant.parse("2026-10-10T12:30:00Z")) : Clock() {
    override fun instant() = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current,zone)
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ReviewIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private val clock=MutableStudyClock()
    private val filename="review-${UUID.randomUUID()}.db"
    private lateinit var flash: FlashcardRepository
    private lateinit var typing: TypingRepository
    private lateinit var sentences: SentencePracticeRepository
    private lateinit var statistics: StatisticsRepository
    @Before fun setup() { context=ApplicationProvider.getApplicationContext();open() }
    private fun open() {
        db=Room.databaseBuilder(context,TrainerDatabase::class.java,filename).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build()
        flash=FlashcardRepository(db,clock,Random(5));typing=TypingRepository(db,clock,Random(5));sentences=SentencePracticeRepository(db,clock,Random(5));statistics=StatisticsRepository(db,clock)
    }
    @After fun teardown() { db.close();context.deleteDatabase(filename) }
    private fun request()=UUID.randomUUID().toString()
    private suspend fun seed(count: Int)=(0 until count).map { VocabularyRepository(db,clock).create(WordInput("дума$it",listOf(TranslationInput("слово$it")))) }
    private suspend fun sentence()=SentenceRepository(db,clock).create(SentenceInput("Я здесь.","Тук съм.",listOf("Аз съм тук."),difficulty=1))
    private suspend fun wordSchedule(id: Long,mode: ReviewMode)=db.reviewDao().word(id,mode.name)!!.schedule()
    private suspend fun expected(state: TypingState): String {
        val q=db.typingDao().question(state.id,state.question!!.id)!!
        return if(q.direction=="BG_TO_RU") Json.parseToJsonElement(q.russianJson).jsonArray.first().jsonPrimitive.content else q.bulgarian
    }
    @Test fun recognitionScheduleSharesBothDirectionsAndDuplicateTapsOrRecoveryNeverAdvanceItTwice()=runBlocking<Unit> {
        val word=seed(1).single();val state=flash.start(request())
        flash.reveal(state.id,state.question!!.id);assertTrue(db.reviewDao().words().isEmpty())
        val replies=(1..4).map { async(Dispatchers.Default) { flash.rate(state.id,state.question!!.id,true) } }.awaitAll()
        assertTrue(replies.all { it==replies.first() });val first=wordSchedule(word.id,ReviewMode.FLASHCARD)
        assertEquals(1,first.attempts);assertEquals(1,first.intervalDays);assertEquals(clock.current.plus(Duration.ofDays(1)),first.nextReview)
        db.close();open();assertEquals(replies.first(),flash.restore())
        flash.rate(state.id,state.question!!.id,true);assertEquals(first,wordSchedule(word.id,ReviewMode.FLASHCARD))
        val reverse=flash.start(request(),FlashcardSettings(1,FlashcardDirection.RU_TO_BG));flash.reveal(reverse.id,reverse.question!!.id);flash.rate(reverse.id,reverse.question!!.id,true)
        assertEquals(3,wordSchedule(word.id,ReviewMode.FLASHCARD).intervalDays);assertEquals(2L,statistics.overview().reviewAttempts)
        assertEquals(2L,statistics.overview().completedSessions)
    }
    @Test fun typingDirectionsScheduleIndependentlyAndExactNewScopeFindsTheUntestedDirection()=runBlocking<Unit> {
        val word=seed(1).single();val bg=typing.start(request(),true)
        typing.submit(bg.id,bg.question!!.id,expected(bg))
        val bgSchedule=wordSchedule(word.id,ReviewMode.TYPING_BG_TO_RU)
        assertNull(db.reviewDao().word(word.id,ReviewMode.TYPING_RU_TO_BG.name))
        val ru=typing.start(request(),true,StudyScope.NEW);assertEquals(TypingDirection.RU_TO_BG,ru.question!!.direction)
        typing.submit(ru.id,ru.question!!.id,"ошибка")
        assertEquals(bgSchedule,wordSchedule(word.id,ReviewMode.TYPING_BG_TO_RU));assertEquals(0,wordSchedule(word.id,ReviewMode.TYPING_RU_TO_BG).successes)
        assertTrue(db.reviewDao().word(word.id,ReviewMode.FLASHCARD.name)==null)
    }
    @Test fun pendingSentenceSchedulesOnlyOnFinalizationAndStatisticsKeepOriginalSubmissionDate()=runBlocking<Unit> {
        val sentence=sentence();val original=clock.current;val state=sentences.start(request())
        sentences.submit(state.id,state.question!!.id,"Тука съм.");assertNull(db.reviewDao().sentence(sentence.id));assertEquals(0L,statistics.overview().reviewAttempts)
        clock.current=original.plus(Duration.ofDays(2));val result=sentences.selfRate(state.id,state.question!!.id,SentenceRating.PARTIAL)
        assertEquals(1,result.partial);val scheduled=db.reviewDao().sentence(sentence.id)!!.schedule()
        assertEquals(1,scheduled.attempts);assertEquals(0,scheduled.successes);assertEquals(0,scheduled.streak);assertEquals(clock.current.plus(Duration.ofDays(1)),scheduled.nextReview)
        sentences.selfRate(state.id,state.question!!.id,SentenceRating.PARTIAL);assertEquals(scheduled,db.reviewDao().sentence(sentence.id)!!.schedule())
        val stats=statistics.overview();assertEquals(1L,stats.reviewAttempts);assertEquals(1L,stats.sentences.selfAssessed.attempts)
        assertEquals(1L,stats.last7Days.single { it.date==StatisticsCalculator.utcDate(original) }.reviewAttempts)
        assertEquals(0L,stats.last7Days.last().reviewAttempts);assertTrue(db.reviewDao().words().isEmpty())
    }
    @Test fun failedAnswerOrSelfRatingRollsBackCountersPositionAndSchedulesInSameTransaction()=runBlocking<Unit> {
        val word=seed(1).single();val state=flash.start(request());flash.reveal(state.id,state.question!!.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_review BEFORE UPDATE ON flashcard_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { flash.rate(state.id,state.question!!.id,true);fail("Expected rollback") } catch(_:SQLiteConstraintException) { }
        assertNull(db.reviewDao().word(word.id,ReviewMode.FLASHCARD.name));assertNull(flash.progress(word.id));assertEquals(0,flash.get(state.id).answered)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_review")
        val sentence=sentence();val s=sentences.start(request());sentences.submit(s.id,s.question!!.id,"Тука съм.")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_schedule BEFORE UPDATE ON sentence_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { sentences.selfRate(s.id,s.question!!.id,SentenceRating.CORRECT);fail("Expected rollback") } catch(_:SQLiteConstraintException) { }
        assertNull(db.reviewDao().sentence(sentence.id));assertNull(sentences.progress(sentence.id));assertEquals(1,sentences.get(s.id).pending)
    }
    @Test fun dueBoundaryUsesInjectedClockAndScopesSettingsRemainDurableAndRetrySafe()=runBlocking<Unit> {
        val word=seed(1).single();val state=flash.start(request());flash.reveal(state.id,state.question!!.id);flash.rate(state.id,state.question!!.id,false)
        val due=wordSchedule(word.id,ReviewMode.FLASHCARD).nextReview!!;val review=ReviewRepository(db,clock)
        clock.current=due.minusMillis(1);assertEquals(0L,review.available(ReviewMode.FLASHCARD,StudyScope.DUE))
        clock.current=due;assertEquals(1L,review.available(ReviewMode.FLASHCARD,StudyScope.DUE));assertEquals(0L,review.available(ReviewMode.FLASHCARD,StudyScope.NEW))
        val request=request();val started=flash.start(request,FlashcardSettings(1,scope=StudyScope.DUE));db.close();open()
        assertEquals(started,flash.restore());assertEquals(StudyScope.DUE,flash.restore()!!.settings.scope)
        assertEquals(started,flash.start(request,FlashcardSettings(1,scope=StudyScope.DUE)))
        try { flash.start(request,FlashcardSettings(1,scope=StudyScope.NEW));fail("Changed scope") } catch(e:FlashcardException) { assertEquals(FlashcardError.START_REQUEST_CHANGED,e.code) }
    }
    @Test fun newDueMixedPoolsExcludeArchiveAndDueTypingMayHaveThirtyQuestionsInOneDirection()=runBlocking<Unit> {
        val words=seed(30);val past=clock.current.minus(Duration.ofDays(1));val future=clock.current.plus(Duration.ofDays(1))
        for(word in words) {
            db.typingDao().saveProgress(TypingProgressEntity(word.id,"BG_TO_RU",1,1,1,past.toEpochMilli()))
            db.typingDao().saveProgress(TypingProgressEntity(word.id,"RU_TO_BG",1,1,1,clock.current.toEpochMilli()))
            db.reviewDao().save(ReviewSchedule(1,1,1,1,past,past).wordEntity(word.id,ReviewMode.TYPING_BG_TO_RU.name))
            db.reviewDao().save(ReviewSchedule(1,1,1,1,clock.current,future).wordEntity(word.id,ReviewMode.TYPING_RU_TO_BG.name))
        }
        val due=typing.start(request(),false,StudyScope.DUE)
        assertEquals(30,due.total);assertEquals(30,due.bgToRuQuestions);assertEquals(0,due.ruToBgQuestions)
        try { typing.start(request(),true,StudyScope.NEW);fail("No new pool") } catch(e:TypingException) { assertEquals(TypingError.EMPTY_NEW_QUEUE,e.code) }
        VocabularyRepository(db).setArchived(words.first().id,true)
        assertEquals(29L,ReviewRepository(db,clock).available(ReviewMode.TYPING_BG_TO_RU,StudyScope.DUE))
    }
    @Test fun realSqlProjectionMatchesThreeCorrectOfFiveAndKeepsCollidingModeSessionIdsSeparate()=runBlocking<Unit> {
        seed(2);val f=flash.start(request(),FlashcardSettings(1));flash.reveal(f.id,f.question!!.id);flash.rate(f.id,f.question!!.id,true)
        var t=typing.start(request(),true)
        while(t.question!=null) {
            val q=t.question!!;val answer=if(q.direction==TypingDirection.BG_TO_RU) "ошибка" else expected(t)
            t=typing.submit(t.id,q.id,answer);t=typing.next(t.id,q.id)
        }
        sentence();val auto=sentences.start(request());sentences.submit(auto.id,auto.question!!.id,"Тук съм.")
        val self=sentences.start(request());sentences.submit(self.id,self.question!!.id,"Тука съм.");sentences.selfRate(self.id,self.question!!.id,SentenceRating.PARTIAL)
        val pending=sentences.start(request());sentences.submit(pending.id,pending.question!!.id,"Вариант.")
        val stats=statistics.overview();assertEquals(5L,stats.reviewAttempts);assertEquals(3L,stats.overall.total.correct);assertEquals(1L,stats.overall.total.partial)
        assertEquals(60.0,stats.overall.total.accuracyPercent!!,0.0);assertEquals(3L,stats.overall.automatic.attempts);assertEquals(2L,stats.overall.selfAssessed.attempts)
        assertEquals(4L,stats.completedSessions);assertEquals(5,stats.recentSessions.size)
        assertEquals(3,stats.recentSessions.filter { it.id==1L }.map { it.mode }.toSet().size)
        assertEquals(0L,stats.recentSessions.single { it.mode==PracticeMode.SENTENCE&&it.id==pending.id }.answeredQuestions)
        val before=statistics.overview();db.close();open();flash.rate(f.id,f.question!!.id,true)
        assertEquals(before,statistics.overview())
    }
    @Test fun difficultWordDetailsUseCurrentTranslationsIncludeArchiveAndLimitRecentMistakesToTwenty()=runBlocking<Unit> {
        val word=seed(1).single()
        repeat(25) { index -> val state=typing.start(request(),true);typing.submit(state.id,state.question!!.id,"ошибка$index");clock.current=clock.current.plusSeconds(60) }
        VocabularyRepository(db).update(word.id,WordInput("дума0",listOf(TranslationInput("новый перевод"))));VocabularyRepository(db).setArchived(word.id,true)
        val stats=statistics.overview();assertEquals(1,stats.difficultWords.size);assertTrue(stats.difficultWords.single().archived)
        val details=statistics.wordMistakes(word.id);assertEquals(25L,details.incorrectAttempts);assertEquals(20,details.recent.size)
        assertEquals("ошибка24",details.recent.first().submitted);assertEquals(listOf("новый перевод"),details.translations)
        assertEquals(0L,stats.vocabulary.dueItems)
    }
    @Test fun failedTypingSubmissionLeavesNoDirectionScheduleOrAttempt()=runBlocking<Unit> {
        val word=seed(1).single();val state=typing.start(request(),true)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_typing_schedule BEFORE UPDATE ON typing_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { typing.submit(state.id,state.question!!.id,expected(state));fail("Expected rollback") } catch(_:SQLiteConstraintException) { }
        assertNull(db.reviewDao().word(word.id,ReviewMode.TYPING_BG_TO_RU.name));assertNull(typing.progress(word.id,TypingDirection.BG_TO_RU))
        assertEquals(0L,statistics.overview().reviewAttempts);assertEquals(0,typing.get(state.id).answered)
    }
    @Test fun pendingOrEarlyAbandonedSentenceNeverBecomesDueOrMastered()=runBlocking<Unit> {
        val sentence=sentence();val state=sentences.start(request());sentences.submit(state.id,state.question!!.id,"Вариант.");sentences.complete(state.id)
        assertNull(db.reviewDao().sentence(sentence.id));assertEquals(1L,ReviewRepository(db,clock).available(ReviewMode.SENTENCE,StudyScope.NEW))
        val stats=statistics.overview();assertEquals(0L,stats.reviewAttempts);assertEquals(0L,stats.completedSessions)
        assertEquals(1,stats.categories.getValue(ReviewMode.SENTENCE).newItems)
    }
}
