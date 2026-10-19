package com.danishbears.bulgariantrainer.flashcards

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.transfer.*
import java.io.StringWriter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class FlashcardRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var repository: FlashcardRepository
    private lateinit var words: VocabularyRepository
    private val filename = "flashcards-${UUID.randomUUID()}.db"
    private val at = Instant.parse("2026-10-19T15:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        repository = FlashcardRepository(db, clock, Random(5))
        words = VocabularyRepository(db, clock)
    }
    private fun reopen() { db.close(); open() }
    @After fun teardown() { db.close(); context.deleteDatabase(filename) }
    private suspend fun seed(count: Int) = (0 until count).map { words.create(WordInput("дума $it",
        listOf(TranslationInput("слово $it"), TranslationInput("выражение $it", true)))) }
    private suspend fun start(size: Int = 20, direction: FlashcardDirection = FlashcardDirection.BG_TO_RU) =
        repository.start(UUID.randomUUID().toString(), FlashcardSettings(size, direction))
    private suspend fun rate(state: FlashcardState, known: Boolean): FlashcardState {
        repository.reveal(state.id, state.question!!.id)
        return repository.rate(state.id, state.question!!.id, known)
    }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun expect(code: FlashcardError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: FlashcardException) { assertEquals(code, e.code) }
    }
    private suspend fun constraint(action: suspend () -> Unit) {
        try { action(); fail("Expected SQLite constraint") } catch (_: SQLiteConstraintException) { }
    }

    @Test fun defaultsExcludeArchiveHideAnswersAndViewingOrRevealNeverAwardProgress() = runBlocking<Unit> {
        val saved = seed(3)
        words.setArchived(saved.last().id, true)
        val state = start()
        assertEquals(FlashcardSettings(), state.settings)
        assertEquals(2, state.total)
        assertEquals(1, state.currentPosition)
        assertEquals(0, state.answered)
        assertTrue(state.question!!.answer.isEmpty())
        assertFalse(state.question!!.revealed)
        assertTrue(state.results.isEmpty())
        assertEquals(state, repository.get(state.id))
        val revealed = repository.reveal(state.id, state.question!!.id)
        assertEquals(listOf("выражение", "слово"), revealed.question!!.answer.map { it.substringBeforeLast(' ') })
        assertEquals(revealed, repository.reveal(state.id, state.question!!.id))
        assertEquals(0, count("flashcard_attempts"))
        assertEquals(0, count("recognition_progress"))
        assertTrue(db.flashcardDao().graph(state.id)!!.cards.none { it.card.wordId == saved.last().id })
        assertEquals(at.toEpochMilli(), db.flashcardDao().card(state.id, state.question!!.id)!!.revealedAt)
    }
    @Test fun allDirectionsUseEveryTranslationWithoutRepeatingWordsAndMixedAlternates() = runBlocking<Unit> {
        seed(5)
        for (direction in FlashcardDirection.entries) {
            var state = start(20, direction)
            val queue = db.flashcardDao().graph(state.id)!!.cards.sortedBy { it.card.position }
            assertEquals(5, queue.map { it.card.wordId }.toSet().size)
            val seen = mutableSetOf<Long>()
            while (state.active) {
                val q = state.question!!
                assertTrue(seen.add(q.id))
                assertEquals(if (q.direction == CardDirection.BG_TO_RU) 1 else 2, q.prompt.size)
                assertTrue(q.answer.isEmpty())
                if (direction != FlashcardDirection.MIXED) assertEquals(direction.name, q.direction.name)
                state = rate(state, false)
            }
            assertEquals(5, seen.size)
            assertEquals(FlashcardStatus.COMPLETED, state.status)
            assertEquals(5, state.unknown)
            if (direction == FlashcardDirection.MIXED) assertTrue(queue.zipWithNext().all { it.first.card.direction != it.second.card.direction })
        }
        assertEquals(CardDirection.BG_TO_RU, cardDirection(FlashcardDirection.MIXED, 0, true))
        assertEquals(CardDirection.RU_TO_BG, cardDirection(FlashcardDirection.MIXED, 0, false))
    }
    @Test fun emptyTranslationlessArchivedAndInvalidSettingsCannotCreateSessions() = runBlocking<Unit> {
        expect(FlashcardError.EMPTY_DICTIONARY) { start() }
        val noTranslation = db.vocabularyDao().insertWord(WordEntity(bulgarianText = "пусто", normalizedText = "пусто",
            partOfSpeech = null, normalizedPartOfSpeech = null, grammaticalGender = null, notes = null, createdAt = 1, updatedAt = 1))
        val archived = seed(1).single()
        words.setArchived(archived.id, true)
        assertEquals(0L, repository.observeAvailable().first())
        expect(FlashcardError.EMPTY_DICTIONARY) { start() }
        for (size in listOf(0, -1, 101)) expect(FlashcardError.INVALID_SETTINGS) { start(size) }
        expect(FlashcardError.INVALID_SETTINGS) { repository.start("not-a-uuid") }
        expect(FlashcardError.SESSION_NOT_FOUND) { repository.get(999) }
        assertEquals(0, count("flashcard_sessions"))
        assertEquals(noTranslation, words.get(noTranslation).id)
    }
    @Test fun partialDictionarySelectionAlwaysKeepsRelationsForTheSameSampledIds() = runBlocking<Unit> {
        val dictionary = seed(12).associateBy { it.id }
        repeat(12) {
            val state = start(3, FlashcardDirection.MIXED)
            val graph = db.flashcardDao().graph(state.id)!!
            assertEquals(3, graph.cards.size)
            assertEquals(3, graph.cards.map { it.card.wordId }.toSet().size)
            graph.cards.forEach { row ->
                val original = dictionary.getValue(row.card.wordId)
                assertEquals(original.bulgarianText, row.card.bulgarian)
                assertEquals(2, kotlinx.serialization.json.Json.parseToJsonElement(row.card.russianJson).let {
                    (it as kotlinx.serialization.json.JsonArray).size
                })
            }
            assertEquals(state, repository.get(state.id))
        }
    }
    @Test fun hiddenFutureForeignRatingsAndEarlyResultsAreRejected() = runBlocking<Unit> {
        seed(2)
        val first = start(2)
        val other = start(1, FlashcardDirection.RU_TO_BG)
        val future = db.flashcardDao().graph(first.id)!!.cards.single { it.card.position == 2 }.card.id
        expect(FlashcardError.ANSWER_HIDDEN) { repository.rate(first.id, first.question!!.id, true) }
        expect(FlashcardError.STALE_CARD) { repository.rate(first.id, other.question!!.id, true) }
        expect(FlashcardError.STALE_CARD) { repository.reveal(first.id, future) }
        expect(FlashcardError.STALE_CARD) { repository.rate(first.id, future, false) }
        expect(FlashcardError.RESULTS_UNAVAILABLE) { repository.results(first.id) }
        assertEquals(0, count("flashcard_attempts"))
        assertEquals(0, count("recognition_progress"))
    }
    @Test fun ratingReplayAfterCompletionIsIdempotentAndDifferentRatingConflicts() = runBlocking<Unit> {
        val word = seed(1).single()
        val state = start(1)
        val done = rate(state, true)
        assertEquals(done, repository.rate(state.id, state.question!!.id, true))
        expect(FlashcardError.RATING_CONFLICT) { repository.rate(state.id, state.question!!.id, false) }
        assertEquals(1, count("flashcard_attempts"))
        assertEquals(RecognitionProgress(word.id, 1, 1, 1, at), repository.progress(word.id))
        assertEquals(CardResult.CORRECT, repository.results(state.id).single().result)
        assertEquals(2, done.currentPosition)
        assertNull(done.question)
        assertEquals(at, done.completedAt)
    }
    @Test fun earlyCompletionKeepsOnlyRatedAttemptsAndReportsUnratedSnapshotsAsSkipped() = runBlocking<Unit> {
        seed(3)
        val first = start(3, FlashcardDirection.MIXED)
        val next = rate(first, false)
        repository.reveal(next.id, next.question!!.id)
        val done = repository.complete(next.id)
        assertEquals(FlashcardStatus.ABANDONED, done.status)
        assertEquals(1, done.answered)
        assertEquals(1, done.unknown)
        assertEquals(2, done.remaining)
        assertEquals(2, done.results.count { it.result == CardResult.SKIPPED })
        assertEquals(done, repository.complete(next.id))
        expect(FlashcardError.SESSION_CLOSED) { repository.rate(next.id, next.question!!.id, true) }
        assertEquals(1, count("flashcard_attempts"))
        assertEquals(1, count("recognition_progress"))
    }
    @Test fun completionWithoutRatingsDoesNotCreateRecognitionProgress() = runBlocking<Unit> {
        seed(2)
        val done = repository.complete(start().id)
        assertEquals(0, done.answered)
        assertTrue(done.results.all { it.result == CardResult.SKIPPED })
        assertEquals(0, count("flashcard_attempts"))
        assertEquals(0, count("recognition_progress"))
    }
    @Test fun directionsShareRecognitionCountersWhileOtherModesRemainUntouchedAndUnknownResetsStreak() = runBlocking<Unit> {
        val word = seed(1).single()
        db.openHelper.writableDatabase.execSQL("CREATE TABLE other_mode_progress(word_id INTEGER PRIMARY KEY, attempts INTEGER NOT NULL)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO other_mode_progress VALUES(${word.id}, 17)")
        rate(start(1), true)
        rate(start(1, FlashcardDirection.RU_TO_BG), true)
        assertEquals(2, repository.progress(word.id)!!.consecutiveKnown)
        rate(start(1, FlashcardDirection.MIXED), false)
        assertEquals(RecognitionProgress(word.id, 3, 2, 0, at), repository.progress(word.id))
        db.openHelper.readableDatabase.query("SELECT attempts FROM other_mode_progress").use { it.moveToFirst(); assertEquals(17, it.getInt(0)) }
    }
    @Test fun concurrentIdenticalRatingsCreateExactlyOneAttemptAndCounterUpdate() = runBlocking<Unit> {
        val word = seed(1).single()
        val state = start(1)
        repository.reveal(state.id, state.question!!.id)
        val results = List(8) { async(Dispatchers.Default) { repository.rate(state.id, state.question!!.id, true) } }.awaitAll()
        assertTrue(results.all { it.known == 1 })
        assertEquals(1, count("flashcard_attempts"))
        assertEquals(1, repository.progress(word.id)!!.attempts)
    }
    @Test fun concurrentOppositeRatingsCommitOneOutcomeAndRejectTheConflictingChoice() = runBlocking<Unit> {
        val word = seed(1).single()
        val state = start(1)
        repository.reveal(state.id, state.question!!.id)
        val results = listOf(true, false).map { known -> async(Dispatchers.Default) { runCatching { repository.rate(state.id, state.question!!.id, known) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(FlashcardError.RATING_CONFLICT, (results.single { it.isFailure }.exceptionOrNull() as FlashcardException).code)
        assertEquals(1, repository.progress(word.id)!!.attempts)
        assertEquals(1, count("flashcard_attempts"))
    }
    @Test fun differentSessionsRatingSameWordCannotLoseRecognitionUpdates() = runBlocking<Unit> {
        val word = seed(1).single()
        val sessions = listOf(start(1), start(1, FlashcardDirection.RU_TO_BG))
        sessions.forEach { repository.reveal(it.id, it.question!!.id) }
        sessions.map { async(Dispatchers.Default) { repository.rate(it.id, it.question!!.id, true) } }.awaitAll()
        assertEquals(RecognitionProgress(word.id, 2, 2, 2, at), repository.progress(word.id))
        assertEquals(2, count("flashcard_attempts"))
    }
    @Test fun concurrentStartRetriesReuseOneDurableQueueAndChangedSettingsAreRejected() = runBlocking<Unit> {
        seed(3)
        val id = UUID.randomUUID().toString()
        val settings = FlashcardSettings(2, FlashcardDirection.MIXED)
        val results = List(2) { async(Dispatchers.Default) { repository.start(id, settings) } }.awaitAll()
        assertEquals(results[0], results[1])
        assertEquals(1, count("flashcard_sessions"))
        assertEquals(2, count("flashcard_cards"))
        expect(FlashcardError.START_REQUEST_CHANGED) { repository.start(id, settings.copy(size = 3)) }
        val done = repository.complete(results[0].id)
        assertEquals(done, repository.start(id, settings))
    }
    @Test fun fileReopenPreservesOrderSnapshotsDirectionsRevealsPositionRatingsAndStatus() = runBlocking<Unit> {
        seed(3)
        val initial = start(3, FlashcardDirection.MIXED)
        val graph = db.flashcardDao().graph(initial.id)!!
        reopen()
        assertEquals(initial, repository.restore())
        assertEquals(graph, db.flashcardDao().graph(initial.id))
        val revealed = repository.reveal(initial.id, initial.question!!.id)
        reopen()
        assertEquals(revealed, repository.restore())
        val next = repository.rate(initial.id, initial.question!!.id, true)
        reopen()
        assertEquals(next, repository.restore())
        assertFalse(repository.restore()!!.question!!.revealed)
        val done = repository.complete(initial.id)
        reopen()
        assertEquals(done, repository.restore())
    }
    @Test fun lostRatingResponseRecoversFromCommittedPositionAndLateRetryDoesNotRateNextCard() = runBlocking<Unit> {
        seed(2)
        val state = start(2)
        rate(state, true) // Simulate losing the UI response after the transaction commits.
        reopen()
        val restored = repository.restore()!!
        assertEquals(2, restored.currentPosition)
        assertEquals(1, restored.known)
        assertFalse(restored.question!!.revealed)
        assertEquals(restored, repository.rate(state.id, state.question!!.id, true))
        assertEquals(1, count("flashcard_attempts"))
    }
    @Test fun snapshotsSurviveEditsArchiveAndProtectedDeleteBeforeAndAfterRating() = runBlocking<Unit> {
        val word = words.create(WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("здание"))))
        val state = start(1)
        words.update(word.id, WordInput("къщичка", listOf(TranslationInput("домик"))))
        words.setArchived(word.id, true)
        try { words.delete(word.id); fail("History must prevent deletion") } catch (_: WordInUseException) { }
        constraint { db.vocabularyDao().deleteWord(word.id) }
        val revealed = repository.reveal(state.id, state.question!!.id)
        assertEquals(listOf("къща"), revealed.question!!.prompt)
        assertEquals(listOf("дом", "здание"), revealed.question!!.answer)
        val done = repository.rate(state.id, state.question!!.id, true)
        assertEquals("къща", done.results.single().bulgarian)
        assertTrue(words.get(word.id).archived)
        try { words.delete(word.id); fail("Rated history must survive") } catch (_: WordInUseException) { }
    }
    @Test fun lateSqliteFailureRollsBackAttemptProgressPositionAndCompletionTogether() = runBlocking<Unit> {
        seed(1)
        val state = start(1)
        val before = repository.reveal(state.id, state.question!!.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_advance BEFORE UPDATE ON flashcard_sessions WHEN NEW.current_position > OLD.current_position BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        constraint { repository.rate(state.id, state.question!!.id, true) }
        assertEquals(before, repository.get(state.id))
        assertEquals(0, count("flashcard_attempts"))
        assertEquals(0, count("recognition_progress"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_advance")
        assertEquals(1, repository.rate(state.id, state.question!!.id, true).known)
    }
    @Test fun failedQueueCreationCannotLeavePartialSessionOrCards() = runBlocking<Unit> {
        seed(2)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_queue BEFORE INSERT ON flashcard_cards WHEN NEW.position=2 BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        constraint { start(2) }
        assertEquals(0, count("flashcard_sessions"))
        assertEquals(0, count("flashcard_cards"))
        assertEquals(2, words.list().size)
    }
    @Test fun roomObservationInvalidatesWhenOnlyRevealChangesAndWhenRatingCommits() = runBlocking<Unit> {
        seed(1)
        val state = start(1)
        val initial = CompletableDeferred<Unit>()
        val revealed = async { withTimeout(10_000) { repository.observe(state.id).onEach { if (it?.question?.revealed == false) initial.complete(Unit) }
            .first { it?.question?.revealed == true } } }
        withTimeout(10_000) { initial.await() }
        repository.reveal(state.id, state.question!!.id)
        assertTrue(revealed.await()!!.question!!.answer.isNotEmpty())
        val finished = async { withTimeout(10_000) { repository.observe(state.id).first { it?.status == FlashcardStatus.COMPLETED } } }
        repository.rate(state.id, state.question!!.id, false)
        assertEquals(1, finished.await()!!.unknown)
    }
    @Test fun contentImportsAndExportsLeaveActualRecognitionProgressAndOldSnapshotsUnchanged() = runBlocking<Unit> {
        val word = words.create(WordInput("къща", listOf(TranslationInput("дом"))))
        val state = start(1)
        val done = rate(state, true)
        val progress = repository.progress(word.id)
        val transfer = ContentTransferRepository(db, clock)
        val p = transfer.preview(ContentKind.WORDS, "words.txt", "къща - здание".toByteArray())
        transfer.confirm(p.copy(rows = p.rows.map { it.copy(selected = true, action = ImportAction.APPEND) }))
        assertEquals(progress, repository.progress(word.id))
        assertEquals(done, repository.get(state.id))
        val export = StringWriter()
        transfer.export(ContentKind.WORDS, true, export)
        assertTrue(export.toString().contains("здание"))
        assertFalse(export.toString().contains("recognition_progress"))
    }
    @Test fun databaseConstraintsAlsoProtectAttemptUniquenessCardMembershipAndHistoryForeignKeys() = runBlocking<Unit> {
        seed(1)
        val state = start(1)
        rate(state, true)
        val card = db.flashcardDao().card(state.id, state.question!!.id)!!
        constraint { db.flashcardDao().insertAttempt(FlashcardAttemptEntity(cardId = card.id, known = true, ratedAt = 1)) }
        constraint { db.flashcardDao().insertCard(card.copy(id = 0, position = 2)) }
        constraint { db.flashcardDao().insertCard(card.copy(id = 0, wordId = 999, position = 2)) }
        constraint { db.flashcardDao().insertAttempt(FlashcardAttemptEntity(cardId = 999, known = true, ratedAt = 1)) }
        assertEquals(1, count("flashcard_attempts"))
    }
}
