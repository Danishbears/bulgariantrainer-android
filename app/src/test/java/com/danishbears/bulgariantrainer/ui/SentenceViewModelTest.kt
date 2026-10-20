package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.sentences.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class SentenceViewModelTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var repository: SentencePracticeRepository
    private lateinit var content: SentenceRepository
    private val store = ViewModelStore()
    private var number = 0
    private val filename = "sentence-vm-${UUID.randomUUID()}.db"
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); context = ApplicationProvider.getApplicationContext(); open() }
    private fun open() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        repository = SentencePracticeRepository(db); content = SentenceRepository(db)
    }
    @After fun teardown() { store.clear(); db.close(); context.deleteDatabase(filename); Dispatchers.resetMain() }
    private fun model(handle: SavedStateHandle = SavedStateHandle()) = SentencePracticeViewModel(repository, handle).also { store.put("practice${number++}", it) }
    private fun editor(handle: SavedStateHandle = SavedStateHandle()) = SentenceEditorViewModel(content, handle).also { store.put("editor${number++}", it) }
    private suspend fun ready(model: SentencePracticeViewModel, predicate: (SentencePracticeUiState) -> Boolean = { true }) =
        withTimeout(10_000) { model.state.first { !it.loading && !it.busy && predicate(it) } }
    private suspend fun edited(model: SentenceEditorViewModel, predicate: (SentenceEditorState) -> Boolean = { true }) =
        withTimeout(10_000) { model.state.first { !it.loading && !it.saving && predicate(it) } }
    private suspend fun seed(count: Int) = repeat(count) { content.create(SentenceInput("Русское $it", "Българско $it", listOf("Вариант $it"))) }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun emptyAvailabilityAndInvalidSizeAreExplicitAndNeverCreateSession() = runBlocking<Unit> {
        val vm = model(); ready(vm)
        val available = withTimeout(10_000) { vm.availability.first { it is SentencePracticeAvailability.Ready } } as SentencePracticeAvailability.Ready
        assertEquals(0L, available.count)
        vm.setSize("101"); vm.start(); assertEquals(SentenceError.INVALID_SETTINGS, vm.state.value.error)
        vm.setSize("10"); vm.start(); ready(vm) { it.error == SentenceError.EMPTY_SENTENCES }
        assertEquals(0, count("sentence_sessions"))
    }
    @Test fun rapidSubmissionsSelfRatingsAndDelayedCallbacksCannotDoubleCountOrRateNextQuestion() = runBlocking<Unit> {
        seed(2); val vm = model(); ready(vm); vm.start(); vm.start()
        val state = ready(vm) { it.session != null }.session!!; val q = state.question!!
        assertEquals(1, count("sentence_sessions"))
        vm.setInput("Мой вариант.\nВторая строка."); vm.submit(state.id, q.id, vm.input.value); vm.submit(state.id, q.id, vm.input.value)
        val pending = ready(vm) { it.session?.pending == 1 }.session!!
        assertEquals(0, pending.evaluated); assertEquals(1, count("sentence_attempts"))
        vm.selfRate(state.id, q.id, SentenceRating.PARTIAL); vm.selfRate(state.id, q.id, SentenceRating.PARTIAL)
        val rated = ready(vm) { it.session?.evaluated == 1 }.session!!
        assertEquals(SentenceEvaluation.SELF, rated.question!!.feedback!!.evaluation)
        assertEquals(1, count("sentence_attempts")); assertEquals(1, count("sentence_progress"))
        vm.next(state.id, q.id); vm.next(state.id, q.id)
        ready(vm) { it.session?.currentPosition == 2 }; assertEquals("", vm.input.value)
        vm.selfRate(state.id, q.id, SentenceRating.PARTIAL); ready(vm)
        assertNull(vm.state.value.session!!.question!!.feedback); assertEquals(1, count("sentence_attempts"))
    }
    @Test fun freshEmptySavedStateRecoversPendingThenFinalManualFeedbackAndAcknowledgedPosition() = runBlocking<Unit> {
        seed(3); var vm = model(); ready(vm); vm.start()
        val state = ready(vm) { it.session != null }.session!!; val q = state.question!!
        vm.submit(state.id, q.id, " \nВариант.\n ")
        val before = ready(vm) { it.session?.pending == 1 }.session!!
        store.clear(); db.close(); open(); vm = model(SavedStateHandle())
        assertEquals(before, ready(vm) { it.session != null }.session); assertEquals(" \nВариант.\n ", vm.input.value)
        vm.selfRate(state.id, q.id, SentenceRating.CORRECT)
        val rated = ready(vm) { it.session?.evaluated == 1 }.session!!
        store.clear(); db.close(); open(); vm = model(SavedStateHandle())
        assertEquals(rated, ready(vm) { it.session != null }.session)
        vm.next(state.id, q.id); val next = ready(vm) { it.session?.currentPosition == 2 }.session!!
        store.clear(); db.close(); open(); vm = model(SavedStateHandle())
        assertEquals(next, ready(vm) { it.session != null }.session); assertEquals("", vm.input.value)
    }
    @Test fun completedAutomaticFeedbackAndSummaryRecoverWithoutLosingLastAcknowledgement() = runBlocking<Unit> {
        seed(1); val state = repository.start(UUID.randomUUID().toString()); val q = state.question!!
        repository.submit(state.id, q.id, "Вариант 0") // Old UI never receives response.
        db.close(); open(); val vm = model()
        val feedback = ready(vm) { it.session != null }.session!!
        assertEquals(SentenceStatus.COMPLETED, feedback.status); assertTrue(feedback.question!!.feedback!!.automaticMatch)
        vm.newSession(); assertNotNull(vm.state.value.session)
        repository.next(state.id, q.id)
        store.clear(); db.close(); open(); val restored = model()
        val summary = ready(restored) { it.session != null }.session!!
        assertNull(summary.question); assertEquals(1, summary.review.size)
        restored.newSession(); assertNull(restored.state.value.session); assertEquals(1, count("sentence_sessions"))
    }
    @Test fun failedSelfRatingPreservesExactPayloadAndPendingAttemptUntilRetry() = runBlocking<Unit> {
        seed(1); val vm = model(); ready(vm); vm.start()
        val state = ready(vm) { it.session != null }.session!!; val q = state.question!!
        vm.submit(state.id, q.id, "Вариант."); ready(vm) { it.session?.pending == 1 }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sentence_vm BEFORE UPDATE ON sentence_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        vm.selfRate(state.id, q.id, SentenceRating.PARTIAL)
        val failed = ready(vm) { it.error == SentenceError.STORAGE_FAILED }
        assertEquals(SentencePracticeAction.SelfRate(state.id, q.id, SentenceRating.PARTIAL), failed.pending)
        assertEquals(0, count("sentence_progress")); assertEquals(1, count("sentence_attempts"))
        vm.setInput("другое"); assertEquals("Вариант.", vm.input.value)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_sentence_vm")
        vm.retry(); val recovered = ready(vm) { it.session?.partial == 1 }
        assertNull(recovered.pending); assertNull(recovered.error); assertEquals(1, count("sentence_attempts"))
    }
    @Test fun earlyCompletionKeepsUnassessedAnswerInSummaryAndDoesNotChangeProgress() = runBlocking<Unit> {
        seed(2); val vm = model(); ready(vm); vm.start()
        val state = ready(vm) { it.session != null }.session!!; vm.submit(state.id, state.question!!.id, "Вариант.")
        ready(vm) { it.session?.pending == 1 }; vm.complete(state.id)
        val summary = ready(vm) { it.session?.status == SentenceStatus.ABANDONED }.session!!
        assertEquals(1, summary.pending); assertEquals(1, summary.unanswered); assertEquals(0, summary.evaluated)
        assertEquals(1, count("sentence_attempts")); assertEquals(0, count("sentence_progress"))
    }
    @Test fun editorValidationOptionalDifficultyAndRapidSavesCreateOneApprovedSentence() = runBlocking<Unit> {
        val vm = editor(); edited(vm); vm.save(); assertEquals(SentenceError.INVALID_SENTENCE, vm.state.value.error)
        vm.change { it.copy(russian = "Дом.", bulgarian = "Къща.", difficulty = "не число") }; vm.save()
        assertEquals(SentenceError.INVALID_SENTENCE, vm.state.value.error)
        vm.change { it.copy(difficulty = "", alternatives = listOf("Дом.")) }; vm.save(); vm.save()
        edited(vm) { it.saved }; assertEquals(1, count("sentences"))
        val saved = content.get(1); assertNull(saved.input.difficulty); assertTrue(saved.approved); assertEquals(listOf("Дом."), saved.input.alternatives)
    }
    @Test fun unsavedEditorDraftSurvivesNewViewModelWithSavedStateAndPreservesMultilineText() = runBlocking<Unit> {
        val handle = SavedStateHandle(); val vm = editor(handle); edited(vm)
        vm.change { it.copy(russian = "Первый.\nВторой.", bulgarian = "Първи.\nВтори.", alternatives = listOf("Вариант."), difficulty = "3") }
        val draft = vm.state.value.draft
        store.clear(); val restored = editor(handle)
        assertEquals(draft, edited(restored).draft); assertTrue(restored.state.value.dirty); assertEquals(0, count("sentences"))
    }
    @Test fun editorLoadsExistingAlternativesAndKeepsArchiveProvenanceOnUpdate() = runBlocking<Unit> {
        val record = content.create(SentenceInput("Дом.", "Къща.", listOf("Дом."), difficulty = 2))
        content.setArchived(record.id, true)
        val vm = editor(SavedStateHandle(mapOf("sentenceId" to record.id.toString())))
        assertEquals(listOf("Дом."), edited(vm).draft.alternatives)
        vm.change { it.copy(bulgarian = "Това е къща.", difficulty = "") }; vm.save(); edited(vm) { it.saved }
        val saved = content.get(record.id); assertTrue(saved.archived); assertEquals(record.sourceId, saved.sourceId); assertNull(saved.input.difficulty)
    }
    @Test fun listArchiveRetryIsIdempotentAndUnknownEditorCannotSaveNewContent() = runBlocking<Unit> {
        val record = content.create(SentenceInput("Дом.", "Къща."))
        val vm = SentenceListViewModel(content, SavedStateHandle()).also { store.put("list", it) }
        vm.archive(record.id, true); vm.archive(record.id, true)
        withTimeout(10_000) { vm.busy.first { !it } }; assertTrue(content.get(record.id).archived)
        vm.showArchive(true); assertTrue(vm.archived.value)
        val unknown = editor(SavedStateHandle(mapOf("sentenceId" to "999")))
        edited(unknown) { it.loadFailed }; unknown.save()
        assertEquals(SentenceError.SENTENCE_NOT_FOUND, unknown.state.value.error); assertEquals(1, count("sentences"))
    }
}
