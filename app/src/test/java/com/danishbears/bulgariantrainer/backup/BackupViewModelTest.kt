package com.danishbears.bulgariantrainer.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.review.StudyScope
import com.danishbears.bulgariantrainer.ui.*
import java.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupViewModelTest {
    private lateinit var db: TrainerDatabase
    private lateinit var repo: BackupRepository
    private lateinit var documents: BackupDocuments
    private lateinit var resolver: ShadowContentResolver
    private lateinit var model: BackupViewModel
    private val store=ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build()
        repo=BackupRepository(context,db);documents=BackupDocuments(context);resolver=shadowOf(context.contentResolver)
        ShadowContentResolver.registerProviderInternal("backup-documents",Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())
        model=BackupViewModel(repo,documents);store.put("backup",model)
    }
    @After fun teardown(){store.clear();db.close();Dispatchers.resetMain()}
    private suspend fun word(text:String)=VocabularyRepository(db).create(WordInput(text,listOf(TranslationInput("перевод"))))
    private fun input(bytes: ByteArray)=Uri.parse("content://backup-documents/${System.nanoTime()}.json").also{resolver.registerInputStream(it,ByteArrayInputStream(bytes))}
    private suspend fun settled(predicate:(BackupUiState)->Boolean)=withTimeout(15_000){model.state.first(predicate)}
    @Test fun selectionOnlyValidatesAndShowsSummaryUntilExplicitConfirmedReplacement()=runBlocking<Unit> {
        word("котка");val backup=repo.snapshot();word("куче")
        model.inspect(input(backup));val preview=settled{!it.busy && it.summary!=null}
        assertEquals(1,preview.summary!!.words);assertEquals(2,VocabularyRepository(db).list().size)
        model.restoreConfirmed("stale-confirmation");assertEquals(2,VocabularyRepository(db).list().size)
        model.restoreConfirmed(preview.previewId!!);model.restoreConfirmed(preview.previewId!!)
        settled{it.restored};assertEquals(1,VocabularyRepository(db).list().size)
    }
    @Test fun cancellingPreviewOrRecreatingViewModelCannotRestoreWithOldConfirmation()=runBlocking<Unit> {
        word("котка");val backup=repo.snapshot();word("куче");model.inspect(input(backup))
        val id=settled{!it.busy && it.summary!=null}.previewId!!
        model.cancelPreview();model.restoreConfirmed(id);assertEquals(2,VocabularyRepository(db).list().size)
        store.clear();model=BackupViewModel(repo,documents);store.put("new",model);model.restoreConfirmed(id)
        assertEquals(2,VocabularyRepository(db).list().size);assertNull(model.state.value.summary)
    }
    @Test fun corruptDocumentLeavesExistingDictionaryAndNoConfirmablePreview()=runBlocking<Unit> {
        word("котка");model.inspect(input("{broken".toByteArray()))
        settled{!it.busy && it.error==BackupError.INVALID_FORMAT};assertNull(model.state.value.previewId)
        model.restoreConfirmed("anything");assertEquals(1,VocabularyRepository(db).list().size)
    }
    @Test fun explicitCreationUriReceivesFullBackupNotCsv()=runBlocking<Unit> {
        word("добър ден");val uri=Uri.parse("content://backup-documents/full.json");val output=ByteArrayOutputStream()
        resolver.registerOutputStream(uri,output);model.export(uri);settled{!it.busy && it.saved}
        assertEquals(1,repo.prepare(output.toByteArray()).summary.words)
        assertTrue(output.toString("UTF-8").contains("flashcard_sessions"));assertTrue(output.toString("UTF-8").contains("trainer_preferences"))
    }
    @Test fun failedProviderWriteReportsFailureWithoutChangingLocalData()=runBlocking<Unit> {
        word("котка");val uri=Uri.parse("content://backup-documents/fail.json")
        resolver.registerOutputStream(uri,object:OutputStream(){override fun write(value:Int){throw IOException("provider failed")}})
        model.export(uri);settled{!it.busy && it.error==BackupError.WRITE_FAILED}
        assertEquals(1,VocabularyRepository(db).list().size)
    }
    @Test fun persistentTrainingPreferencesLoadIntoFreshViewModelsAndInvalidDraftIsNotSaved()=runBlocking<Unit> {
        val settings=TrainerPreferencesRepository(db);settings.flashcardSize(8);settings.flashcardDirection(FlashcardDirection.MIXED);settings.typingScope(StudyScope.DUE);settings.sentenceSize(6)
        val flash=FlashcardViewModel(FlashcardRepository(db),SavedStateHandle(),settings);store.put("flash",flash)
        val typing=TypingViewModel(com.danishbears.bulgariantrainer.typing.TypingRepository(db),SavedStateHandle(),settings);store.put("typing",typing)
        val sentence=SentencePracticeViewModel(com.danishbears.bulgariantrainer.sentences.SentencePracticeRepository(db),SavedStateHandle(),settings);store.put("sentence",sentence)
        withTimeout(10_000){flash.state.first{!it.loading && !it.busy};typing.state.first{!it.loading && !it.busy};sentence.state.first{!it.loading && !it.busy}}
        assertEquals("8",flash.size.value);assertEquals("MIXED",flash.direction.value);assertEquals("DUE",typing.scope.value);assertEquals("6",sentence.size.value)
        flash.setSize("not a number");assertEquals(8,settings.get().flashcardSize)
        flash.setSize("12");withTimeout(10_000){while(settings.get().flashcardSize!=12)delay(10)}
    }
    @Test fun explicitlySavedDraftTakesPrecedenceOverPersistentDefaultsDuringRecovery()=runBlocking<Unit> {
        val settings=TrainerPreferencesRepository(db);settings.flashcardSize(9)
        val flash=FlashcardViewModel(FlashcardRepository(db),SavedStateHandle(mapOf("size" to "4")),settings);store.put("flash",flash)
        withTimeout(10_000){flash.state.first{!it.loading && !it.busy}}
        assertEquals("4",flash.size.value);assertEquals(9,settings.get().flashcardSize)
    }
}
