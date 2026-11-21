package com.danishbears.bulgariantrainer.ui

import android.os.Looper
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
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=BulgarianTrainerApplication::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristStatisticsComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as BulgarianTrainerApplication
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val fake=FakeTtsAdapter();private lateinit var speech:BulgarianPronunciation
    @Before fun setup(){speech=BulgarianPronunciation(scope,{fake});speech.initialize();fake.initialized!!(true)}
    @After fun close(){speech.close();scope.cancel()}
    private fun text(id:Int)=app.getString(id)
    private fun awaitText(value:String){compose.waitUntil(20_000){
        shadowOf(Looper.getMainLooper()).idle();compose.waitForIdle()
        if(compose.onAllNodesWithText(value).fetchSemanticsNodes().isEmpty())try{compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value))}catch(_:AssertionError){}
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }}
    private fun click(id:Int){awaitText(text(id));compose.onNodeWithText(text(id)).performScrollTo().performClick()}
    private fun back(){compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}}
    private fun show(){compose.setContent{
        val density=LocalDensity.current
        MaterialTheme{CompositionLocalProvider(LocalPronunciation provides speech,LocalDensity provides Density(density.density,1.5f)){
            TrainerApp(app.vocabulary,app.transfer,app.flashcards,app.typing,app.sentences,app.sentencePractice,app.statistics)
        }}
    };click(R.string.aorist_title);click(R.string.aos_title);awaitText(text(R.string.aos_all))}
    @Test fun emptyStatisticsRemainSeparateAndBackRestoresExistingNavigationAtLargeFont(){
        show();awaitText(text(R.string.aos_empty));compose.onNodeWithText(text(R.string.home)).assertDoesNotExist()
        click(R.string.aos_by_exercise);awaitText(text(R.string.aop_choice))
        back();awaitText(text(R.string.aorist_search));back();awaitText(text(R.string.home))
        assertTrue(runBlocking{app.aorist.datasets().isEmpty()})
    }
    @Test fun realTypedAndChoiceOutcomesAppearInEveryBreakdownAndViewingNeverAddsAttempts(){
        runBlocking{
            val p=app.aorist.preview("starter.json",app.assets.open("bulgarian_aorist_40.json").use{it.readBytes()});app.aorist.confirm(p,p.changes.map{it.externalId}.toSet())
            val typed=app.aoristPractice.start(UUID.randomUUID().toString(),AoristSettings(size=1));val q=typed.question!!
            app.aoristPractice.submitTyped(typed.id,q.id,AoristContent.answers(app.aorist.get(q.verbId).entry,"1sg").first())
            val choice=app.aoristPractice.start(UUID.randomUUID().toString(),AoristSettings(size=1,exercise=AoristExercise.CHOICE));val c=choice.question!!
            val accepted=AoristContent.answers(app.aorist.get(c.verbId).entry,"1sg").map(AoristContent::key)
            app.aoristPractice.submitChoice(choice.id,c.id,c.options.first{AoristContent.key(it.text) !in accepted}.id)
        }
        show();val person=app.getString(R.string.aos_counts,2,1,1,0);awaitText(person)
        compose.onNodeWithText(person).assertExists()
        click(R.string.aos_by_exercise);awaitText(app.getString(R.string.aos_counts,1,1,0,0));awaitText(app.getString(R.string.aos_counts,1,0,1,0))
        click(R.string.aos_by_both);awaitText(text(R.string.aorist_1sg)+" · "+text(R.string.aop_typed))
        click(R.string.flash_refresh);awaitText(text(R.string.aos_all))
        assertEquals(AoristStatisticsCounts(2,1,1,0,0),runBlocking{app.aoristStatistics.overview().counts})
        back();click(R.string.aop_title);awaitText(text(R.string.ap_mismatch))
        click(R.string.aos_title);awaitText(text(R.string.aos_all))
        assertEquals(2L,runBlocking{app.aoristStatistics.overview().counts.answered})
    }
}
