package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import java.util.UUID
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristStatisticsViewModelTest {
    private lateinit var db:TrainerDatabase;private lateinit var repo:AoristStatisticsRepository
    private val clock=Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"),ZoneOffset.UTC)
    private val store=ViewModelStore()
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher());db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build();repo=AoristStatisticsRepository(db,clock)}
    @After fun close(){store.clear();db.close();Dispatchers.resetMain()}
    private fun model(saved:SavedStateHandle=SavedStateHandle())=AoristStatisticsViewModel(repo,saved).also{store.put("statistics",it)}
    private suspend fun ready(vm:AoristStatisticsViewModel,predicate:(AoristStatisticsUi)->Boolean={true})=withTimeout(15_000){vm.state.first{!it.loading&&!it.refreshing&&predicate(it)}}
    private suspend fun seed(){
        val content=AoristRepository(db,clock);val p=content.preview("starter.json",javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.use{it.readBytes()})
        content.confirm(p,p.changes.map{it.externalId}.toSet())
    }
    @Test fun persistedAttemptRefreshesCountsAndInvalidFilterNeverReplacesConfirmedData()=runBlocking<Unit>{
        seed();val saved=SavedStateHandle();var vm=model(saved);assertEquals(0L,ready(vm){it.value!=null}.value!!.counts.answered)
        val practice=AoristPracticeRepository(db,clock);val s=practice.start(UUID.randomUUID().toString(),AoristSettings(size=1))
        practice.submitTyped(s.id,s.question!!.id,"неверный ответ")
        val confirmed=ready(vm){it.value?.counts?.answered==1L}.value!!
        vm.draft("INVALID DATASET");vm.apply();assertNotNull(vm.state.value.error);assertEquals(confirmed,vm.state.value.value)
        vm.draft("missing");vm.apply();assertEquals(0L,ready(vm){it.value?.datasetId=="missing"}.value!!.counts.answered)
        vm.draft("bg-aorist-core-40");vm.apply();assertEquals(1L,ready(vm){it.value?.datasetId=="bg-aorist-core-40"}.value!!.counts.incorrect)
        store.clear();vm=model(saved);assertEquals("bg-aorist-core-40",ready(vm){it.value!=null}.value!!.datasetId)
    }
    @Test fun queryFailureRetainsLastConfirmedCountersAndRetryRecovers()=runBlocking<Unit>{
        seed();val vm=model();val old=ready(vm){it.value!=null}.value!!
        db.openHelper.writableDatabase.execSQL("ALTER TABLE aorist_attempts RENAME TO unavailable_aorist_attempts")
        vm.reload();assertEquals(old,ready(vm){it.error!=null}.value)
        db.openHelper.writableDatabase.execSQL("ALTER TABLE unavailable_aorist_attempts RENAME TO aorist_attempts")
        vm.reload();assertEquals(old,ready(vm){it.error==null&&it.value!=null}.value)
    }
}
