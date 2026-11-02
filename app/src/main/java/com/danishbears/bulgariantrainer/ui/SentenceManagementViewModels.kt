package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.danishbears.bulgariantrainer.sentences.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SentenceListViewModel(private val repository: SentenceRepository, private val saved: SavedStateHandle) : ViewModel() {
    val archived = saved.getStateFlow("archived", false)
    val pages = archived.flatMapLatest(repository::pages).cachedIn(viewModelScope)
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<SentenceError?>(null); val error = _error.asStateFlow()
    private var retryAction: Pair<Long, Boolean>? = null
    fun showArchive(value: Boolean) { if (!_busy.value) saved["archived"] = value }
    fun archive(id: Long, value: Boolean) {
        if (_busy.value) return
        _busy.value = true; _error.value = null; retryAction = id to value
        viewModelScope.launch {
            try { repository.setArchived(id, value); retryAction = null }
            catch (e: CancellationException) { throw e }
            catch (e: SentenceException) { _error.value = e.code }
            catch (_: Exception) { _error.value = SentenceError.STORAGE_FAILED }
            finally { _busy.value = false }
        }
    }
    fun retry() { retryAction?.let { archive(it.first, it.second) } }
}

data class SentenceEditDraft(val russian: String = "", val bulgarian: String = "", val alternatives: List<String> = emptyList(),
    val explanation: String = "", val difficulty: String = "", val topic: String = "") {
    fun input(): SentenceInput {
        val level = difficulty.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (difficulty.isNotBlank() && level == null) throw SentenceException(SentenceError.INVALID_SENTENCE)
        return SentenceInput(russian, bulgarian, alternatives, explanation, level, topic)
    }
}
data class SentenceEditorState(val loading: Boolean = true, val saving: Boolean = false, val saved: Boolean = false,
    val dirty: Boolean = false, val draft: SentenceEditDraft = SentenceEditDraft(), val error: SentenceError? = null, val loadFailed: Boolean = false)
class SentenceEditorViewModel(private val repository: SentenceRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val route = saved.get<String>("sentenceId") ?: "new"
    private val id = route.toLongOrNull()
    val isNew get() = route == "new"
    private val _state = MutableStateFlow(SentenceEditorState()); val state = _state.asStateFlow()
    init {
        if (saved.get<Boolean>("initialized") == true) _state.value = SentenceEditorState(loading = false, dirty = saved["dirty"] ?: false,
            draft = SentenceEditDraft(saved["russian"] ?: "", saved["bulgarian"] ?: "", saved.get<ArrayList<String>>("alternatives").orEmpty(),
                saved["explanation"] ?: "", saved["difficulty"] ?: "", saved["topic"] ?: ""))
        else load()
    }
    fun load() {
        if (_state.value.saving) return
        if (isNew) { _state.value = SentenceEditorState(loading = false); persist(_state.value.draft, false); return }
        if (id == null || id < 1) { _state.value = SentenceEditorState(loading = false, error = SentenceError.SENTENCE_NOT_FOUND, loadFailed = true); return }
        _state.update { it.copy(loading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            try {
                val s = repository.get(id).input
                val draft = SentenceEditDraft(s.russian, s.bulgarian, s.alternatives, s.explanation.orEmpty(), s.difficulty?.toString().orEmpty(), s.topic.orEmpty())
                persist(draft, false); _state.value = SentenceEditorState(loading = false, draft = draft)
            } catch (e: CancellationException) { throw e }
            catch (e: SentenceException) { _state.update { it.copy(error = e.code, loadFailed = true) } }
            catch (_: Exception) { _state.update { it.copy(error = SentenceError.STORAGE_FAILED, loadFailed = true) } }
            finally { _state.update { it.copy(loading = false) } }
        }
    }
    fun change(transform: (SentenceEditDraft) -> SentenceEditDraft) {
        val old = _state.value
        if (old.loading || old.saving || old.saved || old.loadFailed) return
        val draft = transform(old.draft); persist(draft, true)
        _state.value = old.copy(draft = draft, dirty = true, error = null)
    }
    fun addAlternative() = change { if (it.alternatives.size < 50) it.copy(alternatives = it.alternatives + "") else it }
    fun alternative(index: Int, text: String) = change { it.copy(alternatives = it.alternatives.mapIndexed { i, old -> if (index == i) text else old }) }
    fun removeAlternative(index: Int) = change { it.copy(alternatives = it.alternatives.filterIndexed { i, _ -> i != index }) }
    fun save() {
        val current = _state.value
        if (current.loading || current.saving || current.saved || current.loadFailed) return
        val valid = try { SentenceValidation.normalize(current.draft.input()) }
        catch (e: SentenceException) { _state.update { it.copy(error = e.code) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (isNew) repository.create(valid) else repository.update(requireNotNull(id), valid)
                saved["dirty"] = false; _state.update { it.copy(saved = true, dirty = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: SentenceException) { _state.update { it.copy(error = e.code) } }
            catch (_: Exception) { _state.update { it.copy(error = SentenceError.STORAGE_FAILED) } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }
    private fun persist(d: SentenceEditDraft, dirty: Boolean) {
        saved["russian"] = d.russian; saved["bulgarian"] = d.bulgarian; saved["alternatives"] = ArrayList(d.alternatives)
        saved["explanation"] = d.explanation; saved["difficulty"] = d.difficulty; saved["topic"] = d.topic
        saved["dirty"] = dirty; saved["initialized"] = true
    }
}
