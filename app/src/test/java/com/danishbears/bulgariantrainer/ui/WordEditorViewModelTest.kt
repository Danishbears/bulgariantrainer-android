package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.FieldIssue
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.TranslationInput
import com.danishbears.bulgariantrainer.data.VocabularyFilter
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.data.WordInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class WordEditorViewModelTest {
    private lateinit var database: TrainerDatabase
    private lateinit var repository: VocabularyRepository
    private val store = ViewModelStore()
    private var modelNumber = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java).build()
        repository = VocabularyRepository(database)
    }
    @After fun tearDown() {
        store.clear()
        database.close()
        Dispatchers.resetMain()
    }
    private fun editor(handle: SavedStateHandle = SavedStateHandle()) = WordEditorViewModel(repository, handle)
        .also { store.put("editor${modelNumber++}", it) }
    private suspend fun settled(model: WordEditorViewModel, predicate: (EditorState) -> Boolean) =
        withTimeout(10_000) { model.state.first(predicate) }
    private fun fill(model: WordEditorViewModel) {
        model.change { it.copy(bulgarian = " КЪЩА ", part = " съществително ", gender = " женский ", notes = "заметка") }
        model.translation(model.state.value.draft.translations.first().key, "Дом")
        model.addTranslation()
        model.translation(model.state.value.draft.translations.last().key, "жилище")
        model.change { it.copy(primary = 1) }
    }

    @Test fun invalidFormShowsFieldErrorsWithoutCreatingAnyRecords() = runBlocking<Unit> {
        val model = editor()
        model.save()
        assertEquals(FieldIssue.REQUIRED, model.state.value.errors.bulgarian)
        assertEquals(FieldIssue.REQUIRED, model.state.value.errors.translations[0])
        fill(model)
        model.translation(model.state.value.draft.translations.last().key, " ДОМ\u00A0")
        model.save()
        assertEquals(FieldIssue.DUPLICATE, model.state.value.errors.translations[1])
        assertTrue(repository.list().isEmpty())
    }

    @Test fun saveTwiceCreatesOneCompleteWordWithChosenPrimaryAndMetadata() = runBlocking<Unit> {
        val model = editor()
        fill(model)
        model.save()
        model.save()
        val state = settled(model) { it.saved && !it.saving }
        assertFalse(state.dirty)
        val word = repository.list().single()
        assertEquals("КЪЩА", word.bulgarianText)
        assertEquals(listOf("жилище", "Дом"), word.translations.map { it.russianText })
        assertTrue(word.translations.first().isPrimary)
        assertEquals("съществително", word.partOfSpeech)
        assertEquals("женский", word.grammaticalGender)
        assertEquals("заметка", word.notes)
    }

    @Test fun duplicateIncludingArchiveKeepsDraftAndShowsBulgarianFieldError() = runBlocking<Unit> {
        val existing = repository.create(WordInput("къща", listOf(TranslationInput("дом"))))
        repository.setArchived(existing.id, true)
        val model = editor()
        fill(model)
        model.save()
        val state = settled(model) { !it.saving && it.errors.bulgarian == FieldIssue.DUPLICATE }
        assertTrue(state.dirty)
        assertEquals(" КЪЩА ", state.draft.bulgarian)
        assertFalse(state.saved)
        assertEquals(1, repository.list(VocabularyFilter(archived = true)).size)
        assertTrue(repository.list().isEmpty())
    }

    @Test fun savedStateRestoresUnsavedTranslationsKeysPrimaryAndMetadata() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val original = editor(handle)
        fill(original)
        val copied = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = editor(copied)
        assertEquals(original.state.value.draft, restored.state.value.draft)
        assertTrue(restored.state.value.dirty)
        assertTrue(repository.list().isEmpty())
        restored.removeTranslation(0)
        assertEquals(0, restored.state.value.draft.primary)
        assertEquals("жилище", restored.state.value.draft.translations.single().text)
        restored.save()
        settled(restored) { it.saved && !it.saving }
        assertEquals("жилище", repository.list().single().translations.single().russianText)
    }

    @Test fun editingArchivedWordKeepsIdentityAndArchiveAndRemovingPrimaryChoosesSurvivor() = runBlocking<Unit> {
        val before = repository.create(WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("жилище"))))
        repository.setArchived(before.id, true)
        val model = editor(SavedStateHandle(mapOf("wordId" to before.id.toString())))
        settled(model) { !it.loading }
        model.removeTranslation(0)
        model.change { it.copy(notes = "обновлено") }
        model.save()
        settled(model) { it.saved && !it.saving }
        val after = repository.get(before.id)
        assertTrue(after.archived)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.translations[1].id, after.translations.single().id)
        assertTrue(after.translations.single().isPrimary)
        assertEquals("обновлено", after.notes)
    }

    @Test fun dashboardUsesRepositoryCountsAndArchiveActionRefreshesState() = runBlocking<Unit> {
        val saved = repository.create(WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("жилище"))))
        val model = VocabularyViewModel(repository, SavedStateHandle())
        store.put("vocabulary", model)
        val initial = withTimeout(10_000) { model.dashboard.first { it is DashboardState.Ready } } as DashboardState.Ready
        assertEquals(1L, initial.counts.active)
        assertEquals(2L, initial.counts.translations)
        model.archive(saved.id, true)
        withTimeout(10_000) { model.notice.first { it?.kind == NoticeKind.ARCHIVED } }
        val archived = withTimeout(10_000) {
            model.dashboard.first { it is DashboardState.Ready && it.counts.archived == 1L }
        } as DashboardState.Ready
        assertEquals(0L, archived.counts.active)
        assertEquals(1L, archived.counts.total)
        assertTrue(model.busy.value.isEmpty())
        assertTrue(repository.get(saved.id).archived)
    }

    @Test fun searchAndArchiveSelectionAreRestoredAndOversizedQueryIsExplicit() {
        val handle = SavedStateHandle()
        val original = VocabularyViewModel(repository, handle)
        store.put("controls", original)
        original.setQuery("а".repeat(256))
        original.showArchived(true)
        val restored = VocabularyViewModel(repository, SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        store.put("restoredControls", restored)
        assertTrue(restored.controls.value.queryTooLong)
        assertTrue(restored.controls.value.archived)
        assertEquals("а".repeat(256), restored.controls.value.query)
    }

    @Test fun missingWordIsExplicitAndCannotBeSavedAsAnAccidentalNewWord() = runBlocking<Unit> {
        val model = editor(SavedStateHandle(mapOf("wordId" to "123")))
        val state = settled(model) { !it.loading }
        assertEquals(EditorFailure.NOT_FOUND, state.loadFailure)
        model.save()
        assertFalse(model.state.value.saving)
        assertTrue(repository.list().isEmpty())
    }
}
