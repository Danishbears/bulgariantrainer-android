package com.danishbears.bulgariantrainer.pronunciation

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.sentences.*
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PronunciationLearningTest {
    private lateinit var db: TrainerDatabase
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build() }
    @After fun teardown() { db.close() }
    private fun request()=UUID.randomUUID().toString()
    @Test fun trainingRemainsUsableWhenNoBulgarianVoiceExists()=runBlocking<Unit> {
        VocabularyRepository(db).create(WordInput("котка",listOf(TranslationInput("кошка"))))
        val repo=FlashcardRepository(db);val session=repo.start(request(),FlashcardSettings(1))
        val fake=FakeTtsAdapter().apply { availability=LanguageAvailability.UNSUPPORTED;available=emptyList() }
        val speech=BulgarianPronunciation(this,{fake});speech.initialize();fake.initialized!!(true)
        try {
            val q=session.question!!;val target=PronunciationPolicy.flashcard(session.id,q)!!
            speech.register(target);assertFalse(speech.speak(target.owner))
            repo.reveal(session.id,q.id);val complete=repo.rate(session.id,q.id,true)
            assertEquals(FlashcardStatus.COMPLETED,complete.status)
            assertEquals(1L,StatisticsRepository(db).overview().reviewAttempts)
            assertTrue(fake.spoken.isEmpty());assertEquals(1,db.reviewDao().words().single().attempts)
        } finally { speech.close() }
    }
    @Test fun repeatedApprovedSentencePlaybackNeverGradesPendingAttemptOrChangesItsSchedule()=runBlocking<Unit> {
        SentenceRepository(db).create(SentenceInput("Я здесь.","Тук съм.",listOf("Аз съм тук."),difficulty=1))
        val repo=SentencePracticeRepository(db);val session=repo.start(request(),1)
        assertTrue(PronunciationPolicy.sentence(session.id,session.question!!).isEmpty())
        val submitted=repo.submit(session.id,session.question!!.id,"Аз тука")
        val q=submitted.question!!;assertTrue(q.feedback!!.needsSelfAssessment)
        val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(this,{fake})
        speech.initialize();fake.initialized!!(true)
        try {
            val targets=PronunciationPolicy.sentence(session.id,q)
            targets.forEach { speech.register(it);assertTrue(speech.speak(it.owner));assertTrue(speech.speak(it.owner,true)) }
            assertEquals(listOf("Тук съм.","Тук съм.","Аз съм тук.","Аз съм тук."),fake.spoken.map{it.text})
            assertEquals(0L,StatisticsRepository(db).overview().reviewAttempts);assertTrue(db.reviewDao().sentences().isEmpty())
            repo.selfRate(session.id,q.id,SentenceRating.PARTIAL)
            assertEquals(1L,StatisticsRepository(db).overview().reviewAttempts);assertEquals(1,db.reviewDao().sentences().single().attempts)
        } finally { speech.close() }
    }
}
