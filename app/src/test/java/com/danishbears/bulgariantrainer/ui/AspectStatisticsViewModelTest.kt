package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import java.util.UUID
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class AspectStatisticsViewModelTest {
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectStatisticsRepository
    private val store=ViewModelStore()
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher());db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build();repo=AspectStatisticsRepository(db,Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"),ZoneOffset.UTC))}
    @After fun close(){store.clear();db.close();Dispatchers.resetMain()}
    private fun model()=AspectStatisticsViewModel(repo,SavedStateHandle()).also{store.put("statistics",it)}
    private suspend fun ready(vm:AspectStatisticsViewModel,predicate:(AspectStatisticsUi)->Boolean={true})=withTimeout(10_000){vm.state.first{!it.loading&&!it.refreshing&&predicate(it)}}
    private suspend fun seed(){
        val root=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.bufferedReader().use{it.readText()}).jsonObject
        val e=root.getValue("entries").jsonArray.single{it.jsonObject["id"]==JsonPrimitive("bgasp-057")}
        val content=AspectRepository(db);val p=content.preview("fixture","fixture.json",JsonObject(root+("entries" to JsonArray(listOf(e)))).toString().toByteArray());content.confirm(p,setOf("bgasp-057"))
    }
    @Test fun persistedAttemptInvalidatesStatisticsAndInvalidDraftKeepsConfirmedValues()=runBlocking<Unit>{
        seed();val vm=model();assertEquals(0L,ready(vm){it.value!=null}.value!!.answered)
        val practice=AspectPracticeRepository(db);val s=practice.start(UUID.randomUUID().toString(),AspectSettings(1,direction=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE));practice.submit(s.id,s.question!!.id,"купя")
        val confirmed=ready(vm){it.value?.answered==1L}.value!!
        vm.draft("INVALID NAMESPACE");vm.apply();assertNotNull(vm.state.value.error);assertEquals(confirmed,vm.state.value.value)
        vm.draft("empty");vm.apply();val empty=ready(vm){it.value?.namespace=="empty"};assertEquals(0L,empty.value!!.answered)
        vm.draft("fixture");vm.apply();assertEquals(1L,ready(vm){it.value?.namespace=="fixture"}.value!!.answered)
    }
    @Test fun loadingFailureDoesNotReplaceLastConfirmedCountsWithFabricatedZeros()=runBlocking<Unit>{
        seed();val vm=model();ready(vm){it.value!=null}
        val old=vm.state.value.value!!;db.close();vm.reload()
        val error=ready(vm){it.error!=null};assertEquals(old,error.value)
    }
}
