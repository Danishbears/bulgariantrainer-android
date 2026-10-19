package com.danishbears.bulgariantrainer.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.transfer.ContentKind
import com.danishbears.bulgariantrainer.transfer.ContentTransferRepository
import com.danishbears.bulgariantrainer.transfer.DocumentGateway
import com.danishbears.bulgariantrainer.transfer.ImportAction
import com.danishbears.bulgariantrainer.transfer.ImportPreview
import com.danishbears.bulgariantrainer.transfer.ImportRow
import com.danishbears.bulgariantrainer.transfer.ImportSummary
import com.danishbears.bulgariantrainer.transfer.RowStatus
import com.danishbears.bulgariantrainer.transfer.TransferError
import com.danishbears.bulgariantrainer.transfer.TransferException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransferState(
    val busy: Boolean = false, val preview: ImportPreview? = null, val summary: ImportSummary? = null,
    val error: TransferError? = null, val errorRow: Int? = null, val exported: Long? = null,
    val previewLost: Boolean = false, val editing: ImportRow? = null, val showEditErrors: Boolean = false,
)
class TransferViewModel(
    private val repository: ContentTransferRepository,
    private val documents: DocumentGateway,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(TransferState(previewLost = saved["hasPreview"] ?: false))
    val state = _state.asStateFlow()
    val kind = saved.getStateFlow("kind", ContentKind.WORDS.name)
    val includeArchived = saved.getStateFlow("includeArchived", false)
    val sentenceCount = repository.observeSentenceCount().map<Long, Long?> { it }.catch { if (it is CancellationException) throw it; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setKind(value: ContentKind) { if (!_state.value.busy) saved["kind"] = value.name }
    fun setIncludeArchived(value: Boolean) { if (!_state.value.busy) saved["includeArchived"] = value }
    fun preparePicker() { saved["importKind"] = kind.value }
    fun prepareExport(): String {
        saved["exportKind"] = kind.value
        saved["exportArchived"] = includeArchived.value
        return if (kind.value == ContentKind.WORDS.name) "bulgarian-vocabulary.csv" else "bulgarian-sentences.csv"
    }
    fun read(uri: Uri) = load(ContentKind.valueOf(saved["importKind"] ?: kind.value)) { contentKind -> documents.read(uri, contentKind) }
    fun sample() = load(ContentKind.SENTENCES) { documents.sample() }
    private fun load(kind: ContentKind, read: suspend (ContentKind) -> Pair<String, ByteArray>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, errorRow = null, exported = null) }
        viewModelScope.launch {
            try {
                val (name, bytes) = read(kind)
                val preview = repository.preview(kind, name, bytes)
                saved["hasPreview"] = true
                saved["kind"] = kind.name
                _state.value = TransferState(busy = true, preview = preview)
            } catch (e: CancellationException) { throw e }
            catch (e: TransferException) { _state.update { it.copy(error = e.code, errorRow = e.rowId) } }
            catch (_: Exception) { _state.update { it.copy(error = TransferError.READ_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun select(id: Int, value: Boolean) = updateRow(id) { it.copy(selected = value) }
    fun action(id: Int, value: ImportAction) = updateRow(id) { it.copy(action = value) }
    private fun updateRow(id: Int, change: (ImportRow) -> ImportRow) {
        if (_state.value.busy || _state.value.summary != null) return
        _state.update { state -> state.copy(error = null, errorRow = null,
            preview = state.preview?.let { p -> p.copy(rows = p.rows.map { if (it.rowId == id) change(it) else it }) }) }
    }
    fun selectReady(clear: Boolean = false) {
        if (_state.value.busy || _state.value.summary != null) return
        _state.update { it.copy(error = null, errorRow = null, preview = it.preview?.let { p -> p.copy(rows = p.rows.map { row ->
            row.copy(selected = !clear && row.status == RowStatus.READY && row.issues().isEmpty())
        }) }) }
    }
    /** An explicit bulk choice, followed by the same final confirmation dialog as individual append choices. */
    fun combineWords() {
        if (_state.value.busy || _state.value.summary != null || _state.value.preview?.kind != ContentKind.WORDS) return
        _state.update { it.copy(error = null, errorRow = null, preview = it.preview?.let { p -> p.copy(rows = p.rows.map { row ->
            val valid = row.selectable && (row.status != RowStatus.INVALID || row.edited) && row.issues().isEmpty()
            row.copy(selected = valid, action = if (row.existingId != null || row.duplicateOf != null) ImportAction.APPEND else ImportAction.CREATE)
        }) }) }
    }
    fun beginEdit(id: Int) {
        if (_state.value.busy || _state.value.summary != null) return
        _state.update { it.copy(editing = it.preview?.rows?.find { row -> row.rowId == id && row.selectable }, showEditErrors = false) }
    }
    fun changeDraft(change: (ImportRow) -> ImportRow) {
        if (!_state.value.busy) _state.update { it.copy(editing = it.editing?.let(change)) }
    }
    fun cancelEdit() { if (!_state.value.busy) _state.update { it.copy(editing = null, showEditErrors = false) } }
    fun applyEdit() {
        val row = _state.value.editing ?: return
        if (row.issues().isNotEmpty()) { _state.update { it.copy(showEditErrors = true) }; return }
        edit(row)
    }
    private fun edit(row: ImportRow) {
        val current = _state.value
        if (current.busy || current.summary != null || current.preview == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val changed = current.preview.copy(rows = current.preview.rows.map {
                    if (it.rowId == row.rowId) it.copy(word = row.word, sentence = row.sentence, edited = true, action = ImportAction.CREATE) else it
                })
                val refreshed = repository.refresh(changed)
                _state.update { it.copy(preview = refreshed, editing = null, showEditErrors = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = TransferError.STORAGE_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun confirm() {
        val current = _state.value
        val preview = current.preview ?: return
        if (current.busy || current.summary != null) return
        _state.update { it.copy(busy = true, error = null, errorRow = null, exported = null) }
        viewModelScope.launch {
            try {
                val summary = repository.confirm(preview)
                saved["hasPreview"] = false
                _state.update { it.copy(summary = summary) }
            } catch (e: CancellationException) { throw e }
            catch (e: TransferException) { _state.update { it.copy(error = e.code, errorRow = e.rowId) } }
            catch (_: Exception) { _state.update { it.copy(error = TransferError.STORAGE_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun export(uri: Uri) {
        if (_state.value.busy) return
        val exportKind = ContentKind.valueOf(saved["exportKind"] ?: kind.value)
        val archived = saved["exportArchived"] ?: includeArchived.value
        _state.update { it.copy(busy = true, error = null, exported = null) }
        viewModelScope.launch {
            try {
                val count = documents.write(uri, repository, exportKind, archived)
                _state.update { it.copy(exported = count) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = TransferError.WRITE_FAILED) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
