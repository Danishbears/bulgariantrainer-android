package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.review.*
import com.danishbears.bulgariantrainer.statistics.*
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class StatisticsViewModelTest {
    private lateinit var db:TrainerDatabase
    private val clock=MutableStudyClock();private val store=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher());db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build() }
    @After fun teardown() { store.clear();db.close();Dispatchers.resetMain() }
    private fun model()=StatisticsViewModel(StatisticsRepository(db,clock)).also { store.put("statistics",it) }
    private suspend fun ready(vm:StatisticsViewModel,predicate:(StatisticsOverview)->Boolean={true})=withTimeout(10_000) {
        (vm.state.first { it is StatisticsState.Ready && predicate(it.overview) } as StatisticsState.Ready).overview
    }
    @Test fun explicitRefreshRecalculatesDayWithoutDatabaseWrite()=runBlocking<Unit> {
        val vm=model();val old=ready(vm);assertNull(old.overall.total.accuracyPercent)
        clock.current=clock.current.plus(Duration.ofDays(1));vm.reload()
        val refreshed=ready(vm) { it.at==clock.current }
        assertEquals(old.last7Days.last().date.plusDays(1),refreshed.last7Days.last().date)
    }
    @Test fun committedRatingInvalidatesStatisticsAndReplayCannotAddAnotherSessionOrAttempt()=runBlocking<Unit> {
        VocabularyRepository(db).create(WordInput("къща",listOf(TranslationInput("дом"))))
        val vm=model();ready(vm)
        val flash=FlashcardRepository(db,clock);val s=flash.start(UUID.randomUUID().toString());flash.reveal(s.id,s.question!!.id)
        flash.rate(s.id,s.question!!.id,false)
        val first=ready(vm) { it.reviewAttempts==1L };assertEquals(1L,first.completedSessions)
        flash.rate(s.id,s.question!!.id,false);vm.reload()
        val after=ready(vm) { it.at==clock.current };assertEquals(1L,after.reviewAttempts);assertEquals(1L,after.completedSessions)
    }
}
