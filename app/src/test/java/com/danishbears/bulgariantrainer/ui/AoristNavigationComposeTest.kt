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

/** Native Compose semantics under Robolectric; not a device/storage-provider check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=BulgarianTrainerApplication::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristNavigationComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun dashboardOpensAoristAndBackRestoresExistingBottomNavigationWithoutAutomaticImport() {
        val app=compose.activity.application as BulgarianTrainerApplication
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(scope,{fake});speech.initialize();fake.initialized!!(true)
        try {
            compose.setContent{MaterialTheme{CompositionLocalProvider(LocalPronunciation provides speech){
                TrainerApp(app.vocabulary,app.transfer,app.flashcards,app.typing,app.sentences,app.sentencePractice,app.statistics)
            }}}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.aorist_title)))
            compose.onNodeWithText(app.getString(R.string.aorist_title)).performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.aorist_search)).assertExists()
            compose.onNodeWithText(app.getString(R.string.home)).assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.dictionary)).assertDoesNotExist()
            org.junit.Assert.assertTrue(runBlocking{app.aorist.datasets().isEmpty()})
            compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithText(app.getString(R.string.home)).assertExists()
            compose.onNodeWithText(app.getString(R.string.dictionary)).assertExists()
        } finally{speech.close();scope.cancel()}
    }
}
