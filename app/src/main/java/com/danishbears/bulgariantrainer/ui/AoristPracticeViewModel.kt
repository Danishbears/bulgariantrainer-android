package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.*
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.review.StudyScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface AoristPracticeAvailability {
    data object Loading:AoristPracticeAvailability
    data class Ready(val value:AoristAvailability):AoristPracticeAvailability
    data class Failed(val message:String):AoristPracticeAvailability
}
sealed interface AoristAction {
    data class Start(val request:String,val settings:AoristSettings,val shorter:Boolean,val fewer:Boolean):AoristAction
    data class Typed(val session:Long,val question:Long,val raw:String):AoristAction
    data class Choice(val session:Long,val question:Long,val option:String):AoristAction
    data class Reveal(val session:Long,val question:Long):AoristAction
    data class Next(val session:Long,val question:Long):AoristAction
    data class Complete(val session:Long):AoristAction
}
data class AoristPracticeUi(val loading:Boolean=true,val busy:Boolean=false,val session:AoristPracticeState?=null,
    val pending:AoristAction?=null,val error:String?=null,val draftError:Boolean=false,val restored:Boolean=false,val hasHistory:Boolean=false)

@OptIn(ExperimentalCoroutinesApi::class)
class AoristPracticeViewModel(private val repository:AoristPracticeRepository,private val saved:SavedStateHandle):ViewModel() {
    private val hadConfig=saved.contains("practiceConfig")
    private val mutable=MutableStateFlow(AoristPracticeUi())
    val state=mutable.asStateFlow()
    val config=saved.getStateFlow("practiceConfig",AoristSettings().json())
    val size=saved.getStateFlow("practiceSize","20")
    val group=saved.getStateFlow("practiceGroup","")
    val dataset=saved.getStateFlow("practiceDataset","")
    val input=saved.getStateFlow("practiceInput","")
    val selection=saved.getStateFlow<String?>("practiceSelection",null)
    private val refresh=MutableStateFlow(0)
    private val polling=flow{while(true){emit(Unit);delay(60_000)}}
    val availability=combine(config,size,group,dataset,refresh){_,_,_,_,_->Unit}.combine(polling){_,_->Unit}.flatMapLatest {
        flow<AoristPracticeAvailability>{emit(AoristPracticeAvailability.Loading);emit(AoristPracticeAvailability.Ready(repository.availability(settings())))}
            .catch{if(it is CancellationException)throw it;emit(AoristPracticeAvailability.Failed(if(it is AoristException)it.message.orEmpty()else "Не удалось загрузить очередь аориста."))}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),AoristPracticeAvailability.Loading)
    private var observer:Job?=null
    private var observing:Long?=null
    private data class Draft(val session:Long,val question:Long,val raw:String,val option:String?,val version:Long)
    private var pendingDraft:Draft?=null
    init{reload()}
    private fun enabled()=!mutable.value.loading&&!mutable.value.busy&&mutable.value.pending==null
    private fun settings()=AoristSettings.read(config.value).copy(size=size.value.toIntOrNull()?:throw AoristException("Введите размер от 1 до 100."),studyGroup=group.value.ifBlank{null},datasetId=dataset.value.ifBlank{null}).canonical()
    private fun configure(s:AoristSettings) {saved["practiceConfig"]=s.json();saved["practiceSize"]=s.size.toString();saved["practiceGroup"]=s.studyGroup.orEmpty();saved["practiceDataset"]=s.datasetId.orEmpty()}
    fun setSize(value:String){if(enabled())saved["practiceSize"]=value}
    fun setGroup(value:String){if(enabled())saved["practiceGroup"]=value}
    fun setDataset(value:String){if(enabled())saved["practiceDataset"]=value}
    fun setExercise(value:AoristExercise){if(enabled())saved["practiceConfig"]=AoristSettings.read(config.value).copy(exercise=value).json()}
    fun setScope(value:StudyScope){if(enabled())saved["practiceConfig"]=AoristSettings.read(config.value).copy(scope=value).json()}
    fun setAspect(value:String?){if(enabled())saved["practiceConfig"]=AoristSettings.read(config.value).copy(aspect=value).canonical().json()}
    fun persons(values:List<String>) {
        if(!enabled())return
        try{saved["practiceConfig"]=AoristSettings.read(config.value).copy(persons=values).canonical().json()}
        catch(e:AoristException){mutable.update{it.copy(error=e.message)}}
    }
    fun person(value:String,checked:Boolean){val old=AoristSettings.read(config.value).persons;persons(if(checked)(old+value).distinct()else old-value)}
    fun setInput(value:String){if(enabled()&&mutable.value.session?.question?.feedback==null&&value.length<=4000){saved["practiceInput"]=value;saveDraft()}}
    fun select(value:String){if(enabled()&&mutable.value.session?.question?.feedback==null){saved["practiceSelection"]=value;saveDraft()}}
    private fun saveDraft() {
        val s=mutable.value.session?:return;val q=s.question?:return
        if(q.feedback!=null)return
        val version=maxOf(saved.get<Long>("practiceDraftVersion")?:0,q.draftVersion)+1;saved["practiceDraftVersion"]=version
        val d=Draft(s.id,q.id,if(q.exercise==AoristExercise.TYPED)input.value else "",if(q.exercise==AoristExercise.CHOICE)selection.value else null,version)
        pendingDraft=d
        viewModelScope.launch {
            try{persist(d)}catch(e:CancellationException){throw e}catch(_:Exception){
                if(mutable.value.session?.question?.let{it.id==d.question&&it.feedback==null}==true&&saved.get<Long>("practiceDraftVersion")==d.version)mutable.update{it.copy(draftError=true)}
            }
        }
    }
    private suspend fun persist(d:Draft) {repository.saveDraft(d.session,d.question,d.raw,d.option,d.version);if(pendingDraft==d)pendingDraft=null}
    fun leave(onLeft:()->Unit) {
        if(!enabled())return
        mutable.update{it.copy(busy=true)}
        viewModelScope.launch {
            try{pendingDraft?.let{persist(it)};onLeft()}
            catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(error="Не удалось сохранить черновик. Повторите выход.",draftError=true)}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    fun retryAvailable(){refresh.update{it+1}}
    fun reload(force:Boolean=false) {
        if(mutable.value.busy)return
        observer?.cancel();observing=null;mutable.update{it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {
                val found=repository.restore(saved.get<Long>("practiceSession"))
                if(found!=null&&(force||found.active||saved.get<Boolean>("practiceSetup")!=true)){accept(found);mutable.update{it.copy(restored=found.active)}}
                else {
                    if(!hadConfig&&saved.get<Boolean>("practicePreferencesLoaded")!=true)configure(repository.preferences())
                    saved["practicePreferencesLoaded"]=true
                    mutable.update{it.copy(loading=false,session=null,pending=null,hasHistory=found!=null)}
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(loading=false,error="Не удалось восстановить занятие аориста.")}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    fun newSession() {
        if(!enabled()||mutable.value.session?.active==true)return
        observer?.cancel();observing=null;pendingDraft=null;saved["practiceSession"]=null;saved["practiceSetup"]=true
        mutable.update{it.copy(session=null,error=null,draftError=false,restored=false)};retryAvailable()
    }
    fun start(shorter:Boolean=false,fewer:Boolean=false) {
        if(!enabled()||mutable.value.session?.active==true)return
        try{perform(AoristAction.Start(UUID.randomUUID().toString(),settings(),shorter,fewer))}catch(e:AoristException){mutable.update{it.copy(error=e.message)}}
    }
    fun submit(sessionId:Long,questionId:Long,raw:String,option:String?) {
        val s=mutable.value.session?:return;val q=s.question?:return
        if(s.id!=sessionId||q.id!=questionId)return
        if(q.exercise==AoristExercise.TYPED)perform(AoristAction.Typed(sessionId,questionId,raw))
        else option?.let{perform(AoristAction.Choice(sessionId,questionId,it))}
    }
    fun reveal(sessionId:Long,questionId:Long)=perform(AoristAction.Reveal(sessionId,questionId))
    fun next(sessionId:Long,questionId:Long)=perform(AoristAction.Next(sessionId,questionId))
    fun complete(sessionId:Long)=perform(AoristAction.Complete(sessionId))
    fun retry(){mutable.value.pending?.let(::perform)?:reload(true)}
    private fun perform(action:AoristAction) {
        val old=mutable.value
        if(old.loading||old.busy||old.pending!=null&&old.pending!=action)return
        if(old.pending==null&&action !is AoristAction.Start) {
            val session=when(action){is AoristAction.Typed->action.session;is AoristAction.Choice->action.session;is AoristAction.Reveal->action.session;is AoristAction.Next->action.session;is AoristAction.Complete->action.session;else->null}
            val question=when(action){is AoristAction.Typed->action.question;is AoristAction.Choice->action.question;is AoristAction.Reveal->action.question;is AoristAction.Next->action.question;else->null}
            if(old.session?.id!=session||question!=null&&old.session?.question?.id!=question)return
        }
        mutable.update{it.copy(busy=true,pending=action,error=null)}
        viewModelScope.launch {
            try {
                accept(when(action) {
                    is AoristAction.Start->repository.start(action.request,action.settings,action.shorter,action.fewer)
                    is AoristAction.Typed->repository.submitTyped(action.session,action.question,action.raw)
                    is AoristAction.Choice->repository.submitChoice(action.session,action.question,action.option)
                    is AoristAction.Reveal->repository.reveal(action.session,action.question)
                    is AoristAction.Next->repository.next(action.session,action.question)
                    is AoristAction.Complete->repository.complete(action.session)
                });pendingDraft=null;retryAvailable()
            }catch(e:CancellationException){throw e}
            catch(e:AoristException){mutable.update{it.copy(pending=null,error=e.message)}}
            catch(_:Exception){mutable.update{it.copy(error="Сохранение не подтверждено. Повторите тот же запрос или перечитайте занятие.")}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    private fun sync(s:AoristPracticeState) {
        val q=s.question;val new=saved.get<Long>("practiceInputQuestion")!=q?.id
        if(new||q?.feedback!=null||(q!=null&&q.draftVersion>=(saved.get<Long>("practiceDraftVersion")?:0))) {
            saved["practiceInputQuestion"]=q?.id;saved["practiceInput"]=q?.draft.orEmpty();saved["practiceSelection"]=q?.selectedDraft;saved["practiceDraftVersion"]=q?.draftVersion?:0
            mutable.update{it.copy(draftError=false)}
        }
    }
    private fun accept(s:AoristPracticeState) {
        val previous=mutable.value.session
        if(previous!=null&&previous.id==s.id&&previous.revision>s.revision)return
        sync(s);configure(s.settings);saved["practiceSession"]=s.id;saved["practiceSetup"]=false
        mutable.update{it.copy(loading=false,session=s,pending=null,error=null,hasHistory=true)}
        if(observing==s.id)return
        observer?.cancel();observing=s.id
        observer=viewModelScope.launch {
            repository.observe(s.id).catch{if(it is CancellationException)throw it;mutable.update{it.copy(error="Не удалось перечитать занятие.")}}
                .collect{incoming->if(incoming!=null){val old=mutable.value.session
                    if(old==null||old.id!=incoming.id||incoming.revision>=old.revision){sync(incoming);mutable.update{it.copy(session=incoming)}}}}
        }
    }
}
