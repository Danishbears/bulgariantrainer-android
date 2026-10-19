package com.danishbears.bulgariantrainer.sentences

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.transfer.*
import java.io.StringWriter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class SentenceRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var content: SentenceRepository
    private lateinit var practice: SentencePracticeRepository
    private val filename = "sentences-${UUID.randomUUID()}.db"
    private val instant = Instant.parse("2026-10-19T18:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        content = SentenceRepository(db, clock); practice = SentencePracticeRepository(db, clock, Random(7))
    }
    @After fun teardown() { db.close(); context.deleteDatabase(filename) }
    private fun reopen() { db.close(); open() }
    private suspend fun seed(count: Int) = (0 until count).map { content.create(SentenceInput("Русское предложение $it", "Българско изречение $it",
        listOf("Български вариант $it"), "Пояснение $it", 2, "Тема")) }
    private suspend fun start(size: Int = 10) = practice.start(UUID.randomUUID().toString(), size)
    private suspend fun accepted(state: SentencePracticeState): List<String> {
        val q = db.sentenceDao().question(state.id, state.question!!.id)!!
        return content.get(q.sentenceId).input.let { listOf(it.bulgarian) + it.alternatives }
    }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun expect(code: SentenceError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: SentenceException) { assertEquals(code, e.code) }
    }
    @Test fun manualCreateNormalizesMetadataAndOptionalDifficultyWithoutDemoRecords() = runBlocking<Unit> {
        assertEquals(0, count("sentences"))
        val record = content.create(SentenceInput(" Я\u00a0 здесь. ", " Тук съм. ", listOf(" Аз съм тук. "), "  ", null, " Тема "))
        assertEquals(SentenceInput("Я здесь.", "Тук съм.", listOf("Аз съм тук."), null, null, "Тема"), record.input)
        assertTrue(record.approved); assertFalse(record.archived); assertTrue(record.sourceId.startsWith("manual-"))
        assertEquals(record, content.get(record.id)); assertEquals(1L, content.observeAvailable().first())
    }
    @Test fun duplicateRussianReservesKeyAcrossArchiveAndUpdateKeepsProvenanceAndAlternativeIdentity() = runBlocking<Unit> {
        val a = seed(2); val original = a.first(); val alternatives = db.sentenceDao().get(original.id)!!.alternatives
        content.setArchived(original.id, true)
        expect(SentenceError.DUPLICATE_SENTENCE) { content.create(original.input.copy(russian = " ${original.input.russian.uppercase()} ")) }
        expect(SentenceError.DUPLICATE_SENTENCE) { content.update(a.last().id, original.input) }
        val changed = content.update(original.id, original.input.copy(bulgarian = "Нова версия.", difficulty = null))
        assertTrue(changed.archived); assertEquals(original.sourceId, changed.sourceId); assertNull(changed.input.difficulty)
        assertEquals(alternatives.single().id, db.sentenceDao().get(original.id)!!.alternatives.single().id)
        assertEquals(1L, content.observeAvailable().first())
        content.setArchived(original.id, false); assertEquals(2L, content.observeAvailable().first())
    }
    @Test fun validationRejectsBlankLengthInvalidDifficultyAndNormalizedDuplicateAlternatives() = runBlocking<Unit> {
        val valid = SentenceInput("Дом.", "Къща.")
        for (invalid in listOf(valid.copy(russian = "\u00a0"), valid.copy(bulgarian = ""), valid.copy(russian = "а".repeat(4001)),
            valid.copy(explanation = "а".repeat(4001)), valid.copy(topic = "а".repeat(256)), valid.copy(difficulty = 0), valid.copy(difficulty = 6),
            valid.copy(alternatives = listOf(" КЪЩА. ")), valid.copy(alternatives = listOf("Мой дом.", "Мои\u0306\u00a0дом.")),
            valid.copy(alternatives = listOf("")), valid.copy(alternatives = (1..51).map { "вариант $it" }))) {
            expect(SentenceError.INVALID_SENTENCE) { content.create(invalid) }
        }
        assertEquals(0, count("sentences"))
    }
    @Test fun pagedActiveAndArchiveListsAreBoundedAndContainRealContent() = runBlocking<Unit> {
        val records = seed(36); content.setArchived(records.last().id, true)
        val active = content.pages(false).asSnapshot { scrollTo(34) }
        assertEquals(35, active.size); assertTrue(active.none { it.archived })
        assertEquals(records.last().id, content.pages(true).asSnapshot().single().id)
    }
    @Test fun relatedContentWritesRollbackTogetherOnAlternativeFailure() = runBlocking<Unit> {
        val record = seed(1).single()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_alt BEFORE INSERT ON sentence_alternatives BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { content.update(record.id, record.input.copy(bulgarian = "Нова версия.", alternatives = listOf("Нов вариант."))); fail("Expected rollback") }
        catch (_: SQLiteConstraintException) { }
        assertEquals(record, content.get(record.id))
    }
    @Test fun nullDifficultyExportRefusesBeforeWritingThenWebCompatibleRoundTripKeepsAlternatives() = runBlocking<Unit> {
        val record = content.create(SentenceInput("Где вокзал?", "Къде е гарата?", listOf("Къде се намира гарата?")))
        val transfer = ContentTransferRepository(db); val writer = StringWriter()
        try { transfer.export(ContentKind.SENTENCES, false, writer); fail("Expected explicit difficulty requirement") }
        catch (e: TransferException) { assertEquals(TransferError.SENTENCE_DIFFICULTY_REQUIRED, e.code) }
        assertEquals("", writer.toString())
        content.update(record.id, record.input.copy(difficulty = 3))
        transfer.export(ContentKind.SENTENCES, false, writer)
        val preview = transfer.preview(ContentKind.SENTENCES, "export.csv", writer.toString().toByteArray())
        val row = preview.rows.single { it.sentence != null }
        assertEquals(record.input.alternatives, row.sentence!!.alternatives); assertTrue(row.issues().isEmpty())
    }
    @Test fun defaultAndShortQueuesAreUniqueApprovedOnlyAndHideAnswersAndExplanations() = runBlocking<Unit> {
        val records = seed(14); content.setArchived(records.last().id, true)
        db.sentenceDao().get(records.first().id)!!.sentence.let { db.sentenceDao().update(it.copy(approved = false)) }
        val state = start(); val queue = db.sentenceDao().graph(state.id)!!.questions
        assertEquals(10, state.total); assertEquals(10, state.requestedSize); assertEquals(10, queue.map { it.question.sentenceId }.toSet().size)
        assertFalse(queue.any { it.question.sentenceId in setOf(records.first().id, records.last().id) })
        assertNull(state.question!!.feedback); assertTrue(state.review.isEmpty()); assertEquals(0, state.submitted)
        assertEquals(12L, practice.observeAvailable().first()); assertEquals(12, start(100).total)
        assertEquals(state, practice.get(state.id)); assertEquals(0, count("sentence_progress"))
    }
    @Test fun emptyAndInvalidRequestsInsertNothing() = runBlocking<Unit> {
        expect(SentenceError.EMPTY_SENTENCES) { start() }
        expect(SentenceError.INVALID_SETTINGS) { start(0) }; expect(SentenceError.INVALID_SETTINGS) { start(101) }
        expect(SentenceError.INVALID_SETTINGS) { practice.start("bad") }
        assertEquals(0, count("sentence_sessions"))
    }
    @Test fun referenceAndApprovedAlternativeMatchUnicodeMultilineAutomaticallyWithoutManualAssessment() = runBlocking<Unit> {
        content.create(SentenceInput("Это мой дом.", "Това е моят дом.", listOf("Това е моята къща.")))
        for (useAlternative in listOf(false, true)) {
            val state = start(); val q = state.question!!; val answers = accepted(state)
            val answer = " \t" + java.text.Normalizer.normalize(answers[if (useAlternative) 1 else 0].uppercase(), java.text.Normalizer.Form.NFD).replace(" ", "\n\u00a0  ") + "\n"
            val evaluated = practice.submit(state.id, q.id, answer)
            assertEquals(SentenceStatus.COMPLETED, evaluated.status); assertEquals(1, evaluated.correct)
            val feedback = evaluated.question!!.feedback!!
            assertTrue(feedback.automaticMatch); assertNull(feedback.selfRating); assertEquals(SentenceEvaluation.AUTOMATIC, feedback.evaluation)
            assertEquals(answer, feedback.submitted); assertEquals(answers, feedback.accepted)
            expect(SentenceError.EVALUATION_CONFLICT) { practice.selfRate(state.id, q.id, SentenceRating.CORRECT) }
        }
        assertEquals(2, count("sentence_attempts")); assertEquals(2, practice.progress(1)!!.attempts)
    }
    @Test fun mismatchIsPendingNotIncorrectAndProgressWaitsForOneSelfAssessment() = runBlocking<Unit> {
        val record = seed(1).single(); val state = start(); val q = state.question!!
        val pending = practice.submit(state.id, q.id, "Мой собствен вариант.\nВтори ред.")
        assertEquals(SentenceStatus.IN_PROGRESS, pending.status); assertEquals(1, pending.submitted); assertEquals(0, pending.evaluated)
        assertEquals(0, pending.incorrect); assertEquals(1, pending.pending); assertNull(practice.progress(record.id))
        assertTrue(pending.question!!.feedback!!.needsSelfAssessment); assertNull(pending.question!!.feedback!!.evaluation)
        expect(SentenceError.EVALUATION_REQUIRED) { practice.next(state.id, q.id) }
        val evaluated = practice.selfRate(state.id, q.id, SentenceRating.PARTIAL)
        assertEquals(SentenceStatus.COMPLETED, evaluated.status); assertEquals(1, evaluated.evaluated); assertEquals(1, evaluated.partial)
        assertFalse(evaluated.question!!.feedback!!.automaticMatch); assertEquals(SentenceEvaluation.SELF, evaluated.question!!.feedback!!.evaluation)
        assertEquals(1, count("sentence_attempts")); assertEquals(1, practice.progress(record.id)!!.attempts)
        assertEquals(1, practice.progress(record.id)!!.partial); assertEquals(0, practice.progress(record.id)!!.consecutiveCorrect)
    }
    @Test fun allManualRatingsRemainDistinctFromAutomaticAndUpdateCountersOnce() = runBlocking<Unit> {
        val record = seed(1).single()
        for (rating in SentenceRating.entries) {
            val state = start(); val q = state.question!!; practice.submit(state.id, q.id, "Различен вариант.")
            val rated = practice.selfRate(state.id, q.id, rating)
            assertEquals(rating, rated.question!!.feedback!!.selfRating)
            assertEquals(SentenceEvaluation.SELF, rated.question!!.feedback!!.evaluation)
            assertEquals(rated, practice.selfRate(state.id, q.id, rating))
            assertEquals(1, rated.evaluated)
        }
        val progress = practice.progress(record.id)!!
        assertEquals(3, progress.attempts); assertEquals(1, progress.correct); assertEquals(1, progress.partial); assertEquals(1, progress.incorrect)
        assertEquals(0, progress.consecutiveCorrect); assertEquals(instant, progress.lastEvaluatedAt)
    }
    @Test fun exactRawSubmissionAndRatingRetriesAreSafeAfterAdvanceButChangedActionsConflict() = runBlocking<Unit> {
        seed(2); val state = start(); val q = state.question!!
        val pending = practice.submit(state.id, q.id, " текст "); assertEquals(pending, practice.submit(state.id, q.id, " текст "))
        expect(SentenceError.ANSWER_CONFLICT) { practice.submit(state.id, q.id, "текст") }
        practice.selfRate(state.id, q.id, SentenceRating.CORRECT)
        val next = practice.next(state.id, q.id)
        assertEquals(next, practice.submit(state.id, q.id, " текст ")); assertEquals(next, practice.selfRate(state.id, q.id, SentenceRating.CORRECT))
        assertEquals(next, practice.next(state.id, q.id)); assertNull(next.question!!.feedback)
        expect(SentenceError.EVALUATION_CONFLICT) { practice.selfRate(state.id, q.id, SentenceRating.INCORRECT) }
        assertEquals(1, count("sentence_attempts")); assertEquals(1, count("sentence_progress"))
    }
    @Test fun blankLongForeignFutureAndPrematureActionsDoNotWrite() = runBlocking<Unit> {
        seed(3); val state = start(); val other = start(); val q = state.question!!
        expect(SentenceError.RESULTS_UNAVAILABLE) { practice.review(state.id) }
        expect(SentenceError.ANSWER_REQUIRED) { practice.selfRate(state.id, q.id, SentenceRating.CORRECT) }
        expect(SentenceError.EVALUATION_REQUIRED) { practice.next(state.id, q.id) }
        expect(SentenceError.STALE_QUESTION) { practice.submit(state.id, other.question!!.id, "ответ") }
        val future = db.sentenceDao().graph(state.id)!!.questions.maxBy { it.question.position }.question
        expect(SentenceError.STALE_QUESTION) { practice.submit(state.id, future.id, "ответ") }
        for (answer in listOf("", "\u00a0\n", "а".repeat(4001))) expect(SentenceError.INVALID_ANSWER) { practice.submit(state.id, q.id, answer) }
        assertEquals(0, count("sentence_attempts")); assertEquals(0, count("sentence_progress"))
    }
    @Test fun concurrentSubmissionsAndSelfRatingsNeverDoubleCountAndOpposingRatingsConflict() = runBlocking<Unit> {
        seed(1); val state = start(); val q = state.question!!
        val replies = (1..4).map { async(Dispatchers.Default) { practice.submit(state.id, q.id, "Вариант.") } }.awaitAll()
        assertTrue(replies.all { it == replies.first() }); assertEquals(1, count("sentence_attempts"))
        val ratings = (1..4).map { async(Dispatchers.Default) { practice.selfRate(state.id, q.id, SentenceRating.PARTIAL) } }.awaitAll()
        assertTrue(ratings.all { it == ratings.first() }); assertEquals(1, practice.progress(1)!!.attempts)
        val second = start(); practice.submit(second.id, second.question!!.id, "Вариант.")
        val outcomes = listOf(SentenceRating.CORRECT, SentenceRating.INCORRECT).map { rating -> async(Dispatchers.Default) {
            try { practice.selfRate(second.id, second.question!!.id, rating); null } catch (e: SentenceException) { e.code }
        } }.awaitAll()
        assertEquals(1, outcomes.count { it == SentenceError.EVALUATION_CONFLICT }); assertEquals(2, practice.progress(1)!!.attempts)
    }
    @Test fun concurrentStartsReplaySettingsAndInjectedRandomnessControlsEntireOrder() = runBlocking<Unit> {
        seed(18); val request = UUID.randomUUID().toString()
        val starts = (1..4).map { async(Dispatchers.Default) { practice.start(request, 10) } }.awaitAll()
        assertTrue(starts.all { it == starts.first() }); assertEquals(1, count("sentence_sessions"))
        expect(SentenceError.START_REQUEST_CHANGED) { practice.start(request, 9) }
        suspend fun order(seed: Int) = SentencePracticeRepository(db, clock, Random(seed)).start(UUID.randomUUID().toString()).let {
            db.sentenceDao().graph(it.id)!!.questions.sortedBy { it.question.position }.map { it.question.sentenceId }
        }
        assertEquals(order(5), order(5)); assertNotEquals(order(5), order(6))
    }
    @Test fun earlyCompletionKeepsPendingAndSkippedSeparateWithoutAwardingProgress() = runBlocking<Unit> {
        seed(3); val state = start(); val q = state.question!!; practice.submit(state.id, q.id, "Вариант.")
        val ended = practice.complete(state.id)
        assertEquals(SentenceStatus.ABANDONED, ended.status); assertNull(ended.question)
        assertEquals(1, ended.pending); assertEquals(2, ended.unanswered); assertEquals(0, ended.evaluated)
        assertEquals(3, ended.review.size); assertEquals(1, ended.review.count { it.feedback?.result == SentenceResult.PENDING })
        assertEquals(2, ended.review.count { it.feedback == null }); assertEquals(0, count("sentence_progress"))
        assertEquals(ended, practice.complete(state.id)); assertEquals(ended, practice.submit(state.id, q.id, "Вариант."))
        expect(SentenceError.SESSION_CLOSED) { practice.selfRate(state.id, q.id, SentenceRating.CORRECT) }
        val future = ended.review.last().questionId
        expect(SentenceError.SESSION_CLOSED) { practice.submit(state.id, future, "новый ответ") }
    }
    @Test fun fileReopenRecoversOrderPendingManualFeedbackPositionAndTerminalReview() = runBlocking<Unit> {
        seed(3); var state = start(); val q = state.question!!
        val initialQueue = db.sentenceDao().graph(state.id)!!.questions.map { it.question }.sortedBy { it.position }
        state = practice.submit(state.id, q.id, " \nВариант.\n "); reopen()
        assertEquals(state, practice.restore()); assertEquals(initialQueue, db.sentenceDao().graph(state.id)!!.questions.map { it.question }.sortedBy { it.position })
        state = practice.selfRate(state.id, q.id, SentenceRating.CORRECT); reopen(); assertEquals(state, practice.restore())
        state = practice.next(state.id, q.id); reopen(); assertEquals(state, practice.restore()); assertNull(state.question!!.feedback)
        state = practice.complete(state.id); reopen(); assertEquals(state, practice.restore())
        assertEquals(" \nВариант.\n ", state.review.first().feedback!!.submitted); assertEquals(1, count("sentence_attempts"))
    }
    @Test fun finalAutomaticFeedbackAndAcknowledgedSummarySurviveLostResponses() = runBlocking<Unit> {
        seed(1); val state = start(); val q = state.question!!; val answer = accepted(state).last()
        practice.submit(state.id, q.id, answer); reopen()
        val final = practice.restore()!!; assertEquals(SentenceStatus.COMPLETED, final.status); assertTrue(final.question!!.feedback!!.automaticMatch)
        practice.next(state.id, q.id); reopen()
        val summary = practice.restore()!!; assertNull(summary.question); assertEquals(1, summary.correct)
        assertEquals(summary, practice.submit(state.id, q.id, answer)); assertEquals(1, practice.progress(1)!!.attempts)
    }
    @Test fun contentEditArchiveAndImportNeverRewriteExistingSnapshotsOrProgress() = runBlocking<Unit> {
        val record = seed(1).single(); val state = start(); val q = state.question!!; val accepted = accepted(state)
        content.update(record.id, record.input.copy(bulgarian = "Нова версия.", alternatives = emptyList(), explanation = "Нов текст"))
        content.setArchived(record.id, true); assertEquals(state, practice.get(state.id))
        val answer = practice.submit(state.id, q.id, accepted.last())
        assertTrue(answer.question!!.feedback!!.automaticMatch); assertEquals(accepted, answer.question!!.feedback!!.accepted)
        assertEquals("Пояснение 0", answer.question!!.feedback!!.explanation)
        val before = practice.progress(record.id)
        val transfer = ContentTransferRepository(db); val export = StringWriter(); transfer.export(ContentKind.SENTENCES, true, export)
        val preview = transfer.preview(ContentKind.SENTENCES, "export.csv", export.toString().toByteArray())
        val confirmed = preview.copy(rows = preview.rows.map { if (it.sentence != null) it.copy(selected = true) else it })
        transfer.confirm(confirmed)
        assertEquals(before, practice.progress(record.id)); assertEquals(answer, practice.get(state.id))
        expect(SentenceError.EMPTY_SENTENCES) { start() }
    }
    @Test fun sentenceTrainingDoesNotTouchVocabularyOrRecognitionOrTypingProgress() = runBlocking<Unit> {
        val word = VocabularyRepository(db).create(WordInput("чай", listOf(TranslationInput("чай"))))
        val flash = FlashcardRepository(db); val f = flash.start(UUID.randomUUID().toString()); flash.reveal(f.id, f.question!!.id); flash.rate(f.id, f.question!!.id, true)
        val typing = TypingRepository(db); val t = typing.start(UUID.randomUUID().toString(), true); typing.submit(t.id, t.question!!.id, "чай")
        val oldFlash = flash.progress(word.id); val oldTyping = typing.progress(word.id, TypingDirection.BG_TO_RU)
        seed(1); val state = start(); val q = state.question!!; practice.submit(state.id, q.id, "Вариант."); practice.selfRate(state.id, q.id, SentenceRating.CORRECT)
        assertEquals(oldFlash, flash.progress(word.id)); assertEquals(oldTyping, typing.progress(word.id, TypingDirection.BG_TO_RU))
        assertEquals(1, count("flashcard_attempts")); assertEquals(1, count("typing_attempts")); assertEquals(1, count("sentence_attempts"))
    }
    @Test fun failedFinalizationRollsBackRatingProgressAndStatusButKeepsOriginalPendingSubmission() = runBlocking<Unit> {
        seed(1); val state = start(); val q = state.question!!; val pending = practice.submit(state.id, q.id, "Вариант.")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_final BEFORE UPDATE ON sentence_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { practice.selfRate(state.id, q.id, SentenceRating.PARTIAL); fail("Expected rollback") } catch (_: SQLiteConstraintException) { }
        assertEquals(pending, practice.get(state.id)); assertEquals(1, count("sentence_attempts")); assertEquals(0, count("sentence_progress"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_sentence_final")
        assertEquals(1, practice.selfRate(state.id, q.id, SentenceRating.PARTIAL).evaluated)
    }
    @Test fun lateSubmissionAndQueueCreationFailuresRollbackAllRelatedWrites() = runBlocking<Unit> {
        seed(2); val state = start(); val q = state.question!!; val answer = accepted(state).first()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_submit BEFORE UPDATE ON sentence_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { practice.submit(state.id, q.id, answer); fail("Expected rollback") } catch (_: SQLiteConstraintException) { }
        assertEquals(state, practice.get(state.id)); assertEquals(0, count("sentence_attempts")); assertEquals(0, count("sentence_progress"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_sentence_submit")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_queue BEFORE INSERT ON sentence_questions WHEN NEW.position = 2 BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try { start(); fail("Expected rollback") } catch (_: SQLiteConstraintException) { }
        assertEquals(1, count("sentence_sessions")); assertEquals(2, count("sentence_questions"))
    }
    @Test fun reactiveStateAndUniqueConstraintsProtectQueueAndOneAttemptPerQuestion() = runBlocking<Unit> {
        seed(2); val state = start(); val q = db.sentenceDao().question(state.id, state.question!!.id)!!
        try { db.sentenceDao().insertQuestion(q.copy(id = 0, position = 3)); fail("Expected unique sentence") } catch (_: SQLiteConstraintException) { }
        val observed = async { withTimeout(10_000) { practice.observe(state.id).first { it?.pending == 1 } } }
        practice.submit(state.id, q.id, "Вариант."); assertTrue(observed.await()!!.question!!.feedback!!.needsSelfAssessment)
        try { db.sentenceDao().insertAttempt(SentenceAttemptEntity(questionId = q.id, submitted = "новый", normalizedAnswer = "новый", automaticMatch = false, submittedAt = 5)); fail("Expected unique attempt") } catch (_: SQLiteConstraintException) { }
        assertEquals(1, count("sentence_attempts"))
    }
    @Test fun meaningfulBulgarianDifferencesRemainPendingRatherThanAutomaticGrammarErrors() = runBlocking<Unit> {
        content.create(SentenceInput("Мой дом.", "Моята къща.", listOf("Моят дом.")))
        for (answer in listOf("Моята каща.", "Моят дoм.", "Моят дом!")) {
            val state = start(); val evaluated = practice.submit(state.id, state.question!!.id, answer)
            assertEquals(SentenceResult.PENDING, evaluated.question!!.feedback!!.result); assertEquals(0, evaluated.incorrect)
            practice.complete(state.id)
        }
        assertEquals(0, count("sentence_progress"))
    }
}
