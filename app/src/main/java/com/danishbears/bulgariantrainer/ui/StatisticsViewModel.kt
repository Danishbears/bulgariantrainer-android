package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.statistics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface StatisticsState {
    data object Loading : StatisticsState
    data class Ready(val overview: StatisticsOverview) : StatisticsState
    data object Failed : StatisticsState
}
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val repository: StatisticsRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val state = refresh.flatMapLatest {
        combine(repository.observeChanges(), flow { while (true) { emit(Unit); delay(60_000) } }) { _, _ -> repository.overview() }
            .map<StatisticsOverview, StatisticsState> { StatisticsState.Ready(it) }.onStart { emit(StatisticsState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(StatisticsState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsState.Loading)
    fun reload() { refresh.update { it + 1 } }
}
data class WordMistakesState(val loading: Boolean = true, val value: WordMistakes? = null, val failed: Boolean = false)
class WordMistakesViewModel(private val repository: StatisticsRepository, private val wordId: Long) : ViewModel() {
    private val _state = MutableStateFlow(WordMistakesState()); val state = _state.asStateFlow()
    init { reload() }
    fun reload() {
        _state.value = WordMistakesState()
        viewModelScope.launch {
            try { _state.value = WordMistakesState(loading = false, value = repository.wordMistakes(wordId)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = WordMistakesState(loading = false, failed = true) }
        }
    }
}
