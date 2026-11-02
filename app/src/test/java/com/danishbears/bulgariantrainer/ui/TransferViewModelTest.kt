package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.transfer.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

class FixtureDocuments : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/csv"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TransferViewModelTest {
    private lateinit var db: TrainerDatabase
    private lateinit var repository: ContentTransferRepository
    private lateinit var gateway: DocumentGateway
    private lateinit var resolver: ShadowContentResolver
    private lateinit var model: TransferViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java).build()
        repository = ContentTransferRepository(db)
        gateway = DocumentGateway(context)
        resolver = shadowOf(context.contentResolver)
        ShadowContentResolver.registerProviderInternal("documents", Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())
        model = TransferViewModel(repository, gateway, SavedStateHandle())
        store.put("transfer", model)
    }
    @After fun teardown() { store.clear(); db.close(); Dispatchers.resetMain() }
    private suspend fun settled(predicate: (TransferState) -> Boolean) = withTimeout(10_000) { model.state.first(predicate) }
    private fun input(name: String, bytes: ByteArray): Uri = Uri.parse("content://documents/$name").also {
        resolver.registerInputStream(it, ByteArrayInputStream(bytes))
    }
    @Test fun systemDocumentReadOnlyPreviewsUntilConfirmationAndDoubleTapIsGuarded() = runBlocking<Unit> {
        model.preparePicker()
        model.read(input("words.txt", "куче - собака".toByteArray()))
        settled { !it.busy && it.preview != null }
        assertTrue(VocabularyRepository(db).list().isEmpty())
        model.confirm(); model.confirm()
        val result = settled { !it.busy && it.summary != null }
        assertEquals(1, result.summary!!.created)
        assertEquals(1, VocabularyRepository(db).list().size)
    }
    @Test fun invalidEncodingAndBoundedDocumentReadsLeaveDatabaseUntouched() = runBlocking<Unit> {
        model.preparePicker()
        model.read(input("words.txt", byteArrayOf(0xd0.toByte())))
        settled { !it.busy && it.error == TransferError.INVALID_UTF8 }
        model.read(input("large.txt", ByteArray(ContentParser.MAX_BYTES + 1)))
        settled { !it.busy && it.error == TransferError.FILE_TOO_LARGE }
        assertTrue(VocabularyRepository(db).list().isEmpty())
    }
    @Test fun correctedPreviewIsRetainedInViewModelAndExplicitCombineRestoresMultipleTranslations() = runBlocking<Unit> {
        model.preparePicker()
        model.read(input("words.txt", "куче - собака\nКУЧЕ - пёс".toByteArray()))
        val initial = settled { !it.busy && it.preview != null }.preview!!
        model.beginEdit(2)
        model.changeDraft { it.copy(word = it.word!!.copy(translations = listOf(com.danishbears.bulgariantrainer.data.TranslationInput("домашний пёс")))) }
        model.applyEdit()
        val corrected = settled { !it.busy && it.editing == null }.preview!!
        assertEquals(initial.rows[1].source, corrected.rows[1].source)
        assertTrue(corrected.rows[1].edited)
        assertTrue(VocabularyRepository(db).list().isEmpty())
        model.combineWords(); model.confirm()
        settled { !it.busy && it.summary != null }
        assertEquals(listOf("собака", "домашний пёс"), VocabularyRepository(db).list().single().translations.map { it.russianText })
    }
    @Test fun exportUsesTheExplicitCreationUriAndFrozenExportSettings() = runBlocking<Unit> {
        VocabularyRepository(db).create(com.danishbears.bulgariantrainer.data.WordInput("къща", listOf(com.danishbears.bulgariantrainer.data.TranslationInput("дом"))))
        model.setKind(ContentKind.WORDS)
        assertEquals("bulgarian-vocabulary.csv", model.prepareExport())
        model.setKind(ContentKind.SENTENCES) // Picker may outlive a composition; pending format is frozen.
        val uri = Uri.parse("content://documents/export.csv")
        val bytes = ByteArrayOutputStream()
        resolver.registerOutputStream(uri, bytes)
        model.export(uri)
        settled { !it.busy && it.exported != null }
        assertEquals("bulgarian,russian,partOfSpeech\r\nкъща,дом,\r\n", bytes.toString("UTF-8"))
        assertEquals(1, VocabularyRepository(db).list().size)
    }
    @Test fun sentenceExportExplainsMissingOptionalDifficultyWithoutWritingInvalidCsv() = runBlocking<Unit> {
        com.danishbears.bulgariantrainer.sentences.SentenceRepository(db).create(
            com.danishbears.bulgariantrainer.sentences.SentenceInput("Дом.", "Къща."))
        model.setKind(ContentKind.SENTENCES); model.prepareExport()
        val uri = Uri.parse("content://documents/sentences.csv")
        val bytes = ByteArrayOutputStream(); resolver.registerOutputStream(uri, bytes)
        model.export(uri)
        settled { !it.busy && it.error == TransferError.SENTENCE_DIFFICULTY_REQUIRED }
        assertEquals(0, bytes.size()); assertNull(model.state.value.exported)
    }
    @Test fun recreatingAfterProcessLossDoesNotAutomaticallyReadOrImportDocuments() = runBlocking<Unit> {
        val restored = TransferViewModel(repository, gateway, SavedStateHandle(mapOf("hasPreview" to true)))
        store.put("restored", restored)
        assertTrue(restored.state.value.previewLost)
        assertNull(restored.state.value.preview)
        assertFalse(restored.state.value.busy)
        assertTrue(VocabularyRepository(db).list().isEmpty())
    }
}
