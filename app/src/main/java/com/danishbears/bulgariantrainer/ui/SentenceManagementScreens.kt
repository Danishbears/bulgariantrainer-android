package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.sentences.*

@Composable
fun SentenceListScreen(model: SentenceListViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, onTrain: () -> Unit) {
    val items = model.pages.collectAsLazyPagingItems()
    val archived by model.archived.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sentences), style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onTrain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sentence_practice)) }
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sentence_add)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !archived, enabled = !busy, onClick = { model.showArchive(false) }, label = { Text(stringResource(R.string.sentence_active)) })
                    FilterChip(selected = archived, enabled = !busy, onClick = { model.showArchive(true) }, label = { Text(stringResource(R.string.sentence_archive)) })
                }
            }
        }
        error?.let { item(key = "write-error") { FailureMessage(sentenceErrorLabel(it), model::retry) } }
        when (items.loadState.refresh) {
            is LoadState.Loading -> item(key = "loading") { CircularProgressIndicator() }
            is LoadState.Error -> item(key = "load-error") { FailureMessage(stringResource(R.string.load_error), items::retry) }
            else -> if (items.itemCount == 0) item(key = "empty") { Text(stringResource(R.string.sentence_list_empty)) }
        }
        items(items.itemCount, key = items.itemKey { it.id }, contentType = { "sentence" }) { index ->
            items[index]?.let { sentence ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(sentence.input.russian, style = MaterialTheme.typography.titleMedium)
                        Text(sentence.input.bulgarian)
                        sentence.input.difficulty?.let { Text(stringResource(R.string.sentence_level, it)) }
                        sentence.input.topic?.let { Text(it) }
                        TextButton(onClick = { onEdit(sentence.id) }, enabled = !busy) { Text(stringResource(R.string.sentence_edit)) }
                        OutlinedButton(onClick = { model.archive(sentence.id, !sentence.archived) }, enabled = !busy) {
                            Text(stringResource(if (sentence.archived) R.string.sentence_restore else R.string.sentence_to_archive))
                        }
                    }
                }
            }
        }
        if (items.loadState.append is LoadState.Error) item(key = "append-error") { FailureMessage(stringResource(R.string.load_error), items::retry) }
        if (items.loadState.append is LoadState.Loading) item(key = "append-loading") { CircularProgressIndicator() }
    }
}

@Composable
fun SentenceEditorScreen(model: SentenceEditorViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    val back = { if (state.dirty) discard = true else onClose() }
    BackHandler { if (!state.saving) back() }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.sentence_discard)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.sentence_leave)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.flash_continue)) } })
    val enabled = !state.loading && !state.saving && !state.loadFailed && !state.saved
    val draft = state.draft
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(if (model.isNew) R.string.sentence_add else R.string.sentence_edit), style = MaterialTheme.typography.headlineMedium) }
        item { OutlinedButton(onClick = back, enabled = !state.saving) { Text(stringResource(R.string.sentence_back)) } }
        if (state.loading || state.saving) item { CircularProgressIndicator() }
        state.error?.let { item { FailureMessage(sentenceErrorLabel(it), if (state.loadFailed) model::load else null) } }
        if (!state.loading && !state.loadFailed) {
            item { Text(stringResource(R.string.sentence_editor_hint)) }
            item { OutlinedTextField(draft.russian, { value -> model.change { it.copy(russian = value) } }, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.sentence_russian)) }) }
            item { OutlinedTextField(draft.bulgarian, { value -> model.change { it.copy(bulgarian = value) } }, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.sentence_bulgarian)) }) }
            item { Text(stringResource(R.string.sentence_alternatives), style = MaterialTheme.typography.titleMedium) }
            items(draft.alternatives.size) { index ->
                Column {
                    OutlinedTextField(draft.alternatives[index], { model.alternative(index, it) }, enabled = enabled, minLines = 2,
                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sentence_alternative, index + 1)) })
                    TextButton(onClick = { model.removeAlternative(index) }, enabled = enabled) { Text(stringResource(R.string.sentence_remove_alternative)) }
                }
            }
            item { OutlinedButton(onClick = model::addAlternative, enabled = enabled && draft.alternatives.size < 50) { Text(stringResource(R.string.sentence_add_alternative)) } }
            item { OutlinedTextField(draft.explanation, { value -> model.change { it.copy(explanation = value) } }, enabled = enabled, minLines = 2,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sentence_explanation)) }) }
            item { OutlinedTextField(draft.difficulty, { value -> model.change { it.copy(difficulty = value) } }, enabled = enabled, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.sentence_difficulty)) }) }
            item { OutlinedTextField(draft.topic, { value -> model.change { it.copy(topic = value) } }, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sentence_topic)) }) }
            item { Button(onClick = model::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sentence_save)) } }
        }
    }
}

@Composable
internal fun sentenceErrorLabel(error: SentenceError): String = stringResource(when (error) {
    SentenceError.INVALID_SENTENCE -> R.string.sentence_invalid
    SentenceError.DUPLICATE_SENTENCE -> R.string.sentence_duplicate
    SentenceError.SENTENCE_NOT_FOUND -> R.string.sentence_missing
    SentenceError.INVALID_SETTINGS -> R.string.sentence_size_error
    SentenceError.EMPTY_SENTENCES -> R.string.sentence_empty
    SentenceError.INVALID_ANSWER -> R.string.typing_invalid
    SentenceError.SESSION_NOT_FOUND -> R.string.flash_session_missing
    SentenceError.SESSION_CLOSED -> R.string.flash_session_closed
    SentenceError.STALE_QUESTION -> R.string.typing_stale
    SentenceError.ANSWER_REQUIRED -> R.string.typing_answer_required
    SentenceError.EVALUATION_REQUIRED -> R.string.sentence_evaluation_required
    SentenceError.ANSWER_CONFLICT -> R.string.typing_conflict
    SentenceError.EVALUATION_CONFLICT -> R.string.sentence_evaluation_conflict
    SentenceError.START_REQUEST_CHANGED -> R.string.flash_start_changed
    SentenceError.RESULTS_UNAVAILABLE -> R.string.flash_results_unavailable
    SentenceError.STORAGE_FAILED -> R.string.sentence_storage_failed
})
