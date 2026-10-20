package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.typing.*
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
class TypingViewModelTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var repository: TypingRepository
    private val store = ViewModelStore()
    private var number = 0
    private val filename = "typing-vm-${UUID.randomUUID()}.db"
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        repository = TypingRepository(db)
    }
    @After fun teardown() { store.clear(); db.close(); context.deleteDatabase(filename); Dispatchers.resetMain() }
    private fun model(handle: SavedStateHandle = SavedStateHandle()) = TypingViewModel(repository, handle).also { store.put("typing${number++}", it) }
    private suspend fun ready(model: TypingViewModel, predicate: (TypingUiState) -> Boolean = { true }) =
        withTimeout(10_000) { model.state.first { !it.loading && !it.busy && predicate(it) } }
    private suspend fun seed(count: Int) = repeat(count) { VocabularyRepository(db).create(WordInput("дума$it", listOf(TranslationInput("слово$it")))) }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun emptyAndShortVocabularyGiveExplicitErrorsAndConsentIsRequired() = runBlocking<Unit> {
        val vm = model(); ready(vm)
        val available = withTimeout(10_000) { vm.availability.first { it is TypingAvailability.Ready } } as TypingAvailability.Ready
        assertEquals(0L, available.count)
        vm.start(true); ready(vm) { it.error == TypingError.EMPTY_DICTIONARY }
        seed(5); vm.start(); ready(vm) { it.error == TypingError.SHORTER_SESSION_REQUIRED }
        assertEquals(0, count("typing_sessions"))
        vm.start(true); assertEquals(5, ready(vm) { it.session != null }.session!!.total)
    }
    @Test fun rapidStartSubmitNextAndDelayedCallbackDoNotDuplicateOrAnswerTheNextQuestion() = runBlocking<Unit> {
        seed(2); val vm = model(); ready(vm); vm.start(true); vm.start(true)
        val initial = ready(vm) { it.session != null }.session!!; val q = initial.question!!
        assertEquals(1, count("typing_sessions"))
        vm.setInput("ошибка"); vm.submit(initial.id, q.id, vm.input.value); vm.submit(initial.id, q.id, "ошибка")
        val feedback = ready(vm) { it.session?.question?.feedback != null }.session!!
        assertEquals(q.id, feedback.question!!.id); assertEquals("ошибка", vm.input.value)
        assertEquals(1, count("typing_attempts"))
        vm.next(initial.id, q.id); vm.next(initial.id, q.id)
        val next = ready(vm) { it.session?.currentPosition == 2 }.session!!
        assertNull(next.question!!.feedback); assertEquals("", vm.input.value)
        vm.submit(initial.id, q.id, "ошибка"); ready(vm)
        assertEquals(1, count("typing_attempts")); assertNull(vm.state.value.session!!.question!!.feedback)
    }
    @Test fun emptySavedStateAndReopenedDatabaseRecoverSubmittedFeedbackAndAcknowledgedPosition() = runBlocking<Unit> {
        seed(3); var vm = model(); ready(vm); vm.start(true)
        val initial = ready(vm) { it.session != null }.session!!; val q = initial.question!!
        vm.submit(initial.id, q.id, "  ОШИБКА ")
        val before = ready(vm) { it.session?.answered == 1 }.session!!
        store.clear(); db.close(); open(); vm = model(SavedStateHandle())
        assertEquals(before, ready(vm) { it.session != null }.session)
        assertEquals("  ОШИБКА ", vm.input.value)
        vm.next(initial.id, q.id); val next = ready(vm) { it.session?.currentPosition == 2 }.session!!
        store.clear(); db.close(); open(); vm = model(SavedStateHandle())
        assertEquals(next, ready(vm) { it.session != null }.session); assertEquals("", vm.input.value)
    }
    @Test fun finalFeedbackCannotBeBypassedAndSummaryRemainsAfterLostAcknowledgementResponse() = runBlocking<Unit> {
        seed(1); val initial = repository.start(UUID.randomUUID().toString(), true)
        repository.submit(initial.id, initial.question!!.id, "ошибка")
        db.close(); open(); val vm = model()
        val feedback = ready(vm) { it.session != null }.session!!
        assertEquals(TypingStatus.COMPLETED, feedback.status); assertNotNull(feedback.question!!.feedback)
        vm.newSession(); assertNotNull(vm.state.value.session)
        repository.next(feedback.id, feedback.question!!.id) // Response lost to former UI.
        store.clear(); db.close(); open(); val fresh = model()
        val summary = ready(fresh) { it.session != null }.session!!
        assertNull(summary.question); assertEquals(1, summary.mistakes.size)
        fresh.newSession(); assertNull(fresh.state.value.session); assertEquals(1, count("typing_sessions"))
        fresh.reload(force = true); assertEquals(summary, ready(fresh) { it.session != null }.session)
    }
    @Test fun failedSubmissionRetainsExactPayloadAndRollsBackUntilExplicitRetry() = runBlocking<Unit> {
        seed(1); val vm = model(); ready(vm); vm.start(true)
        val initial = ready(vm) { it.session != null }.session!!; val q = initial.question!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_typing_vm BEFORE UPDATE ON typing_sessions BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        vm.setInput(" ТЕКСТ "); vm.submit(initial.id, q.id, vm.input.value)
        val failure = ready(vm) { it.error == TypingError.STORAGE_FAILED }
        assertEquals(TypingAction.Submit(initial.id, q.id, " ТЕКСТ "), failure.pending)
        assertEquals(0, count("typing_attempts")); vm.setInput("другое"); assertEquals(" ТЕКСТ ", vm.input.value)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_typing_vm")
        vm.retry(); val recovered = ready(vm) { it.session?.answered == 1 }
        assertNull(recovered.pending); assertNull(recovered.error)
        assertEquals(" ТЕКСТ ", recovered.session!!.question!!.feedback!!.submitted); assertEquals(1, count("typing_attempts"))
    }
    @Test fun earlyCompletionWithNoAnswersCreatesNoAttemptsOrDirectionProgress() = runBlocking<Unit> {
        seed(2); val vm = model(); ready(vm); vm.start(true)
        val initial = ready(vm) { it.session != null }.session!!
        vm.complete(initial.id); val summary = ready(vm) { it.session?.status == TypingStatus.ABANDONED }.session!!
        assertEquals(2, summary.remaining); assertTrue(summary.mistakes.isEmpty()); assertNull(summary.question)
        assertEquals(0, count("typing_attempts")); assertEquals(0, count("typing_progress"))
    }
}
