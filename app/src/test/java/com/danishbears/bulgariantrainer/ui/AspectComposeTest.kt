package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.pronunciation.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

/** Real Compose trees and semantics on Robolectric; no real keyboard, TalkBack or voice claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val fake=FakeTtsAdapter()
    private lateinit var speech:BulgarianPronunciation
    private val store=ViewModelStore()
    private var db:TrainerDatabase?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup(){speech=BulgarianPronunciation(scope,{fake});speech.initialize();fake.initialized!!(true)}
    @After fun close(){store.clear();db?.close();speech.close();scope.cancel()}
    private fun entry(id:String):JsonObject=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.bufferedReader().use{it.readText()})
        .jsonObject.getValue("entries").jsonArray.single{it.jsonObject["id"]==JsonPrimitive(id)}.jsonObject
    private fun question(id:String="bgasp-057",reverse:Boolean=false):AspectQuestion {
        val e=entry(id)
        return AspectQuestion(1,1,if(reverse)AspectDirection.PERFECTIVE_TO_IMPERFECTIVE else AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,
            AspectPrompt(AspectContent.string(e,if(reverse)"perfective" else "imperfective"),AspectContent.string(e,if(reverse)"russianPerfective" else "russianImperfective"),AspectContent.string(e,if(reverse)"russianImperfective" else "russianPerfective")),null)
    }
    private fun show(font:Float=1f,content:@Composable () -> Unit) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalPronunciation provides speech,LocalDensity provides Density(density.density,font)) {
                MaterialTheme{Box(Modifier.width(320.dp).fillMaxHeight()){content()}}
            }
        }
    }
    private fun listen(text:String)=context.getString(R.string.pronunciation_listen_label,text)
    private fun card(q:State<AspectQuestion>,input:String="",onSubmit:()->Unit={}) {
        show{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
            PronunciationBoundary(q.value.id)
            AspectQuestionCard(9,q.value,input,true,{},onSubmit,{}, {})
        }}
    }
    private fun awaitUi(timeout:Long=10_000,predicate:()->Boolean) {
        compose.waitUntil(timeout) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            predicate()
        }
    }
    private fun lazyText(text:String):SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).performScrollTo()
    }
    @Test fun hiddenTargetAndExamplesAreAbsentFromUnmergedSemanticsAndAudioUntilSubmission() {
        val q=mutableStateOf(question());card(q)
        compose.onNodeWithText("купувам — покупать").assertIsDisplayed()
        compose.onNodeWithText("Напиши совершенный вид со значением «купить».").assertIsDisplayed()
        val before=compose.onRoot(useUnmergedTree=true).printToString()
        assertFalse(before.contains("купя"));assertFalse(before.contains("Обикновено купувам хляб"));assertTrue(fake.spoken.isEmpty())
        compose.onNodeWithContentDescription(listen("купувам")).performScrollTo().performClick()
        compose.runOnIdle{assertEquals(listOf("купувам"),fake.spoken.map{it.text})}
        compose.runOnIdle{q.value=q.value.copy(feedback=AspectFeedback(AspectOutcome.INCORRECT,"НЕВЕРНЫЙ ОТВЕТ",listOf("купя"),entry("bgasp-057")))}
        compose.onNodeWithText("Не совпало с ответами этой карточки").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ваш ответ: НЕВЕРНЫЙ ОТВЕТ").assertExists()
        compose.onNodeWithText("купя — купить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Утре ще купя хляб сутринта.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(listen("купя")).performScrollTo().performClick()
        compose.runOnIdle{assertEquals("купя",fake.spoken.last().text);assertTrue(fake.spoken.none{it.text=="НЕВЕРНЫЙ ОТВЕТ"})}
        compose.onNodeWithText("Дальше").performScrollTo().assertIsDisplayed()
        val stopped=fake.stopCount
        compose.runOnIdle{q.value=question("bgasp-028").copy(id=2)}
        compose.runOnIdle{assertTrue(fake.stopCount>stopped);assertFalse(speech.speak("aspect-9-1-accepted-0"))}
    }
    @Test fun reverseRevealShowsAllApprovedAlternativesWithoutAnEditableOrFictitiousSubmission() {
        val q=mutableStateOf(question("bgasp-043",true));card(q)
        compose.onNodeWithText("прочета — прочитать").assertIsDisplayed()
        compose.onNodeWithText("Напиши несовершенный вид со значением «читать».").assertIsDisplayed()
        assertFalse(compose.onRoot(useUnmergedTree=true).printToString().contains("прочитам"))
        compose.runOnIdle{q.value=q.value.copy(feedback=AspectFeedback(AspectOutcome.REVEALED,null,listOf("чета","прочитам"),entry("bgasp-043")))}
        compose.onAllNodesWithText("прочитам").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ap_revealed_no_submission)).assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription(listen("прочитам")).performScrollTo().performClick()
        compose.runOnIdle{assertEquals("прочитам",fake.spoken.last().text)}
    }
    @Test fun contextualMeaningIsExplicitAndTargetIsStillHidden() {
        card(mutableStateOf(question("bgasp-028")))
        compose.onNodeWithText("Напиши совершенный вид со значением «поговорить».").assertIsDisplayed()
        val tree=compose.onRoot(useUnmergedTree=true).printToString()
        assertFalse(tree.contains("поговоря"));assertFalse(tree.contains("След работа"));assertFalse(tree.contains("Не эквивалент"))
    }
    @Test fun imeSubmissionRemainsAvailableWithoutBulgarianVoicesAndBusyDisablesFurtherActions() {
        fake.available=emptyList();fake.availability=LanguageAvailability.UNSUPPORTED;speech.initialize();fake.initialized!!(true)
        var input by mutableStateOf("");var enabled by mutableStateOf(true);var submitted=0
        show{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
            AspectQuestionCard(1,question(),input,enabled,{input=it},{submitted++;enabled=false},{},{})
        }}
        compose.onNodeWithContentDescription(listen("купувам")).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("купя")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle{assertEquals(1,submitted);assertEquals("купя",input);assertTrue(fake.spoken.isEmpty())}
        compose.onNodeWithText(context.getString(R.string.typing_submit)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.flash_reveal)).assertIsNotEnabled()
    }
    @Test fun twoTimesFontAndNarrowLayoutKeepCheckboxLabelsAndPlaybackInsideWidthAndScrollable() {
        var checked by mutableStateOf(false)
        show(2f){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
            AspectCheckbox("Стадия 3 — ограниченное действие",checked){checked=it}
            AspectQuestionCard(1,question("bgasp-028"),"",true,{},{},{},{})
        }}
        compose.onNodeWithText("Стадия 3 — ограниченное действие").performClick().assertIsOn()
        compose.onNodeWithContentDescription(listen("говоря")).performScrollTo().assertIsDisplayed()
        val width=compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        for(node in compose.onAllNodes(hasClickAction()).fetchSemanticsNodes())assertTrue("click control overflows 320dp",node.boundsInRoot.right<=width+1)
        compose.onNodeWithText(context.getString(R.string.flash_reveal)).performScrollTo().assertIsDisplayed()
    }
    @Test fun managementSearchArchiveAndRestoreUseRealRepositoryWithoutExtraNavigationItems() {
        val database=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build();db=database
        val repo=AspectRepository(database)
        runBlocking{repo.create("fixture",entry("bgasp-057"))}
        val vm=AspectViewModel(repo,AspectDocuments(context),SavedStateHandle());store.put("management",vm)
        var back=0;show{AspectScreen(vm,{}, {back++},{})}
        awaitUi(){vm.state.value.page?.total==1L&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_search)).performTextInput("купить")
        compose.onNodeWithText(context.getString(R.string.aspect_search)).performImeAction()
        awaitUi(){!vm.state.value.busy}
        compose.runOnIdle{assertEquals("Search draft", "купить",vm.filter.value[1]);assertEquals("Search state: ${vm.state.value}",1L,vm.state.value.page?.total)}
        lazyText("купувам → купя").performClick()
        awaitUi(){vm.state.value.viewed!=null&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_archive)).performClick()
        awaitUi(){vm.state.value.page?.total==0L&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_no_matches)).assertIsDisplayed()
        lazyText(context.getString(R.string.aspect_more_filters)).performClick()
        lazyText(context.getString(R.string.aspect_archived_only)).performClick()
        lazyText(context.getString(R.string.aspect_apply)).performClick()
        awaitUi(){vm.state.value.page?.total==1L&&!vm.state.value.busy}
        lazyText("купувам → купя").performClick()
        awaitUi(){vm.state.value.viewed!=null&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_restore)).performClick()
        awaitUi(){vm.state.value.page?.total==0L&&!vm.state.value.busy}
        val activity=compose.activity
        compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
        compose.runOnIdle{assertEquals(1,back)}
        assertEquals(1L,runBlocking{repo.list().total})
    }
    @Test fun jsonPreviewCannotWriteUntilNativeConfirmationDialogIsAccepted() {
        val database=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build();db=database
        val repo=AspectRepository(database);val vm=AspectViewModel(repo,AspectDocuments(context),SavedStateHandle());store.put("preview",vm)
        show{AspectScreen(vm,{}, {},{})}
        awaitUi(){vm.state.value.page!=null&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_sample)).performClick()
        awaitUi(){vm.state.value.preview!=null&&!vm.state.value.busy}
        assertEquals(0L,runBlocking{repo.list().total})
        lazyText(vm.state.value.preview!!.metadata.getValue("editorialStatus").jsonPrimitive.content).assertIsDisplayed()
        lazyText(context.getString(R.string.aspect_confirm)).performClick()
        compose.onNodeWithText(context.getString(R.string.aspect_confirm_hint)).assertIsDisplayed()
        assertEquals(0L,runBlocking{repo.list().total})
        compose.onNodeWithText(context.getString(R.string.aspect_save_selected)).performClick()
        awaitUi(20_000){vm.state.value.summary!=null&&!vm.state.value.busy}
        assertEquals(100L,runBlocking{repo.list().total})
    }
    @Test fun helpUsesNativeScrollableTextAndLabelsStagesAsStudyOrder() {
        var back=0;show(1.8f){AspectHelpScreen{back++}}
        compose.onNodeWithText(context.getString(R.string.aspect_help_aspect_title)).assertIsDisplayed()
        lazyText(context.getString(R.string.aspect_help_stages)).assertIsDisplayed()
        lazyText(context.getString(R.string.aspect_help_offline)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.runOnIdle{assertEquals(1,back);assertTrue(fake.spoken.isEmpty())}
    }
    @Test fun nativeEditorAcceptsMultipleAnswersAndSystemBackAsksBeforeDiscardingADraft() {
        val database=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build();db=database
        val repo=AspectRepository(database);val vm=AspectViewModel(repo,AspectDocuments(context),SavedStateHandle());store.put("editor",vm)
        show{AspectScreen(vm,{}, {},{})}
        awaitUi(){vm.state.value.page!=null&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_new)).performClick()
        for((label,value)in listOf(R.string.aspect_imperfective to "чета",R.string.aspect_perfective to "прочета",
            R.string.aspect_ru_imperfective to "читать",R.string.aspect_ru_perfective to "прочитать",
            R.string.aspect_accepted_imperfective to "чета\nпрочитам",R.string.aspect_accepted_perfective to "прочета",R.string.aspect_topic to "Учёба")) {
            lazyText(context.getString(label)).performTextInput(value)
        }
        lazyText(context.getString(R.string.aspect_save)).performClick()
        awaitUi(){!vm.state.value.editing&&!vm.state.value.busy}
        val record=runBlocking{repo.list().rows.single()}
        assertEquals(listOf("чета","прочитам"),AspectContent.answers(record.entry,"acceptedImperfective"))
        lazyText("чета → прочета").performClick()
        awaitUi(){vm.state.value.viewed!=null&&!vm.state.value.busy}
        lazyText(context.getString(R.string.aspect_edit)).performClick()
        lazyText(context.getString(R.string.aspect_note)).performTextInput("Мой черновик")
        val activity=compose.activity
        compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText(context.getString(R.string.aspect_discard)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.flash_continue)).performClick()
        compose.onNodeWithText("Мой черновик").assertExists()
        compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText(context.getString(R.string.cancel_edit)).performClick()
        assertEquals(record,runBlocking{repo.get(record.id)})
    }
    @Test fun legacyBackupCompatibilityWarningsNameWhatWillBeReplaced() {
        var version by mutableStateOf(1)
        show{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){BackupAspectCompatibility(version)}}
        compose.onNodeWithText(context.getString(R.string.backup_legacy_no_aspects)).assertIsDisplayed()
        compose.runOnIdle{version=2}
        compose.onNodeWithText(context.getString(R.string.backup_legacy_no_aspect_practice)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.backup_legacy_no_aspects)).assertDoesNotExist()
        compose.runOnIdle{version=3}
        compose.onNodeWithText(context.getString(R.string.backup_legacy_no_aspect_practice)).assertDoesNotExist()
    }
}
