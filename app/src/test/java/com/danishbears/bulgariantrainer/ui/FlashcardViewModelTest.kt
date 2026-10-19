package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FlashcardViewModelTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var repository: FlashcardRepository
    private val store = ViewModelStore()
    private var number = 0
    private val filename = "flashcard-vm-${UUID.randomUUID()}.db"
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        repository = FlashcardRepository(db)
    }
    @After fun teardown() { store.clear(); db.close(); context.deleteDatabase(filename); Dispatchers.resetMain() }
    private fun model(handle: SavedStateHandle = SavedStateHandle()) = FlashcardViewModel(repository, handle).also { store.put("model${number++}", it) }
    private suspend fun ready(model: FlashcardViewModel, predicate: (FlashcardUiState) -> Boolean = { true }) =
        withTimeout(10_000) { model.state.first { !it.loading && !it.busy && predicate(it) } }
    private suspend fun seed(count: Int) = repeat(count) { VocabularyRepository(db).create(WordInput("дума$it", listOf(TranslationInput("слово$it")))) }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun emptyAndInvalidSettingsShowExplicitErrorsWithoutCreatingSessions() = runBlocking<Unit> {
        val vm = model(); ready(vm)
        val available = withTimeout(10_000) { vm.availability.first { it is FlashcardAvailability.Ready } } as FlashcardAvailability.Ready
        assertEquals(0L, available.count)
        vm.setSize("101"); vm.start()
        assertEquals(FlashcardError.INVALID_SETTINGS, vm.state.value.error)
        vm.setSize("20"); vm.start()
        ready(vm) { it.error == FlashcardError.EMPTY_DICTIONARY }
        assertNull(vm.state.value.pending)
        assertNull(vm.state.value.session)
        assertEquals(0, count("flashcard_sessions"))
    }
    @Test fun rapidStartRevealAndRatingTapsDoNotCreateExtraSessionsAttemptsOrRateTheNextCard() = runBlocking<Unit> {
        seed(2)
        val vm = model(); ready(vm)
        vm.start(); vm.start()
        var state = ready(vm) { it.session != null }.session!!
        assertEquals(1, count("flashcard_sessions"))
        val first = state.question!!
        vm.reveal(state.id, first.id); vm.reveal(state.id, first.id)
        state = ready(vm) { it.session?.question?.revealed == true }.session!!
        assertEquals(0, count("flashcard_attempts"))
        vm.rate(state.id, first.id, true); vm.rate(state.id, first.id, true)
        val next = ready(vm) { it.session?.answered == 1 }.session!!
        assertEquals(2, next.currentPosition)
        assertFalse(next.question!!.revealed)
        vm.rate(state.id, first.id, true) // A queued callback still carries the old card ID.
        ready(vm)
        assertEquals(1, count("flashcard_attempts"))
        assertEquals(1, vm.state.value.session!!.answered)
    }
    @Test fun freshViewModelWithEmptySavedStateRecoversFromReopenedDatabaseNotOldMemory() = runBlocking<Unit> {
        seed(3)
        var vm = model(); ready(vm)
        vm.setDirection(FlashcardDirection.MIXED); vm.setSize("3"); vm.start()
        val first = ready(vm) { it.session != null }.session!!
        vm.reveal(first.id, first.question!!.id)
        ready(vm) { it.session?.question?.revealed == true }
        vm.rate(first.id, first.question!!.id, false)
        val next = ready(vm) { it.session?.answered == 1 }.session!!
        vm.reveal(next.id, next.question!!.id)
        val before = ready(vm) { it.session?.question?.revealed == true }.session!!
        store.clear(); db.close(); open()
        vm = model(SavedStateHandle()) // No session ID or queue is supplied by ViewModel memory.
        val restored = ready(vm) { it.session != null }.session!!
        assertEquals(before, restored)
        vm.rate(restored.id, restored.question!!.id, true)
        val after = ready(vm) { it.session?.answered == 2 }.session!!
        assertEquals(3, after.currentPosition)
        assertFalse(after.question!!.revealed)
    }
    @Test fun completedSessionSummaryAlsoRecoversAfterLostLastRatingResponseAndNewSetupRetainsHistory() = runBlocking<Unit> {
        seed(1)
        val state = repository.start(UUID.randomUUID().toString())
        repository.reveal(state.id, state.question!!.id)
        repository.rate(state.id, state.question!!.id, true) // The former UI never receives this result.
        db.close(); open()
        val vm = model()
        val restored = ready(vm) { it.session != null }.session!!
        assertEquals(FlashcardStatus.COMPLETED, restored.status)
        assertEquals(CardResult.CORRECT, restored.results.single().result)
        vm.newSession()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.hasHistory)
        assertEquals(1, count("flashcard_sessions"))
        vm.reload(force = true)
        assertEquals(restored, ready(vm) { it.session != null }.session)
    }
    @Test fun failedRatingRetainsExactRetryPayloadAndRetryCommitsOnceAfterRollback() = runBlocking<Unit> {
        seed(2)
        val vm = model(); ready(vm); vm.start()
        val state = ready(vm) { it.session != null }.session!!
        vm.reveal(state.id, state.question!!.id)
        ready(vm) { it.session?.question?.revealed == true }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_vm_advance BEFORE UPDATE ON flashcard_sessions WHEN NEW.current_position > OLD.current_position BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        vm.rate(state.id, state.question!!.id, true)
        val failed = ready(vm) { it.error == FlashcardError.STORAGE_FAILED }
        assertEquals(FlashcardAction.Rate(state.id, state.question!!.id, true), failed.pending)
        assertEquals(0, count("flashcard_attempts"))
        vm.setSize("99")
        assertEquals("20", vm.size.value)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_vm_advance")
        vm.retry()
        val recovered = ready(vm) { it.session?.answered == 1 }
        assertNull(recovered.pending)
        assertNull(recovered.error)
        assertEquals(1, count("flashcard_attempts"))
        assertFalse(recovered.session!!.question!!.revealed)
    }
    @Test fun earlyCompletionKeepsAnExplicitUnratedSummaryAndNoAttempts() = runBlocking<Unit> {
        seed(2)
        val vm = model(); ready(vm); vm.start()
        val state = ready(vm) { it.session != null }.session!!
        vm.complete(state.id)
        val done = ready(vm) { it.session?.active == false }.session!!
        assertEquals(FlashcardStatus.ABANDONED, done.status)
        assertEquals(2, done.remaining)
        assertTrue(done.results.all { it.result == CardResult.SKIPPED })
        assertEquals(0, count("flashcard_attempts"))
        assertEquals(0, count("recognition_progress"))
    }
}
