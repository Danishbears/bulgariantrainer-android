package com.danishbears.bulgariantrainer.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.danishbears.bulgariantrainer.BulgarianTrainerApplication
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.pronunciation.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=BulgarianTrainerApplication::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectNavigationComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun dashboardSectionHelpAndSystemBackKeepExistingBottomNavigationOutOfTheSection() {
        val activity=compose.activity
        val app=activity.application as BulgarianTrainerApplication
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(scope,{fake})
        speech.initialize();fake.initialized!!(true)
        try {
            compose.setContent{MaterialTheme{CompositionLocalProvider(LocalPronunciation provides speech){
                TrainerApp(app.vocabulary,app.transfer,app.flashcards,app.typing,app.sentences,app.sentencePractice,app.statistics)
            }}}
            compose.onNodeWithText(app.getString(R.string.home)).assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.aspect_title)))
            compose.onNodeWithText(app.getString(R.string.aspect_title)).performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.aspect_search)).assertExists()
            compose.onNodeWithText(app.getString(R.string.home)).assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.dictionary)).assertDoesNotExist()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.aspect_statistics_title)))
            compose.onNodeWithText(app.getString(R.string.aspect_statistics_title)).performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.aspect_statistics_scope)).assertExists()
            compose.onNodeWithText(app.getString(R.string.home)).assertDoesNotExist()
            compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithContentDescription(app.getString(R.string.aspect_help_title)).performClick()
            compose.onNodeWithText(app.getString(R.string.aspect_help_aspect_title)).assertIsDisplayed()
            compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithText(app.getString(R.string.aspect_search)).assertExists()
            compose.runOnIdle{activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithText(app.getString(R.string.home)).assertExists()
            compose.onNodeWithText(app.getString(R.string.dictionary)).assertExists()
        } finally {speech.close();scope.cancel()}
    }
}
