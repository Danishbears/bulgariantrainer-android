package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.aorist.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AoristStatisticsUi(val value:AoristStatistics?=null,val loading:Boolean=true,val refreshing:Boolean=false,val error:String?=null)
@OptIn(ExperimentalCoroutinesApi::class)
class AoristStatisticsViewModel(private val repository:AoristStatisticsRepository,private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(AoristStatisticsUi());val state=mutable.asStateFlow()
    val dataset=saved.getStateFlow("aoristStatisticsDraft","")
    private val applied=saved.getStateFlow("aoristStatisticsApplied","")
    private val refresh=MutableStateFlow(0)
    init {
        viewModelScope.launch {
            refresh.flatMapLatest {
                combine(applied,repository.observeChanges(),flow{while(true){emit(Unit);delay(60_000)}}){ns,_,_->ns}
                    .catch{if(it is CancellationException)throw it;mutable.update{state->state.copy(loading=false,refreshing=false,error="Не удалось наблюдать изменения. Повторите обновление.")}}
            }
                .collectLatest{dataset->
                    val previous=mutable.value.value?.takeIf{it.datasetId==dataset.ifBlank{null}}
                    mutable.value=AoristStatisticsUi(previous,previous==null,true)
                    try{mutable.value=AoristStatisticsUi(repository.overview(dataset.ifBlank{null}),loading=false)}
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){mutable.value=AoristStatisticsUi(previous,false,false,if(e is AoristException)e.message else "Не удалось обновить статистику. Предыдущие значения сохранены, если доступны.")}
                }
        }
    }
    fun draft(value:String){saved["aoristStatisticsDraft"]=value}
    fun apply(){
        val chosen=dataset.value.ifBlank{null}
        try{chosen?.let(AoristContent::identifier)}catch(e:AoristException){mutable.update{it.copy(error=e.message)};return}
        saved["aoristStatisticsApplied"]=chosen.orEmpty();reload()
    }
    fun reload(){refresh.update{it+1}}
}
