package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.data.*
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
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectPracticeViewModelTest {
    private lateinit var context:Context
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectPracticeRepository
    private val store=ViewModelStore()
    private val filename="aspect-vm-${UUID.randomUUID()}.db"
    private var number=0
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher());context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,filename).addMigrations(MIGRATION_8_9).build();repo=AspectPracticeRepository(db)}
    @After fun teardown(){store.clear();db.close();context.deleteDatabase(filename);Dispatchers.resetMain()}
    private fun model()=AspectPracticeViewModel(repo,SavedStateHandle()).also{store.put("aspect${number++}",it)}
    private suspend fun ready(vm:AspectPracticeViewModel,predicate:(AspectPracticeUi)->Boolean={true})=withTimeout(10_000){vm.state.first{!it.loading&&!it.busy&&predicate(it)}}
    private fun count(table:String)=db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use{it.moveToFirst();it.getInt(0)}
    private suspend fun seed(){val bytes=javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.readBytes();val content=AspectRepository(db);val p=content.preview("fixture","fixture.json",bytes);content.confirm(p,p.changes.map{it.externalId}.toSet())}
    @Test fun doubleTapsAndStaleCallbacksCannotAdvanceOrAnswerAnotherQuestion()=runBlocking<Unit>{
        seed();val vm=model();ready(vm);vm.setSize("2");vm.start();vm.start()
        val initial=ready(vm){it.session!=null}.session!!;val q=initial.question!!
        assertEquals(1,count("aspect_sessions"))
        vm.submit(initial.id,q.id,"ошибка");vm.submit(initial.id,q.id,"ошибка")
        ready(vm){it.session?.question?.feedback!=null};assertEquals(1,count("aspect_attempts"))
        vm.next(initial.id,q.id);vm.next(initial.id,q.id)
        val next=ready(vm){it.session?.position==2}.session!!
        vm.reveal(initial.id,q.id);vm.submit(initial.id,q.id,"ошибка");ready(vm)
        assertEquals(next,vm.state.value.session);assertEquals(1,count("aspect_attempts"))
        vm.reveal(initial.id,next.question!!.id);ready(vm){it.session?.revealed==1}
        vm.newSession();assertNotNull(vm.state.value.session!!.question)
        vm.next(initial.id,next.question!!.id);ready(vm){it.session?.question==null}
        vm.newSession();vm.start();val newer=ready(vm){it.session!=null}.session!!
        vm.complete(initial.id);vm.submit(initial.id,q.id,"ошибка");ready(vm)
        assertEquals(newer,vm.state.value.session);assertEquals(2,count("aspect_attempts"))
    }
    @Test fun freshViewModelAndDatabaseReopenRecoverRawFeedbackThenRevealedFinalFeedback()=runBlocking<Unit>{
        seed();var vm=model();ready(vm);vm.setSize("2");vm.start()
        val s=ready(vm){it.session!=null}.session!!;vm.submit(s.id,s.question!!.id,"  ОШИБКА  ")
        val feedback=ready(vm){it.session?.answered==1}.session!!
        store.clear();db.close();open();vm=model()
        assertEquals(feedback,ready(vm){it.session!=null}.session);assertEquals("  ОШИБКА  ",vm.input.value)
        vm.next(s.id,feedback.question!!.id);val next=ready(vm){it.session?.position==2}.session!!
        vm.reveal(s.id,next.question!!.id);val revealed=ready(vm){it.session?.revealed==1}.session!!
        store.clear();db.close();open();vm=model()
        assertEquals(revealed,ready(vm){it.session!=null}.session)
        vm.next(s.id,next.question!!.id);val summary=ready(vm){it.session?.question==null}.session!!
        assertEquals(1,summary.incorrect);assertEquals(1,summary.revealed)
    }
    @Test fun failedWriteKeepsFrozenPayloadAndExplicitRetryWritesOnlyOnce()=runBlocking<Unit>{
        seed();val vm=model();ready(vm);vm.setSize("1");vm.start();val s=ready(vm){it.session!=null}.session!!;val q=s.question!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_aspect_vm BEFORE UPDATE ON aspect_sessions BEGIN SELECT RAISE(ABORT,'fixture'); END")
        vm.setInput(" ОШИБКА ");vm.submit(s.id,q.id,vm.input.value)
        val failure=ready(vm){it.pending!=null&&it.error!=null}
        assertEquals(AspectAction.Submit(s.id,q.id," ОШИБКА "),failure.pending);assertEquals(0,count("aspect_attempts"))
        vm.setInput("другое");assertEquals(" ОШИБКА ",vm.input.value)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_aspect_vm")
        vm.retry();val result=ready(vm){it.session?.answered==1}
        assertNull(result.pending);assertEquals(" ОШИБКА ",result.session!!.question!!.feedback!!.submitted);assertEquals(1,count("aspect_attempts"))
    }
}
