package com.danishbears.bulgariantrainer.typing

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TypingRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var repository: TypingRepository
    private lateinit var words: VocabularyRepository
    private val filename = "typing-${UUID.randomUUID()}.db"
    private val at = Instant.parse("2026-10-19T16:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        repository = TypingRepository(db, clock, Random(5)); words = VocabularyRepository(db, clock)
    }
    @After fun teardown() { db.close(); context.deleteDatabase(filename) }
    private fun reopen() { db.close(); open() }
    private suspend fun seed(count: Int) = (0 until count).map { words.create(WordInput("дума $it",
        listOf(TranslationInput("слово $it"), TranslationInput("выражение $it", true)))) }
    private suspend fun start(shorter: Boolean = true) = repository.start(UUID.randomUUID().toString(), shorter)
    private suspend fun queue(id: Long) = db.typingDao().graph(id)!!.questions.sortedBy { it.question.position }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun expected(state: TypingState): List<String> {
        val q = db.typingDao().question(state.id, state.question!!.id)!!
        return if (q.direction == TypingDirection.BG_TO_RU.name) Json.parseToJsonElement(q.russianJson).jsonArray.map { it.jsonPrimitive.content } else listOf(q.bulgarian)
    }
    private suspend fun expect(code: TypingError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: TypingException) { assertEquals(code, e.code) }
    }
    @Test fun fullQueueUsesThirtyDistinctActiveEntriesFifteenEachAndDoesNotLeakAcceptedAnswers() = runBlocking<Unit> {
        val saved = seed(36); words.setArchived(saved.last().id, true)
        val state = start(false); val queue = queue(state.id)
        assertEquals(30, queue.size)
        assertEquals(30, queue.map { it.question.wordId }.toSet().size)
        assertEquals(15, queue.count { it.question.direction == "BG_TO_RU" })
        assertEquals(15, queue.count { it.question.direction == "RU_TO_BG" })
        assertFalse(queue.any { it.question.wordId == saved.last().id })
        assertNull(state.question!!.feedback); assertTrue(state.mistakes.isEmpty())
        assertEquals(0, state.answered); assertEquals(0, count("typing_progress"))
        assertEquals(state, repository.get(state.id)); assertEquals(state, repository.restore())
        assertEquals(35L, repository.observeAvailable().first())
    }
    @Test fun emptyOrTranslationlessPoolNeverCreatesSession() = runBlocking<Unit> {
        expect(TypingError.EMPTY_DICTIONARY) { start() }
        val id = db.openHelper.writableDatabase.compileStatement("INSERT INTO words(bulgarian_text,normalized_text,archived,created_at,updated_at) VALUES('без','без',0,1,1)").executeInsert()
        assertTrue(id > 0)
        expect(TypingError.EMPTY_DICTIONARY) { start() }
        assertEquals(0, count("typing_sessions"))
    }
    @Test fun shorterRequiresExplicitConsentAndOddOrSingleQueuesNeverRepeat() = runBlocking<Unit> {
        val saved = seed(5)
        expect(TypingError.SHORTER_SESSION_REQUIRED) { start(false) }
        assertEquals(0, count("typing_sessions"))
        val state = start(); val queue = queue(state.id)
        assertEquals(5, queue.size); assertEquals(5, queue.map { it.question.wordId }.toSet().size)
        assertEquals(3, queue.count { it.question.direction == "BG_TO_RU" })
        assertEquals(2, queue.count { it.question.direction == "RU_TO_BG" })
        saved.drop(1).forEach { words.setArchived(it.id, true) }
        assertEquals(1, start().total)
    }
    @Test fun alternativesUnicodeOriginalSubmissionAndFeedbackAcknowledgementMatchWeb() = runBlocking<Unit> {
        words.create(WordInput("мой дом", listOf(TranslationInput("свой дом"), TranslationInput("моё жилище", true))))
        words.create(WordInput("нейният дом", listOf(TranslationInput("её дом"), TranslationInput("родной дом", true))))
        var state = start()
        while (state.question != null) {
            val q = state.question!!; val accepted = expected(state)
            val answer = " \t" + Normalizer.normalize(accepted.last().uppercase(Locale.ROOT), Normalizer.Form.NFD).replace(" ", "\u00a0  ") + "  "
            state = repository.submit(state.id, q.id, answer)
            assertEquals(q.id, state.question!!.id)
            assertEquals(TypingFeedback(true, answer, accepted), state.question!!.feedback)
            assertEquals(answer, db.typingDao().attempt(q.id)!!.submitted)
            assertEquals(TypingEvaluator.key(answer), db.typingDao().attempt(q.id)!!.normalizedAnswer)
            assertEquals(state, repository.get(state.id))
            val advanced = repository.next(state.id, q.id)
            assertEquals(advanced, repository.next(state.id, q.id)); state = advanced
        }
        assertEquals(TypingStatus.COMPLETED, state.status); assertEquals(2, state.correct)
        assertTrue(state.mistakes.isEmpty()); assertEquals(0, count("recognition_progress"))
    }
    @Test fun wrongSpellingGivesSavedMistakesAndUnansweredEntriesAreNotMistakesOrProgress() = runBlocking<Unit> {
        seed(3); val initial = start(); val q = initial.question!!
        val accepted = expected(initial)
        val answered = repository.submit(initial.id, q.id, "ошибка")
        assertFalse(answered.question!!.feedback!!.correct)
        assertEquals(accepted, answered.question!!.feedback!!.accepted)
        val closed = repository.complete(initial.id)
        assertEquals(TypingStatus.ABANDONED, closed.status); assertNull(closed.question)
        assertEquals(2, closed.remaining); assertEquals(1, closed.mistakes.size)
        assertEquals(TypingMistake(q.id, q.position, q.direction, q.prompt, "ошибка", accepted), closed.mistakes.single())
        assertEquals(closed.mistakes, repository.mistakes(initial.id)); assertEquals(closed, repository.complete(initial.id))
        assertEquals(1, count("typing_attempts")); assertEquals(1, count("typing_progress"))
    }
    @Test fun blankOversizedForeignFutureAndPrematureActionsDoNotWrite() = runBlocking<Unit> {
        seed(3); val a = start(); val b = start(); val q = a.question!!
        expect(TypingError.ANSWER_REQUIRED) { repository.next(a.id, q.id) }
        expect(TypingError.RESULTS_UNAVAILABLE) { repository.mistakes(a.id) }
        expect(TypingError.STALE_QUESTION) { repository.submit(a.id, b.question!!.id, "ответ") }
        expect(TypingError.STALE_QUESTION) { repository.submit(a.id, queue(a.id).last().question.id, "ответ") }
        for (invalid in listOf("", " \t\n", "\u00a0\u0085", "а".repeat(4001))) expect(TypingError.INVALID_REQUEST) { repository.submit(a.id, q.id, invalid) }
        assertEquals(0, count("typing_attempts")); assertEquals(0, count("typing_progress"))
    }
    @Test fun exactRetriesAreIdempotentButNormalizedEquivalentDifferentRawTextConflicts() = runBlocking<Unit> {
        seed(2); val initial = start(); val q = initial.question!!
        val answer = expected(initial).first()
        val feedback = repository.submit(initial.id, q.id, answer)
        assertEquals(feedback, repository.submit(initial.id, q.id, answer))
        expect(TypingError.ANSWER_CONFLICT) { repository.submit(initial.id, q.id, " $answer ") }
        val next = repository.next(initial.id, q.id)
        assertEquals(next, repository.submit(initial.id, q.id, answer))
        assertNull(next.question!!.feedback); assertEquals(1, count("typing_attempts"))
    }
    @Test fun lastFeedbackSurvivesCompletionButNoNewAnswerCanBeAddedToAbandonedSession() = runBlocking<Unit> {
        seed(1); val initial = start(); val q = initial.question!!
        val answer = expected(initial).first(); val done = repository.submit(initial.id, q.id, answer)
        assertEquals(TypingStatus.COMPLETED, done.status); assertNotNull(done.question!!.feedback)
        assertEquals(done, repository.submit(initial.id, q.id, answer))
        expect(TypingError.ANSWER_CONFLICT) { repository.submit(initial.id, q.id, "новое") }
        assertNull(repository.next(done.id, q.id).question)
        val early = start(); repository.complete(early.id)
        expect(TypingError.SESSION_CLOSED) { repository.submit(early.id, early.question!!.id, "ответ") }
        assertEquals(1, count("typing_attempts"))
    }
    @Test fun concurrentIdenticalAndOpposingSubmissionsCommitExactlyOneAttempt() = runBlocking<Unit> {
        seed(2); val initial = start(); val q = initial.question!!
        val gate = CompletableDeferred<Unit>()
        val tasks = (1..6).map { async(Dispatchers.Default) { gate.await(); repository.submit(initial.id, q.id, "ошибка") } }
        gate.complete(Unit); val values = tasks.awaitAll()
        assertTrue(values.all { it == values.first() }); assertEquals(1, count("typing_attempts"))
        val next = repository.next(initial.id, q.id); val second = next.question!!
        val outcomes = listOf("один", "два").map { text -> async(Dispatchers.Default) {
            try { repository.submit(next.id, second.id, text); null } catch (e: TypingException) { e.code }
        } }.awaitAll()
        assertEquals(1, outcomes.count { it == TypingError.ANSWER_CONFLICT })
        assertEquals(2, count("typing_attempts")); assertEquals(2, count("typing_progress"))
    }
    @Test fun concurrentStartsRetrySameUuidAndRejectChangedConsentWithoutReplacingQueue() = runBlocking<Unit> {
        seed(4); val request = UUID.randomUUID().toString()
        val starts = (1..4).map { async(Dispatchers.Default) { repository.start(request, true) } }.awaitAll()
        assertTrue(starts.all { it == starts.first() }); assertEquals(1, count("typing_sessions"))
        expect(TypingError.START_REQUEST_CHANGED) { repository.start(request, false) }
        expect(TypingError.INVALID_REQUEST) { repository.start("bad", true) }
        assertEquals(4, count("typing_questions"))
    }
    @Test fun reopenedFileRestoresQueueDirectionsSubmittedFeedbackPositionAndTerminalReview() = runBlocking<Unit> {
        seed(5); var state = start(); val originalQueue = queue(state.id).map { it.question }
        val first = state.question!!
        state = repository.submit(state.id, first.id, " ОШИБКА "); reopen()
        assertEquals(state, repository.restore()); assertEquals(originalQueue, queue(state.id).map { it.question })
        state = repository.next(state.id, first.id); reopen()
        assertEquals(state, repository.restore()); assertNull(state.question!!.feedback)
        val second = state.question!!; state = repository.submit(state.id, second.id, expected(state).first())
        reopen(); assertEquals(state, repository.restore())
        state = repository.complete(state.id); reopen()
        assertEquals(state, repository.restore()); assertEquals(1, state.mistakes.size)
        assertEquals(2, count("typing_attempts")); assertEquals(3, state.remaining)
    }
    @Test fun finalFeedbackAndAcknowledgedSummaryBothSurviveLostResponsesAndReopen() = runBlocking<Unit> {
        seed(1); var state = start(); val q = state.question!!
        repository.submit(state.id, q.id, "ошибка") // UI receives no response.
        reopen(); state = repository.restore()!!
        assertEquals(TypingStatus.COMPLETED, state.status); assertFalse(state.question!!.feedback!!.correct)
        repository.next(state.id, q.id); reopen(); state = repository.restore()!!
        assertNull(state.question); assertEquals("ошибка", state.mistakes.single().submitted)
        assertEquals(state, repository.submit(state.id, q.id, "ошибка")); assertEquals(1, count("typing_attempts"))
    }
    @Test fun snapshotEditsArchiveAndDeleteProtectionKeepAcceptedAnswersStable() = runBlocking<Unit> {
        val word = seed(1).single(); val state = start(); val answer = expected(state).first()
        words.update(word.id, WordInput("ново", listOf(TranslationInput("новое")))); words.setArchived(word.id, true)
        assertEquals(state, repository.get(state.id))
        try { words.delete(word.id); fail("Expected protected history") } catch (_: WordInUseException) { }
        assertTrue(repository.submit(state.id, state.question!!.id, answer).question!!.feedback!!.correct)
        expect(TypingError.EMPTY_DICTIONARY) { start() }
    }
    @Test fun bothTypingDirectionsMaintainIndependentCountersAndLeaveRecognitionUntouched() = runBlocking<Unit> {
        val words = seed(2)
        val recognition = FlashcardRepository(db, clock, Random(1)); val flash = recognition.start(UUID.randomUUID().toString())
        recognition.reveal(flash.id, flash.question!!.id); recognition.rate(flash.id, flash.question!!.id, true)
        val original = db.flashcardDao().graph(flash.id)
        for (seed in 0..5) {
            val repo = TypingRepository(db, clock, Random(seed)); var state = repo.start(UUID.randomUUID().toString(), true)
            while (state.question != null) {
                val q = state.question!!; state = repo.submit(state.id, q.id, expected(state).first()); state = repo.next(state.id, q.id)
            }
        }
        for (word in words) for (direction in TypingDirection.entries) {
            val progress = repository.progress(word.id, direction)!!
            assertTrue(progress.attempts > 0); assertEquals(progress.attempts, progress.correct)
            assertEquals(progress.correct, progress.consecutiveCorrect); assertEquals(at, progress.lastSubmittedAt)
        }
        assertEquals(original, db.flashcardDao().graph(flash.id))
        assertEquals(1, count("flashcard_attempts")); assertEquals(1, count("recognition_progress"))
    }
    @Test fun lateFailureRollsBackAttemptCounterStatusAndFeedbackTogetherThenExactRetryWorks() = runBlocking<Unit> {
        seed(1); val state = start(); val q = state.question!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_typing BEFORE UPDATE ON typing_sessions BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        try { repository.submit(state.id, q.id, "ошибка"); fail("Expected transaction failure") } catch (_: SQLiteConstraintException) { }
        assertEquals(state, repository.get(state.id)); assertEquals(0, count("typing_attempts")); assertEquals(0, count("typing_progress"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_typing")
        assertEquals(1, repository.submit(state.id, q.id, "ошибка").answered)
    }
    @Test fun failedQueueCreationRollsBackSessionAndAllQuestions() = runBlocking<Unit> {
        seed(3)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_question BEFORE INSERT ON typing_questions WHEN NEW.position = 2 BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        try { start(); fail("Expected failed start") } catch (_: SQLiteConstraintException) { }
        assertEquals(0, count("typing_sessions")); assertEquals(0, count("typing_questions")); assertNull(repository.restore())
    }
    @Test fun observingSessionReceivesFeedbackAndAcknowledgementFromDatabase() = runBlocking<Unit> {
        seed(2); val state = start(); val q = state.question!!
        val feedback = async { withTimeout(10_000) { repository.observe(state.id).first { it?.answered == 1 } } }
        repository.submit(state.id, q.id, "ошибка")
        assertEquals("ошибка", feedback.await()!!.question!!.feedback!!.submitted)
        repository.next(state.id, q.id)
        assertEquals(2, repository.observe(state.id).first()!!.currentPosition)
    }
    @Test fun freshRepositoriesWithSameRandomSeedSelectExactlySameWordDirectionOrder() = runBlocking<Unit> {
        seed(42)
        suspend fun planned(seed: Int): List<Pair<Long, String>> {
            val state = TypingRepository(db, clock, Random(seed)).start(UUID.randomUUID().toString())
            return queue(state.id).map { it.question.wordId to it.question.direction }
        }
        assertEquals(planned(10), planned(10)); assertNotEquals(planned(10), planned(11))
    }
    @Test fun earlyCompletionWithoutAnswersAndActiveRecoveryPreferenceAwardNoProgress() = runBlocking<Unit> {
        seed(2); val one = start(); val two = start()
        assertEquals(one, repository.restore(one.id)); repository.complete(two.id)
        assertEquals(one, repository.restore(two.id))
        val closed = repository.complete(one.id); assertEquals(2, closed.remaining); assertTrue(closed.mistakes.isEmpty())
        assertEquals(0, count("typing_progress")); assertEquals(0, count("typing_attempts"))
    }
    @Test fun uniqueQueueAndAttemptConstraintsRejectBypassWrites() = runBlocking<Unit> {
        seed(2); val state = start(); val q = db.typingDao().question(state.id, state.question!!.id)!!
        try { db.typingDao().insertQuestion(q.copy(id = 0, position = 3)); fail("Expected unique word") } catch (_: SQLiteConstraintException) { }
        repository.submit(state.id, q.id, "ошибка")
        try { db.typingDao().insertAttempt(TypingAttemptEntity(questionId = q.id, submitted = "ещё", normalizedAnswer = "ещё", correct = false, submittedAt = 2)); fail("Expected unique attempt") } catch (_: SQLiteConstraintException) { }
        assertEquals(1, count("typing_attempts"))
    }
}
