package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.aspect.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AspectStatisticsUi(val value:AspectStatistics?=null,val loading:Boolean=true,val refreshing:Boolean=false,val error:String?=null)
@OptIn(ExperimentalCoroutinesApi::class)
class AspectStatisticsViewModel(private val repository:AspectStatisticsRepository,private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(AspectStatisticsUi());val state=mutable.asStateFlow()
    val namespace=saved.getStateFlow("aspectStatisticsDraft","")
    private val applied=saved.getStateFlow("aspectStatisticsApplied","")
    private val refresh=MutableStateFlow(0)
    init {
        viewModelScope.launch {
            refresh.flatMapLatest {
                combine(applied,repository.observeChanges(),flow{while(true){emit(Unit);delay(60_000)}}){ns,_,_->ns}
                    .catch{if(it is CancellationException)throw it;mutable.update{state->state.copy(loading=false,refreshing=false,error="Не удалось наблюдать изменения. Повторите обновление.")}}
            }
                .collectLatest{namespace->
                    val previous=mutable.value.value?.takeIf{it.namespace==namespace.ifBlank{null}}
                    mutable.value=AspectStatisticsUi(previous,previous==null,true)
                    try{mutable.value=AspectStatisticsUi(repository.overview(namespace.ifBlank{null}),loading=false)}
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){mutable.value=AspectStatisticsUi(previous,false,false,if(e is AspectException)e.message else "Не удалось обновить статистику. Предыдущие значения сохранены, если доступны.")}
                }
        }
    }
    fun draft(value:String){saved["aspectStatisticsDraft"]=value}
    fun apply(){
        val chosen=namespace.value.ifBlank{null}
        try{chosen?.let(AspectContent::namespace)}catch(e:AspectException){mutable.update{it.copy(error=e.message)};return}
        saved["aspectStatisticsApplied"]=chosen.orEmpty();reload()
    }
    fun reload(){refresh.update{it+1}}
}
