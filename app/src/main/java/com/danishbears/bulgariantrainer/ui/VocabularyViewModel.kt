package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.danishbears.bulgariantrainer.data.VocabularyCounts
import com.danishbears.bulgariantrainer.data.VocabularyFilter
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.data.VocabularyText
import com.danishbears.bulgariantrainer.data.VocabularyWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Ready(val counts: VocabularyCounts) : DashboardState
    data object Failed : DashboardState
}

data class DictionaryControls(val query: String = "", val archived: Boolean = false) {
    val queryTooLong: Boolean get() = query.length > 255
}

enum class NoticeKind { ARCHIVED, RESTORED, ARCHIVE_FAILED }
data class UiNotice(val id: Long, val kind: NoticeKind)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VocabularyViewModel(
    private val repository: VocabularyRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val query = saved.getStateFlow("query", "")
    private val archived = saved.getStateFlow("archived", false)
    private val _busy = MutableStateFlow<Set<Long>>(emptySet())
    private val _notice = MutableStateFlow<UiNotice?>(null)
    private var noticeId = 0L

    val busy: StateFlow<Set<Long>> = _busy.asStateFlow()
    val notice: StateFlow<UiNotice?> = _notice.asStateFlow()
    val controls = combine(query, archived, ::DictionaryControls)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionaryControls(query.value, archived.value))

    val dashboard = retry.flatMapLatest {
        repository.observeCounts().map<VocabularyCounts, DashboardState> { DashboardState.Ready(it) }
            .onStart { emit(DashboardState.Loading) }
            .catch { error -> if (error is CancellationException) throw error else emit(DashboardState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState.Loading)

    val words = combine(query.debounce(300), archived, ::DictionaryControls).distinctUntilChanged()
        .flatMapLatest { selection ->
            if (selection.queryTooLong) flowOf(PagingData.empty<VocabularyWord>())
            else repository.pages(VocabularyFilter(
                query = VocabularyText.optional(selection.query), archived = selection.archived,
            ))
        }.cachedIn(viewModelScope)

    fun setQuery(value: String) { saved["query"] = value }
    fun showArchived(value: Boolean) { saved["archived"] = value }
    fun retryDashboard() { retry.update { it + 1 } }
    fun clearNotice(id: Long) { if (_notice.value?.id == id) _notice.value = null }

    fun archive(id: Long, value: Boolean) {
        if (id in _busy.value) return
        _busy.update { it + id }
        viewModelScope.launch {
            try {
                repository.setArchived(id, value)
                _notice.value = UiNotice(++noticeId, if (value) NoticeKind.ARCHIVED else NoticeKind.RESTORED)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _notice.value = UiNotice(++noticeId, NoticeKind.ARCHIVE_FAILED)
            } finally {
                _busy.update { it - id }
            }
        }
    }
}
