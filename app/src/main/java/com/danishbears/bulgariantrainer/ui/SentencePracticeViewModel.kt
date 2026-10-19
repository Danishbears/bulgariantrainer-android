package com.danishbears.bulgariantrainer.ui

import com.danishbears.bulgariantrainer.review.StudyScope
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.sentences.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SentencePracticeAvailability {
    data object Loading : SentencePracticeAvailability
    data class Ready(val count: Long) : SentencePracticeAvailability
    data object Failed : SentencePracticeAvailability
}
sealed interface SentencePracticeAction {
    data class Start(val requestId: String, val size: Int, val scope: StudyScope = StudyScope.MIXED) : SentencePracticeAction
    data class Submit(val session: Long, val question: Long, val submitted: String) : SentencePracticeAction
    data class SelfRate(val session: Long, val question: Long, val rating: SentenceRating) : SentencePracticeAction
    data class Next(val session: Long, val question: Long) : SentencePracticeAction
    data class Complete(val session: Long) : SentencePracticeAction
}
data class SentencePracticeUiState(
    val loading: Boolean = true, val busy: Boolean = false, val session: SentencePracticeState? = null,
    val error: SentenceError? = null, val pending: SentencePracticeAction? = null, val hasHistory: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SentencePracticeViewModel(private val repository: SentencePracticeRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(SentencePracticeUiState())
    val state = _state.asStateFlow()
    val input = saved.getStateFlow("input", "")
    val size = saved.getStateFlow("size", "10")
    val scope = saved.getStateFlow("scope", StudyScope.MIXED.name)
    fun setScope(value: StudyScope) { if (controlsEnabled()) saved["scope"] = value.name }
    private val retryAvailability = MutableStateFlow(0)
    val availability = combine(retryAvailability, scope) { _, value -> value }.flatMapLatest { selected ->
        repository.observeAvailable(StudyScope.valueOf(selected)).map<Long, SentencePracticeAvailability> { SentencePracticeAvailability.Ready(it) }
            .onStart { emit(SentencePracticeAvailability.Loading) }
            .catch { if (it is CancellationException) throw it else emit(SentencePracticeAvailability.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SentencePracticeAvailability.Loading)
    private var observer: Job? = null
    private var observingId: Long? = null
    init { reload() }

    private fun controlsEnabled() = !_state.value.busy && !_state.value.loading && _state.value.pending == null
    fun setInput(value: String) { if (controlsEnabled() && _state.value.session?.question?.feedback == null) saved["input"] = value }
    fun setSize(value: String) { if (controlsEnabled()) saved["size"] = value }
    fun retryAvailable() { retryAvailability.update { it + 1 } }

    fun reload(force: Boolean = false) {
        if (_state.value.busy) return
        observer?.cancel(); observingId = null
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val restored = repository.restore(saved.get<Long>("sessionId"))
                if (restored != null && (force || saved.get<Boolean>("setupRequested") != true || restored.active)) {
                    accept(restored)
                } else _state.update { it.copy(session = null, loading = false, pending = null, hasHistory = restored != null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(loading = false, error = SentenceError.STORAGE_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun newSession() {
        if (!controlsEnabled() || _state.value.session?.let { it.active || it.question != null } == true) return
        observer?.cancel(); observer = null; observingId = null
        saved["sessionId"] = null
        saved["setupRequested"] = true
        _state.value = SentencePracticeUiState(loading = false, hasHistory = _state.value.hasHistory)
    }
    fun start() {
        if (!controlsEnabled() || _state.value.session?.let { it.active || it.question != null } == true) return
        val requested = size.value.toIntOrNull()
        if (requested == null || requested !in 1..100) { _state.update { it.copy(error = SentenceError.INVALID_SETTINGS) }; return }
        perform(SentencePracticeAction.Start(UUID.randomUUID().toString(), requested, StudyScope.valueOf(scope.value)))
    }
    // Immutable rendered IDs/text prevent a delayed callback from submitting to the next question.
    fun submit(session: Long, question: Long, submitted: String) = perform(SentencePracticeAction.Submit(session, question, submitted))
    fun selfRate(session: Long, question: Long, rating: SentenceRating) = perform(SentencePracticeAction.SelfRate(session, question, rating))
    fun next(session: Long, question: Long) = perform(SentencePracticeAction.Next(session, question))
    fun complete(session: Long) = perform(SentencePracticeAction.Complete(session))
    fun retry() { val pending = _state.value.pending; if (pending != null) perform(pending) else reload(force = true) }

    private fun perform(action: SentencePracticeAction) {
        val previous = _state.value
        if (previous.loading || previous.busy || (previous.pending != null && previous.pending != action)) return
        _state.update { it.copy(busy = true, pending = action, error = null) }
        viewModelScope.launch {
            try {
                val updated = when (action) {
                    is SentencePracticeAction.Start -> repository.start(action.requestId, action.size, action.scope)
                    is SentencePracticeAction.Submit -> repository.submit(action.session, action.question, action.submitted)
                    is SentencePracticeAction.SelfRate -> repository.selfRate(action.session, action.question, action.rating)
                    is SentencePracticeAction.Next -> repository.next(action.session, action.question)
                    is SentencePracticeAction.Complete -> repository.complete(action.session)
                }
                accept(updated)
            } catch (e: CancellationException) { throw e }
            catch (e: SentenceException) {
                _state.update { it.copy(error = e.code, pending = null) }
                val id = previous.session?.id
                if (id != null) try {
                    accept(repository.get(id), clearError = false)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Preserve the last visible state and explicit error. */ }
            } catch (_: Exception) {
                _state.update { it.copy(error = SentenceError.STORAGE_FAILED) } // Retry the exact same IDs/payload.
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    private fun accept(session: SentencePracticeState, clearError: Boolean = true) {
        syncInput(session)
        saved["sessionId"] = session.id
        saved["setupRequested"] = false
        _state.update { it.copy(session = session, loading = false, hasHistory = true, pending = null,
            error = if (clearError) null else it.error) }
        if (observingId == session.id) return
        observer?.cancel(); observingId = session.id
        observer = viewModelScope.launch {
            repository.observe(session.id).catch { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(error = SentenceError.STORAGE_FAILED) }
            }.collect { incoming ->
                if (incoming == null) _state.update { it.copy(error = SentenceError.SESSION_NOT_FOUND) }
                else _state.update { previous ->
                    val current = previous.session
                    // A persisted revision orders submit/acknowledge/close even when position is unchanged.
                    if (current != null && current.id == incoming.id && incoming.revision < current.revision) previous
                    else { syncInput(incoming); previous.copy(session = incoming, loading = false, hasHistory = true) }
                }
            }
        }
    }
    private fun syncInput(session: SentencePracticeState) {
        val id = session.question?.id
        if (saved.get<Long>("inputQuestion") != id) {
            saved["inputQuestion"] = id
            saved["input"] = session.question?.feedback?.submitted.orEmpty()
        } else session.question?.feedback?.let { saved["input"] = it.submitted }
    }
}
