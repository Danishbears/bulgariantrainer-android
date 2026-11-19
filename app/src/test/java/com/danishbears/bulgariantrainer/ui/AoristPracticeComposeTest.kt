package com.danishbears.bulgariantrainer.ui

import android.app.Application
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
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.pronunciation.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Native Compose semantics with fake audio; not physical keyboard/TalkBack/TTS verification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristPracticeComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val fake=FakeTtsAdapter();private lateinit var speech:BulgarianPronunciation
    @Before fun setup(){speech=BulgarianPronunciation(scope,{fake});speech.initialize();fake.initialized!!(true)}
    @After fun close(){speech.close();scope.cancel()}
    private fun entry()=AoristContent.strict(javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.bufferedReader().use{it.readText()}).jsonObject.getValue("entries").jsonArray.single{it.jsonObject["lemma"]==JsonPrimitive("чета")}.jsonObject
    private fun question(type:AoristExercise,person:String,options:List<AoristOption> = emptyList())=AoristQuestion(1,21,123,1,type,AoristPrompt("чета","читать",person),options,"",null,0,null)
    private fun show(font:Float=1f,content:@Composable ()->Unit){compose.setContent{
        val density=LocalDensity.current
        MaterialTheme{CompositionLocalProvider(LocalPronunciation provides speech,LocalDensity provides Density(density.density,font)){
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())){content()}
        }}
    }}
    private fun listen(text:String)=context.getString(R.string.pronunciation_listen_label,text)
    @Test fun typedQuestionNamesExactVerbAndPersonAndHidesTargetFromSemanticsAndAudioUntilFeedback() {
        val q=mutableStateOf(question(AoristExercise.TYPED,"1pl"));var next=0
        show(1.4f){PronunciationBoundary(q.value.id);AoristQuestionCard(7,q.value,"",null,true,{}, {}, {}, {}, {next++})}
        compose.onNodeWithText("чета — читать").assertExists()
        compose.onNodeWithText(context.getString(R.string.aop_instruction_typed,context.getString(R.string.aorist_1pl))).assertExists()
        val before=compose.onRoot(useUnmergedTree=true).printToString();assertFalse(before.contains("четохме"));assertFalse(before.contains(AoristContent.text(entry(),"note")))
        compose.onNodeWithContentDescription(listen("четохме")).assertDoesNotExist();assertTrue(fake.spoken.isEmpty())
        compose.onNodeWithContentDescription(listen("чета")).performScrollTo().performClick()
        compose.runOnIdle{assertEquals("чета",fake.spoken.single().text);q.value=q.value.copy(feedback=AoristFeedback(AoristOutcome.INCORRECT,"прочетохме",null,listOf("четохме"),entry()))}
        compose.onNodeWithText(context.getString(R.string.ap_mismatch)).assertExists()
        compose.onNodeWithContentDescription(listen("прочетохме")).assertDoesNotExist()
        compose.onNodeWithContentDescription(listen("четохме")).performScrollTo().performClick()
        compose.runOnIdle{assertEquals("четохме",fake.spoken.last().text);assertEquals(0,next)}
        compose.onNodeWithText(context.getString(R.string.ap_next)).performScrollTo().performClick();compose.runOnIdle{assertEquals(1,next)}
    }
    @Test fun choiceRequiresExplicitSubmitAndRequestsTheFormRatherThanInferringPersonFromHomographs() {
        val q=question(AoristExercise.CHOICE,"2sg",listOf(AoristOption("o1","чете"),AoristOption("o2","четох"),AoristOption("o3","четохме"),AoristOption("o4","четохте")))
        var selected by mutableStateOf<String?>(null);var submitted:String?=null
        show{AoristQuestionCard(7,q,"",selected,true,{}, {selected=it}, {submitted=selected}, {}, {})}
        compose.onNodeWithText(context.getString(R.string.aop_instruction_choice,context.getString(R.string.aorist_2sg))).assertExists()
        compose.onNodeWithText(context.getString(R.string.typing_submit)).assertIsNotEnabled()
        compose.onNodeWithText("четох").performScrollTo().performClick()
        compose.runOnIdle{assertEquals("o2",selected);assertNull(submitted)}
        compose.onNodeWithContentDescription(listen("чете")).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.typing_submit)).performScrollTo().performClick()
        compose.runOnIdle{assertEquals("o2",submitted)}
    }
}
