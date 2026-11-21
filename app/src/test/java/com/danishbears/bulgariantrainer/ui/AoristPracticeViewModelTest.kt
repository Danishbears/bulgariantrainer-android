package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.data.*
import java.util.UUID
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
class AoristPracticeViewModelTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase;private lateinit var repo:AoristPracticeRepository
    private val name="aorist-vm-${UUID.randomUUID()}.db";private val store=ViewModelStore();private var index=0
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher());context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).build();repo=AoristPracticeRepository(db)}
    @After fun close(){store.clear();db.close();context.deleteDatabase(name);Dispatchers.resetMain()}
    private fun model(saved:SavedStateHandle=SavedStateHandle())=AoristPracticeViewModel(repo,saved).also{store.put("aorist"+index++,it)}
    private suspend fun ready(m:AoristPracticeViewModel,predicate:(AoristPracticeUi)->Boolean={true})=withTimeout(20_000){m.state.first{!it.loading&&!it.busy&&predicate(it)}}
    private suspend fun seed(){val r=AoristContent.strict(javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.bufferedReader().use{it.readText()}).jsonObject
        val e=r.getValue("entries").jsonArray.single{it.jsonObject["lemma"]==JsonPrimitive("чета")}
        val c=AoristRepository(db);val p=c.preview("fixture.json",JsonObject(r+("entries" to JsonArray(listOf(e)))).toString().toByteArray());c.confirm(p,p.changes.map{it.externalId}.toSet())}
    private suspend fun start(m:AoristPracticeViewModel,choice:Boolean=false):AoristPracticeState {
        ready(m);m.setSize("1");if(choice)m.setExercise(AoristExercise.CHOICE);m.start();return ready(m){it.session!=null}.session!!
    }
    @Test fun defaultAzRapidSubmitNextAndOldUiCallbacksCannotAffectAnotherQuestion()=runBlocking<Unit> {
        seed();val m=model();ready(m);assertEquals(AoristExercise.TYPED,AoristSettings.read(m.config.value).exercise);assertEquals(listOf("1sg"),AoristSettings.read(m.config.value).persons)
        val s=start(m);val q=s.question!!;m.setInput("ЧЕТОХ")
        m.submit(s.id,q.id,"ЧЕТОХ",null);m.submit(s.id,q.id,"ЧЕТОХ",null)
        ready(m){it.session?.question?.feedback!=null};assertEquals(1,repo.progress(q.verbId,"1sg",AoristExercise.TYPED)!!.attempts)
        m.next(s.id,q.id);m.next(s.id,q.id);ready(m){it.session?.status==AoristSessionStatus.COMPLETED}
        m.newSession();m.start();val second=ready(m){it.session?.active==true}.session!!
        m.setInput("черновик нового задания");m.reveal(s.id,q.id);m.submit(s.id,q.id,"ЧЕТОХ",null);m.next(s.id,q.id)
        assertEquals(second.id,m.state.value.session!!.id);assertNull(m.state.value.session!!.question!!.feedback)
        assertEquals(1,repo.progress(q.verbId,"1sg",AoristExercise.TYPED)!!.attempts)
    }
    @Test fun emptySavedStateAfterDatabaseReopenRecoversTypedDraftAndFeedbackWaitingForNext()=runBlocking<Unit> {
        seed();var m=model();val s=start(m);val q=s.question!!;m.setInput("че")
        ready(m){it.session?.question?.draft=="че"};store.clear();db.close();open();m=model()
        val recovered=ready(m){it.session!=null}.session!!;assertEquals(s.id,recovered.id);assertEquals("че",m.input.value)
        m.submit(s.id,q.id,"четох",null);val feedback=ready(m){it.session?.question?.feedback!=null}.session!!
        store.clear();db.close();open();m=model();assertEquals(feedback,ready(m){it.session!=null}.session)
        assertTrue(m.state.value.session!!.active);m.next(s.id,q.id);assertEquals(1,ready(m){it.session?.status==AoristSessionStatus.COMPLETED}.session!!.correct)
    }
    @Test fun choiceOrderAndDraftSelectionRecoverAndLastUsedSettingsAreDurable()=runBlocking<Unit> {
        seed();var m=model();val s=start(m,true);val q=s.question!!;val chosen=q.options.first();m.select(chosen.id)
        ready(m){it.session?.question?.selectedDraft==chosen.id};store.clear();db.close();open();m=model()
        val recovered=ready(m){it.session!=null}.session!!;assertEquals(q.options,recovered.question!!.options);assertEquals(chosen.id,m.selection.value)
        m.submit(s.id,q.id,"",chosen.id);ready(m){it.session?.question?.feedback!=null};m.next(s.id,q.id);ready(m){it.session?.status==AoristSessionStatus.COMPLETED}
        store.clear();m=model(SavedStateHandle(mapOf("practiceSetup" to true)));ready(m)
        assertEquals(AoristExercise.CHOICE,AoristSettings.read(m.config.value).exercise);assertEquals("1",m.size.value)
    }
    @Test fun navigationFlushesDraftAndFailureBlocksExitWithoutLosingLocalInput()=runBlocking<Unit> {
        seed();val m=model();val s=start(m);var left=false
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aorist_draft BEFORE UPDATE ON aorist_questions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        m.setInput("не потерять");ready(m){it.draftError};m.leave{left=true};ready(m){it.error!=null}
        assertFalse(left);assertEquals("не потерять",m.input.value)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aorist_draft")
        m.leave{left=true};ready(m);assertTrue(left);assertEquals("не потерять",repo.get(s.id).question!!.draft)
        assertTrue(db.aoristPracticeDao().progressRows().isEmpty())
    }
    @Test fun revealingDisablesEditingAndCannotBecomeIndependentRecallOnRetry()=runBlocking<Unit> {
        seed();val m=model();val s=start(m);val q=s.question!!;m.setInput("черновик")
        m.reveal(s.id,q.id);ready(m){it.session?.question?.feedback?.outcome==AoristOutcome.REVEALED}
        m.setInput("четох");assertEquals("",m.input.value)
        m.submit(s.id,q.id,"четох",null);ready(m){it.error!=null}
        val p=repo.progress(q.verbId,"1sg",AoristExercise.TYPED)!!;assertEquals(0,p.correct);assertEquals(1,p.revealed);assertEquals(1,p.attempts)
    }
}
