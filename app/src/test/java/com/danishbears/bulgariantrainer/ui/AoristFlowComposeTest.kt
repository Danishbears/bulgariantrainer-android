package com.danishbears.bulgariantrainer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowContentResolver
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.danishbears.bulgariantrainer.BulgarianTrainerApplication
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.pronunciation.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

/** Real native navigation, ViewModels, assets and Room. No emulator/SAF-provider/airplane-mode claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=BulgarianTrainerApplication::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristFlowComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as BulgarianTrainerApplication
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val fake=FakeTtsAdapter();private lateinit var speech:BulgarianPronunciation
    @Before fun setup(){speech=BulgarianPronunciation(scope,{fake});speech.initialize();fake.initialized!!(true)}
    @After fun close(){speech.close();scope.cancel()}
    private fun text(id:Int)=app.getString(id)
    private fun show(font:Float=1f){compose.setContent{
        val density=LocalDensity.current
        MaterialTheme{CompositionLocalProvider(LocalPronunciation provides speech,LocalDensity provides Density(density.density,font)){
            TrainerApp(app.vocabulary,app.transfer,app.flashcards,app.typing,app.sentences,app.sentencePractice,app.statistics)
        }}
    }}
    private fun awaitText(value:String,enabled:Boolean=false){
        val matcher=if(enabled)hasText(value) and isEnabled() else hasText(value)
        compose.waitUntil(20_000){
            shadowOf(Looper.getMainLooper()).idle();compose.waitForIdle()
            if(compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
                try{compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)}catch(_:AssertionError){ /* Async/lazy destination not ready yet. */ }
            }
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun scroll(value:String){compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value))}
    private fun click(id:Int){val value=text(id);awaitText(value,true);compose.onNodeWithText(value).performScrollTo().performClick()}
    private fun back(){compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}}
    private fun open(){click(R.string.aorist_title);awaitText(text(R.string.aorist_search))}
    private fun replace(id:Int,value:String){scroll(text(id));compose.onNodeWithText(text(id)).performScrollTo().performTextReplacement(value)}
    private fun current()=runBlocking{app.aoristPractice.restore()!!}

    @Test fun explicitStarterImportTypedDraftBackResumeFeedbackResultsThenChoiceAndHelp() {
        show();open();click(R.string.aorist_sample)
        awaitText(app.getString(R.string.aorist_preview_counts,40,0,0))
        assertTrue(runBlocking{app.aorist.datasets().isEmpty()})
        click(R.string.aorist_confirm)
        compose.onNodeWithText(text(R.string.aorist_save_selected)).performClick()
        awaitText(app.getString(R.string.aorist_count,40))
        assertEquals(40L,runBlocking{app.aorist.list().total})
        click(R.string.aop_title);awaitText(text(R.string.aop_settings_hint))
        replace(R.string.ap_size,"1");click(R.string.flash_start);awaitText(text(R.string.ap_answer))
        val first=current();assertEquals(AoristExercise.TYPED,first.settings.exercise)
        assertEquals(listOf("1sg"),first.settings.persons)
        compose.onNodeWithText(text(R.string.ap_answer)).performScrollTo().performTextInput("неверный черновик")
        back();awaitText(text(R.string.aorist_search));click(R.string.aop_title)
        awaitText("неверный черновик")
        assertEquals(first.question!!.id,current().question!!.id)
        assertEquals("неверный черновик",current().question!!.draft)
        click(R.string.typing_submit);awaitText(text(R.string.ap_mismatch))
        assertEquals(1,current().incorrect)
        click(R.string.ap_next);awaitText(text(R.string.aop_summary))
        scroll(text(R.string.aop_mistakes));compose.onNodeWithText(text(R.string.aop_mistakes)).assertExists()
        assertEquals(AoristSessionStatus.COMPLETED,current().status)
        click(R.string.flash_new_session);awaitText(text(R.string.aop_settings_hint))
        click(R.string.aop_choice);click(R.string.flash_start)
        awaitText(text(R.string.aop_instruction_choice))
        val choice=current();val q=choice.question!!
        val content=runBlocking{app.aorist.get(q.verbId).entry}
        val accepted=AoristContent.answers(content,q.prompt.person).map(AoristContent::key)
        val correct=q.options.single{AoristContent.key(it.text) in accepted}
        scroll(correct.text);compose.onNodeWithText(correct.text).performScrollTo().performClick()
        assertEquals(0,current().answered)
        click(R.string.typing_submit);awaitText(text(R.string.typing_correct))
        click(R.string.ap_next);awaitText(text(R.string.aop_summary))
        assertEquals(1,current().correct)
        click(R.string.aorist_help_title)
        awaitText(text(R.string.aorist_help_tense_title))
        scroll(text(R.string.aorist_help_reflexive_title))
        compose.onNodeWithText(text(R.string.aorist_help_reflexive)).assertExists()
        back();awaitText(text(R.string.aop_summary))
        assertEquals(choice.id,current().id);assertEquals(1,current().answered)
        assertTrue(fake.spoken.isEmpty())
    }

    @Test fun managementSearchSixPersonTableEditArchiveRestoreAndHelpAtLargeFont() {
        runBlocking{val p=app.aorist.preview("starter.json",app.assets.open("bulgarian_aorist_40.json").use{it.readBytes()});app.aorist.confirm(p,p.changes.map{it.externalId}.toSet())}
        val original=runBlocking{app.aorist.list(AoristFilter(query="облека")).rows.single()}
        show(1.5f);open();replace(R.string.aorist_search,"облека");click(R.string.aorist_apply)
        awaitText(app.getString(R.string.aorist_count,1))
        val label="облека се — одеться";scroll(label);compose.onNodeWithText(label).performClick()
        awaitText(text(R.string.aorist_table))
        for((person,labelId) in AoristContent.persons.zip(listOf(R.string.aorist_1sg,R.string.aorist_2sg,R.string.aorist_3sg,R.string.aorist_1pl,R.string.aorist_2pl,R.string.aorist_3pl))) {
            val form=AoristContent.answers(original.entry,person).single()
            compose.onNode(hasText(text(labelId)) and hasText(form)).performScrollTo().assertExists()
        }
        click(R.string.aorist_edit);replace(R.string.aorist_note,"Личная заметка")
        replace(R.string.aorist_1sg,"облякох се\nсинтетичен тестов вариант")
        click(R.string.aorist_save);awaitText(app.getString(R.string.aorist_count,1))
        val edited=runBlocking{app.aorist.get(original.id)}
        assertEquals(2,AoristContent.answers(edited.entry,"1sg").size)
        assertEquals("Личная заметка",AoristContent.text(edited.entry,"note"))
        scroll(label);compose.onNodeWithText(label).performClick();awaitText(text(R.string.aorist_table))
        click(R.string.aorist_archive);awaitText(app.getString(R.string.aorist_count,0))
        click(R.string.aorist_filters);click(R.string.aorist_archived_only);click(R.string.aorist_apply)
        awaitText(app.getString(R.string.aorist_count,1))
        scroll(label);compose.onNodeWithText(label).performClick();awaitText(text(R.string.aorist_table))
        click(R.string.aorist_restore);awaitText(app.getString(R.string.aorist_count,0))
        assertFalse(runBlocking{app.aorist.get(original.id).archived})
        click(R.string.aorist_help_title);awaitText(text(R.string.aorist_help_tense_title))
        back();awaitText(text(R.string.aorist_search))
        back();awaitText(text(R.string.home));compose.onNodeWithText(text(R.string.dictionary)).assertExists()
    }
    @Test fun documentPickerMalformedPreviewThenValidFileAndContentOnlyCreateDocumentExport() {
        ShadowContentResolver.registerProviderInternal("aorist-ui",Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())
        val resolver=shadowOf(app.contentResolver)
        val uri=Uri.parse("content://aorist-ui/import.json")
        resolver.registerInputStream(uri,ByteArrayInputStream("{}".toByteArray()))
        show();open();click(R.string.aorist_import)
        compose.runOnIdle{
            val request=shadowOf(compose.activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.intent.action)
            assertTrue(request.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/json"))
            shadowOf(compose.activity).receiveResult(request.intent,Activity.RESULT_OK,Intent().setData(uri))
        }
        awaitText(app.getString(R.string.aorist_preview_counts,0,0,0))
        compose.onNodeWithText(text(R.string.aorist_confirm)).assertIsNotEnabled()
        assertTrue(runBlocking{app.aorist.datasets().isEmpty()})
        back();awaitText(text(R.string.aorist_search))
        resolver.registerInputStream(uri,ByteArrayInputStream(app.assets.open("bulgarian_aorist_40.json").use{it.readBytes()}))
        click(R.string.aorist_import)
        compose.runOnIdle{val request=shadowOf(compose.activity).nextStartedActivityForResult
            shadowOf(compose.activity).receiveResult(request.intent,Activity.RESULT_OK,Intent().setData(uri))}
        awaitText(app.getString(R.string.aorist_preview_counts,40,0,0));click(R.string.aorist_confirm)
        compose.onNodeWithText(text(R.string.aorist_save_selected)).performClick();awaitText(app.getString(R.string.aorist_count,40))
        val output=ByteArrayOutputStream();val target=Uri.parse("content://aorist-ui/export.json")
        resolver.registerOutputStream(target,output)
        click(R.string.aorist_export)
        compose.runOnIdle{val request=shadowOf(compose.activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT,request.intent.action)
            assertEquals("application/json",request.intent.type)
            shadowOf(compose.activity).receiveResult(request.intent,Activity.RESULT_OK,Intent().setData(target))}
        awaitText("Учебный JSON аориста сохранён. Это не резервная копия прогресса.")
        val exported=AoristContent.parse(output.toByteArray())
        assertTrue(exported.issues.isEmpty());assertEquals(40,exported.entries.size)
        assertFalse(output.toString("UTF-8").contains("aorist_sessions"))
    }
}
