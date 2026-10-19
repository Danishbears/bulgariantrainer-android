package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.flashcards.*
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

sealed interface FlashcardAvailability {
    data object Loading : FlashcardAvailability
    data class Ready(val count: Long) : FlashcardAvailability
    data object Failed : FlashcardAvailability
}
sealed interface FlashcardAction {
    data class Start(val requestId: String, val settings: FlashcardSettings) : FlashcardAction
    data class Reveal(val session: Long, val card: Long) : FlashcardAction
    data class Rate(val session: Long, val card: Long, val known: Boolean) : FlashcardAction
    data class Complete(val session: Long) : FlashcardAction
}
data class FlashcardUiState(
    val loading: Boolean = true, val busy: Boolean = false, val session: FlashcardState? = null,
    val error: FlashcardError? = null, val pending: FlashcardAction? = null, val hasHistory: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardViewModel(private val repository: FlashcardRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(FlashcardUiState())
    val state = _state.asStateFlow()
    val size = saved.getStateFlow("size", "20")
    val direction = saved.getStateFlow("direction", FlashcardDirection.BG_TO_RU.name)
    private val retryAvailability = MutableStateFlow(0)
    val availability = retryAvailability.flatMapLatest {
        repository.observeAvailable().map<Long, FlashcardAvailability> { FlashcardAvailability.Ready(it) }
            .onStart { emit(FlashcardAvailability.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FlashcardAvailability.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlashcardAvailability.Loading)
    private var observer: Job? = null
    private var observingId: Long? = null
    init { reload() }

    private fun controlsEnabled() = !_state.value.busy && !_state.value.loading && _state.value.pending == null
    fun setSize(value: String) { if (controlsEnabled()) saved["size"] = value }
    fun setDirection(value: FlashcardDirection) { if (controlsEnabled()) saved["direction"] = value.name }
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
            catch (_: Exception) { _state.update { it.copy(loading = false, error = FlashcardError.STORAGE_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun newSession() {
        if (!controlsEnabled() || _state.value.session?.active == true) return
        observer?.cancel(); observer = null; observingId = null
        saved["sessionId"] = null
        saved["setupRequested"] = true
        _state.value = FlashcardUiState(loading = false, hasHistory = _state.value.hasHistory)
    }
    fun start() {
        if (!controlsEnabled() || _state.value.session?.active == true) return
        val size = size.value.toIntOrNull()
        if (size == null || size !in 1..100) { _state.update { it.copy(error = FlashcardError.INVALID_SETTINGS) }; return }
        perform(FlashcardAction.Start(UUID.randomUUID().toString(), FlashcardSettings(size, FlashcardDirection.valueOf(direction.value))))
    }
    // IDs come from the rendered card, so delayed callbacks can never rate the next card by accident.
    fun reveal(session: Long, card: Long) = perform(FlashcardAction.Reveal(session, card))
    fun rate(session: Long, card: Long, known: Boolean) = perform(FlashcardAction.Rate(session, card, known))
    fun complete(session: Long) = perform(FlashcardAction.Complete(session))
    fun retry() { val pending = _state.value.pending; if (pending != null) perform(pending) else reload(force = true) }

    private fun perform(action: FlashcardAction) {
        val previous = _state.value
        if (previous.loading || previous.busy || (previous.pending != null && previous.pending != action)) return
        _state.update { it.copy(busy = true, pending = action, error = null) }
        viewModelScope.launch {
            try {
                val updated = when (action) {
                    is FlashcardAction.Start -> repository.start(action.requestId, action.settings)
                    is FlashcardAction.Reveal -> repository.reveal(action.session, action.card)
                    is FlashcardAction.Rate -> repository.rate(action.session, action.card, action.known)
                    is FlashcardAction.Complete -> repository.complete(action.session)
                }
                accept(updated)
            } catch (e: CancellationException) { throw e }
            catch (e: FlashcardException) {
                _state.update { it.copy(error = e.code, pending = null) }
                val id = previous.session?.id
                if (id != null) try {
                    accept(repository.get(id), clearError = false)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Preserve the last visible state and explicit error. */ }
            } catch (_: Exception) {
                _state.update { it.copy(error = FlashcardError.STORAGE_FAILED) } // Retry the exact same IDs/payload.
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    private fun accept(session: FlashcardState, clearError: Boolean = true) {
        saved["sessionId"] = session.id
        saved["setupRequested"] = false
        _state.update { it.copy(session = session, loading = false, hasHistory = true, pending = null,
            error = if (clearError) null else it.error) }
        if (observingId == session.id) return
        observer?.cancel(); observingId = session.id
        observer = viewModelScope.launch {
            repository.observe(session.id).catch { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(error = FlashcardError.STORAGE_FAILED) }
            }.collect { incoming ->
                if (incoming == null) _state.update { it.copy(error = FlashcardError.SESSION_NOT_FOUND) }
                else _state.update { previous ->
                    val current = previous.session
                    // Ignore an older queued observation; position, closed status and reveal are monotonic.
                    val older = current != null && current.id == incoming.id && (incoming.currentPosition < current.currentPosition ||
                        (!current.active && incoming.active) || (current.question?.id == incoming.question?.id &&
                        current.question?.revealed == true && incoming.question?.revealed == false))
                    if (older) previous else previous.copy(session = incoming, loading = false, hasHistory = true)
                }
            }
        }
    }
}
