package com.danishbears.bulgariantrainer.aorist

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.ui.*
import java.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
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
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristViewModelTest {
    private lateinit var db:TrainerDatabase;private lateinit var repo:AoristRepository;private lateinit var docs:AoristDocuments
    private lateinit var resolver:ShadowContentResolver
    private val store=ViewModelStore();private var count=0
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher());val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,TrainerDatabase::class.java).build();repo=AoristRepository(db);docs=AoristDocuments(c);resolver=shadowOf(c.contentResolver)
        ShadowContentResolver.registerProviderInternal("aorist-documents",Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())}
    @After fun close(){store.clear();db.close();Dispatchers.resetMain()}
    private fun vm(saved:SavedStateHandle=SavedStateHandle())=AoristViewModel(repo,docs,saved).also{store.put("aorist"+count++,it)}
    private suspend fun ready(model:AoristViewModel,predicate:(AoristUiState)->Boolean={true})=withTimeout(20_000){model.state.first{!it.busy&&it.page!=null&&predicate(it)}}
    private fun input(bytes:ByteArray)=Uri.parse("content://aorist-documents/test.json").also{resolver.registerInputStream(it,ByteArrayInputStream(bytes))}
    @Test fun explicitStarterActionOnlyPreviewsThenConfirmationAndSafExportWork()=runBlocking<Unit> {
        val model=vm();ready(model);assertTrue(repo.datasets().isEmpty())
        model.sample();val preview=ready(model){it.preview!=null};assertEquals(40,preview.selected.size);assertEquals(0L,repo.list().total)
        model.confirm();model.confirm();ready(model){it.summary!=null};assertEquals(40L,repo.list().total)
        val output=ByteArrayOutputStream();val uri=Uri.parse("content://aorist-documents/export.json");resolver.registerOutputStream(uri,output)
        model.export(uri,"bg-aorist-core-40",false);assertNull(ready(model){it.notice!=null}.error)
        assertEquals(40,AoristContent.parse(output.toByteArray()).entries.size)
    }
    @Test fun changedRowsAreNeverPreselectedAndOnlyExplicitSelectionMayUpdateThem()=runBlocking<Unit> {
        val model=vm();ready(model);model.sample();ready(model){it.preview!=null};model.confirm();ready(model){it.summary!=null}
        val first=repo.list().rows.first();val changed=JsonObject(first.entry+("note" to JsonPrimitive("edited")))
        val data=AoristContent.document(AoristContent.manualMetadata("bg-aorist-core-40"),listOf(changed)).toString().toByteArray()
        model.inspect(input(data));val state=ready(model){it.preview!=null}
        assertEquals(AoristChangeKind.UPDATE,state.preview!!.changes.single().kind);assertTrue(state.selected.isEmpty())
        assertEquals(first.entry,repo.get(first.id).entry)
        model.confirm();ready(model){it.error!=null};assertEquals(first.entry,repo.get(first.id).entry)
        model.select(AoristContent.text(changed,"id"),true);model.confirm();ready(model){it.preview==null}
        assertEquals(changed,repo.get(first.id).entry)
    }
    @Test fun recreationRetainsManualDraftUuidButInvalidatesUnsavedImportPreview()=runBlocking<Unit> {
        val saved=SavedStateHandle();var model=vm(saved);ready(model);model.beginEdit();model.draftField("lemma","чета")
        val id=AoristContent.strict(model.draft.value).jsonObject["id"];store.clear();model=vm(saved);ready(model)
        assertTrue(model.state.value.editing);assertEquals(id,AoristContent.strict(model.draft.value).jsonObject["id"])
        model.cancelEdit();model.sample();ready(model){it.preview!=null};store.clear();model=vm(saved);ready(model)
        assertTrue(model.state.value.previewLost);assertNull(model.state.value.preview);model.confirm();ready(model);assertEquals(0L,repo.list().total)
    }
    @Test fun manualEditorRequiresEveryExplicitSlotAndPreservesAcceptedAlternatives()=runBlocking<Unit> {
        val model=vm();ready(model);model.beginEdit();model.save();assertTrue(model.state.value.fieldErrors.containsKey("forms.1sg[0]"))
        model.draftField("lemma","чета");model.draftField("russian","читать")
        for((person,text)in listOf("1sg" to "четох\nсинтетичен тестов вариант","2sg" to "чете","3sg" to "чете","1pl" to "четохме","2pl" to "четохте","3pl" to "четоха"))model.draftField(person,text)
        model.save();ready(model){!it.editing};val record=repo.list().rows.single()
        assertEquals(2,AoristContent.answers(record.entry,"1sg").size);assertEquals("",AoristContent.text(record.entry.getValue("source").jsonObject,"url"))
        assertEquals(6,db.aoristDao().forms(record.id).size)
    }
}
