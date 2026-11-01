package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.*
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.review.StudyScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface AspectPracticeAvailability {
    data object Loading:AspectPracticeAvailability
    data class Ready(val value:AspectAvailability):AspectPracticeAvailability
    data class Failed(val message:String):AspectPracticeAvailability
}
sealed interface AspectAction {
    data class Start(val request:String,val settings:AspectSettings,val shorter:Boolean):AspectAction
    data class Submit(val session:Long,val question:Long,val raw:String):AspectAction
    data class Reveal(val session:Long,val question:Long):AspectAction
    data class Next(val session:Long,val question:Long):AspectAction
    data class Complete(val session:Long):AspectAction
}
data class AspectPracticeUi(val loading:Boolean=true,val busy:Boolean=false,val session:AspectPracticeState?=null,
    val pending:AspectAction?=null,val error:String?=null,val hasHistory:Boolean=false)
@OptIn(ExperimentalCoroutinesApi::class)
class AspectPracticeViewModel(private val repository:AspectPracticeRepository,private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(AspectPracticeUi())
    val state=mutable.asStateFlow()
    val config=saved.getStateFlow("config",AspectSettings().json())
    val size=saved.getStateFlow("size","20")
    val topic=saved.getStateFlow("topic","")
    val namespace=saved.getStateFlow("namespace","")
    val input=saved.getStateFlow("input","")
    private val refresh=MutableStateFlow(0)
    private val polling=flow { while(true){emit(Unit);delay(60_000)} }
    private val drafts=combine(config,size,topic,namespace){_,_,_,_->Unit}
    val availability=combine(drafts,refresh,polling){_,_,_->Unit}.flatMapLatest {
        flow<AspectPracticeAvailability>{emit(AspectPracticeAvailability.Loading);emit(AspectPracticeAvailability.Ready(repository.availability(settings())))}
            .catch{if(it is CancellationException)throw it;emit(AspectPracticeAvailability.Failed(if(it is AspectException)it.message.orEmpty() else "Не удалось загрузить очередь."))}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),AspectPracticeAvailability.Loading)
    private var observer:Job?=null
    private var observing:Long?=null
    init{reload()}
    private fun enabled()=!mutable.value.loading&&!mutable.value.busy&&mutable.value.pending==null
    private fun settings()=AspectSettings.read(config.value).copy(size=size.value.toIntOrNull()?:throw AspectException("Введите размер от 1 до 100."),
        topic=topic.value.ifBlank{null},namespace=namespace.value.ifBlank{null}).canonical()
    fun setSize(value:String){if(enabled())saved["size"]=value}
    fun setTopic(value:String){if(enabled())saved["topic"]=value}
    fun setNamespace(value:String){if(enabled())saved["namespace"]=value}
    fun setDirection(value:AspectDirection){if(enabled())saved["config"]=AspectSettings.read(config.value).copy(direction=value).json()}
    fun setScope(value:StudyScope){if(enabled())saved["config"]=AspectSettings.read(config.value).copy(scope=value).json()}
    fun stage(value:Int,checked:Boolean) {
        if(!enabled())return
        val old=AspectSettings.read(config.value);val next=if(checked)(old.stages+value).distinct().sorted() else old.stages-value
        if(next.isEmpty()){mutable.update{it.copy(error="Выберите хотя бы одну стадию.")};return}
        saved["config"]=old.copy(stages=next).canonical().json()
    }
    fun setInput(value:String){if(enabled()&&mutable.value.session?.question?.feedback==null)saved["input"]=value}
    fun retryAvailable(){refresh.update{it+1}}
    fun reload(force:Boolean=false) {
        if(mutable.value.busy)return
        observer?.cancel();observing=null;mutable.update{it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {
                val found=repository.restore(saved.get<Long>("session"))
                if(found!=null&&(force||saved.get<Boolean>("setup")!=true||found.active))accept(found)
                else mutable.update{it.copy(loading=false,session=null,pending=null,hasHistory=found!=null)}
            }catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(loading=false,error="Не удалось восстановить занятие.")}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    fun newSession() {
        if(!enabled()||mutable.value.session?.let{it.active||it.question!=null}==true)return
        observer?.cancel();observing=null;saved["session"]=null;saved["setup"]=true
        mutable.update{it.copy(session=null,error=null)}
    }
    fun start(shorter:Boolean=false) {
        if(!enabled()||mutable.value.session?.let{it.active||it.question!=null}==true)return
        try{perform(AspectAction.Start(UUID.randomUUID().toString(),settings(),shorter))}catch(e:AspectException){mutable.update{it.copy(error=e.message)}}
    }
    fun submit(session:Long,question:Long,raw:String)=perform(AspectAction.Submit(session,question,raw))
    fun reveal(session:Long,question:Long)=perform(AspectAction.Reveal(session,question))
    fun next(session:Long,question:Long)=perform(AspectAction.Next(session,question))
    fun complete(session:Long)=perform(AspectAction.Complete(session))
    fun retry(){mutable.value.pending?.let(::perform)?:reload(true)}
    private fun perform(action:AspectAction) {
        val old=mutable.value
        if(old.loading||old.busy||old.pending!=null&&old.pending!=action)return
        if(old.pending==null&&action !is AspectAction.Start) {
            val session=when(action){is AspectAction.Submit->action.session;is AspectAction.Reveal->action.session;is AspectAction.Next->action.session;is AspectAction.Complete->action.session;else->null}
            val question=when(action){is AspectAction.Submit->action.question;is AspectAction.Reveal->action.question;is AspectAction.Next->action.question;else->null}
            if(old.session?.id!=session||question!=null&&old.session?.question?.id!=question)return
        }
        mutable.update{it.copy(busy=true,pending=action,error=null)}
        viewModelScope.launch {
            try {
                accept(when(action){
                    is AspectAction.Start->repository.start(action.request,action.settings,action.shorter)
                    is AspectAction.Submit->repository.submit(action.session,action.question,action.raw)
                    is AspectAction.Reveal->repository.reveal(action.session,action.question)
                    is AspectAction.Next->repository.next(action.session,action.question)
                    is AspectAction.Complete->repository.complete(action.session)
                })
            }catch(e:CancellationException){throw e}
            catch(e:AspectException){mutable.update{it.copy(pending=null,error=e.message)};retryAvailable()}
            catch(_:Exception){mutable.update{it.copy(error="Сохранение не подтверждено. Повторите тот же запрос или перечитайте сохранённое занятие.")}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    private fun sync(session:AspectPracticeState) {
        val q=session.question
        if(saved.get<Long>("inputQuestion")!=q?.id){saved["inputQuestion"]=q?.id;saved["input"]=q?.feedback?.submitted.orEmpty()}
        else q?.feedback?.let{saved["input"]=it.submitted.orEmpty()}
    }
    private fun accept(session:AspectPracticeState) {
        val previous=mutable.value.session
        if(previous!=null&&previous.id==session.id&&previous.revision>session.revision){accept(previous);return}
        sync(session);saved["session"]=session.id;saved["setup"]=false
        saved["config"]=session.settings.json();saved["size"]=session.settings.size.toString();saved["topic"]=session.settings.topic.orEmpty();saved["namespace"]=session.settings.namespace.orEmpty()
        mutable.update{it.copy(loading=false,session=session,pending=null,error=null,hasHistory=true)}
        if(observing==session.id)return
        observer?.cancel();observing=session.id
        observer=viewModelScope.launch {
            repository.observe(session.id).catch{if(it is CancellationException)throw it;mutable.update{it.copy(error="Не удалось перечитать занятие.")}}
                .collect{incoming->if(incoming!=null){val current=mutable.value.session;if(current==null||current.id!=incoming.id||incoming.revision>=current.revision){sync(incoming);mutable.update{it.copy(session=incoming)}}}}
        }
    }
}
