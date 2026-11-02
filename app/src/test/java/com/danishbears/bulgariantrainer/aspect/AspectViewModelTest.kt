package com.danishbears.bulgariantrainer.aspect

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectViewModelTest{
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectRepository
    private lateinit var documents:AspectDocuments
    private lateinit var resolver:ShadowContentResolver
    private val store=ViewModelStore()
    private var count=0
    @Before fun setup(){
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build();repo=AspectRepository(db)
        documents=AspectDocuments(context);resolver=shadowOf(context.contentResolver)
        ShadowContentResolver.registerProviderInternal("aspect-documents",Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())
    }
    @After fun close(){store.clear();db.close();Dispatchers.resetMain()}
    private fun vm(saved:SavedStateHandle=SavedStateHandle())=AspectViewModel(repo,documents,saved).also{store.put("aspect"+count++,it)}
    private suspend fun ready(vm:AspectViewModel,predicate:(AspectUiState)->Boolean={true})=withTimeout(20_000){vm.state.first{!it.busy&&it.page!=null&&predicate(it)}}
    private fun input(bytes:ByteArray)=Uri.parse("content://aspect-documents/test.json").also{resolver.registerInputStream(it,ByteArrayInputStream(bytes))}
    private fun bytes()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.use{it.readBytes()}
    @Test fun systemDocumentPreviewHasNoWritesAndDuplicateConfirmDoesNotDuplicateContent()=runBlocking<Unit>{
        val vm=vm();ready(vm);vm.inspect(input(bytes()),"fixture")
        val state=ready(vm){it.preview!=null};assertEquals(100,state.selected.size);assertEquals(0L,repo.list().total)
        vm.confirm();vm.confirm();ready(vm){it.summary!=null}
        assertEquals(100L,repo.list().total)
        val output=ByteArrayOutputStream();val uri=Uri.parse("content://aspect-documents/export.json");resolver.registerOutputStream(uri,output)
        vm.export(uri,"fixture",false);val exported=ready(vm){it.notice?.contains("сохранён")==true}
        assertNull(exported.error)
        assertEquals(100,AspectContent.parse(output.toByteArray()).entries.size)
    }
    @Test fun unsavedEditorIdentifierSurvivesViewModelRecreationAndCanBeCancelled()=runBlocking<Unit>{
        val saved=SavedStateHandle();var vm=vm(saved);ready(vm);vm.beginEdit()
        vm.draftField("imperfective","пиша")
        val before=Json.parseToJsonElement(vm.draft.value).jsonObject.getValue("id")
        store.clear();vm=vm(saved);ready(vm)
        assertTrue(vm.state.value.editing);assertEquals(before,Json.parseToJsonElement(vm.draft.value).jsonObject.getValue("id"))
        assertEquals("пиша",Json.parseToJsonElement(vm.draft.value).jsonObject.getValue("imperfective").jsonPrimitive.content)
        vm.cancelEdit();assertEquals(0L,repo.list().total)
    }
    @Test fun lostPreviewAndInvalidWholeFileCannotBecomeAHiddenImport()=runBlocking<Unit>{
        val saved=SavedStateHandle();var vm=vm(saved);ready(vm);vm.inspect(input(bytes()),"fixture");ready(vm){it.preview!=null}
        store.clear();vm=vm(saved);ready(vm);assertTrue(vm.state.value.previewLost);assertNull(vm.state.value.preview)
        vm.confirm();ready(vm);assertEquals(0L,repo.list().total)
        vm.inspect(input("{\"format\":\"unsupported\"}".toByteArray()),"fixture")
        assertTrue(ready(vm){it.preview!=null}.preview!!.issues.isNotEmpty())
        vm.confirm();ready(vm){it.error!=null};assertEquals(0L,repo.list().total)
    }
    @Test fun editorShowsFieldValidationAndKeepsMultipleAnswersAndOptionalExamples()=runBlocking<Unit>{
        val vm=vm();ready(vm);vm.beginEdit();vm.save()
        assertTrue(vm.state.value.fieldErrors.containsKey("imperfective"));assertEquals(0L,repo.list().total)
        vm.draftField("imperfective","чета");assertFalse(vm.state.value.fieldErrors.containsKey("imperfective"))
        for((key,value)in mapOf("perfective" to "прочета","russianImperfective" to "читать","russianPerfective" to "прочитать", "topic" to "Книги",
            "acceptedImperfective" to "чета\nпрочитам","acceptedPerfective" to "прочета", "example_imperfective_bg" to "Чета книга.","example_imperfective_ru" to "Читаю книгу.",
            "example_perfective_bg" to "Ще прочета книгата.","example_perfective_ru" to "Прочитаю книгу."))vm.draftField(key,value)
        vm.save();ready(vm){!it.editing};val pair=repo.list().rows.single()
        assertEquals(listOf("чета","прочитам"),AspectContent.answers(pair.entry,"acceptedImperfective"));assertTrue("examples" in pair.entry)
    }
    @Test fun viewingPairAndItsDraftCanReturnAfterSavedStateReconstruction()=runBlocking<Unit>{
        val p=repo.preview("fixture","source.json",bytes());repo.confirm(p,setOf(p.changes.first().externalId))
        val record=repo.list().rows.single();val handle=SavedStateHandle();var vm=vm(handle);ready(vm);vm.view(record.id);ready(vm){it.viewed!=null}
        store.clear();vm=vm(SavedStateHandle(handle.keys().associateWith{handle.get<Any?>(it)}));assertEquals(record,ready(vm){it.viewed!=null}.viewed)
        vm.beginEdit(record);vm.draftField("acceptedImperfective","не сохранено");vm.cancelEdit();assertEquals(record,repo.get(record.id))
    }
}
