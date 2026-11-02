package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.transfer.ContentKind
import com.danishbears.bulgariantrainer.transfer.ImportAction
import com.danishbears.bulgariantrainer.transfer.ImportRow
import com.danishbears.bulgariantrainer.transfer.RowStatus

@Composable
fun TransferScreen(model: TransferViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val kindName by model.kind.collectAsStateWithLifecycle()
    val includeArchived by model.includeArchived.collectAsStateWithLifecycle()
    val count by model.sentenceCount.collectAsStateWithLifecycle()
    val kind = ContentKind.valueOf(kindName)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.read(it) }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { if (it != null) model.export(it) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var replacement by rememberSaveable { mutableStateOf(false) }
    var replacementForSample by rememberSaveable { mutableStateOf(false) }
    fun chooseFile() {
        model.preparePicker()
        picker.launch(arrayOf("*/*"))
    }
    BackHandler(enabled = state.busy) { /* Keep an in-flight transaction on this screen. */ }
    state.editing?.let { ImportRowEditor(it, state.busy, state.showEditErrors, model::changeDraft, model::cancelEdit, model::applyEdit) }
    if (replacement) AlertDialog(onDismissRequest = { replacement = false },
        title = { Text(stringResource(R.string.replace_preview)) }, text = { Text(stringResource(R.string.preview_replace_hint)) },
        confirmButton = { TextButton(onClick = { replacement = false; if (replacementForSample) model.sample() else chooseFile() }) { Text(stringResource(R.string.replace_preview_action)) } },
        dismissButton = { TextButton(onClick = { replacement = false }) { Text(stringResource(R.string.continue_editing)) } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.confirm_import)) },
        text = {
            val rows = state.preview?.rows.orEmpty().filter { it.selected && it.action != ImportAction.SKIP }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.import_selection_summary, rows.size, rows.count { it.action == ImportAction.APPEND }))
                Text(stringResource(if (state.preview?.kind == ContentKind.WORDS) R.string.append_confirmation else R.string.approve_sentences))
                Text(stringResource(R.string.transaction_hint))
            }
        },
        confirmButton = { TextButton(onClick = { confirm = false; model.confirm() }) { Text(stringResource(R.string.save_selected)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.continue_editing)) } })

    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "tools") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.transfer_hint))
                    Text(stringResource(R.string.saved_sentences, count?.toString() ?: "…"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContentKind.entries.forEach { value -> FilterChip(selected = value == kind, enabled = !state.busy,
                            onClick = { model.setKind(value) }, label = { Text(stringResource(if (value == ContentKind.WORDS) R.string.dictionary else R.string.sentences)) }) }
                    }
                    Button(onClick = { if (state.preview != null && state.summary == null) { replacementForSample = false; replacement = true } else chooseFile() }, enabled = !state.busy) {
                        Text(stringResource(R.string.choose_file))
                    }
                    if (kind == ContentKind.SENTENCES) OutlinedButton(onClick = {
                        if (state.preview != null && state.summary == null) { replacementForSample = true; replacement = true } else model.sample()
                    }, enabled = !state.busy) {
                        Text(stringResource(R.string.sample_preview))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeArchived, onCheckedChange = model::setIncludeArchived, enabled = !state.busy)
                        Text(stringResource(R.string.export_archived))
                    }
                    OutlinedButton(onClick = { creator.launch(model.prepareExport()) }, enabled = !state.busy) {
                        Text(stringResource(R.string.export_content))
                    }
                    Text(stringResource(R.string.export_limitations), style = MaterialTheme.typography.bodySmall)
                    if (state.previewLost) Text(stringResource(R.string.preview_lost), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.busy && state.preview == null) item(key = "loading") { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
            state.error?.let { error -> item(key = "error") {
                Column {
                    FailureMessage(transferErrorLabel(error))
                    state.errorRow?.let { Text(stringResource(R.string.error_row, it), color = MaterialTheme.colorScheme.error) }
                }
            } }
            state.exported?.let { exported -> item(key = "exported") { Text(stringResource(R.string.export_success, exported)) } }
            state.summary?.let { summary -> item(key = "summary") {
                Text(stringResource(R.string.import_success, summary.created, summary.translationsAdded, summary.skipped),
                    style = MaterialTheme.typography.titleMedium)
            } }
            state.preview?.let { preview ->
                item(key = "preview_heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.preview_content_type, stringResource(if (preview.kind == ContentKind.WORDS) R.string.dictionary else R.string.sentences)), style = MaterialTheme.typography.labelLarge)
                        Text(preview.filename, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.preview_selection_count,
                            preview.rows.count { it.selected && it.action != ImportAction.SKIP }, preview.rows.size))
                        Text(stringResource(R.string.preview_rules))
                        if (state.summary == null) {
                            Row {
                                TextButton(onClick = { model.selectReady() }, enabled = !state.busy) { Text(stringResource(R.string.select_ready)) }
                                TextButton(onClick = { model.selectReady(clear = true) }, enabled = !state.busy) { Text(stringResource(R.string.deselect_all)) }
                            }
                            if (preview.kind == ContentKind.WORDS) OutlinedButton(onClick = model::combineWords, enabled = !state.busy) {
                                Text(stringResource(R.string.combine_import_words))
                            }
                        }
                    }
                }
                items(preview.rows.size, key = { preview.rows[it].rowId }, contentType = { "import-row" }) { index ->
                    PreviewCard(preview.rows[index], enabled = !state.busy && state.summary == null,
                        onSelect = { model.select(preview.rows[index].rowId, it) },
                        onAction = { model.action(preview.rows[index].rowId, it) },
                        onEdit = { model.beginEdit(preview.rows[index].rowId) })
                }
            }
        }
        val canConfirm = !state.busy && state.summary == null && state.preview?.rows?.any { it.selected && it.action != ImportAction.SKIP } == true
        if (state.preview != null && state.summary == null) Button(onClick = { confirm = true }, enabled = canConfirm,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { Text(stringResource(R.string.confirm_import)) }
    }
}

@Composable
private fun PreviewCard(row: ImportRow, enabled: Boolean, onSelect: (Boolean) -> Unit, onAction: (ImportAction) -> Unit, onEdit: () -> Unit) {
    var showSource by rememberSaveable(row.rowId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.selected, onCheckedChange = onSelect, enabled = enabled && row.selectable)
                Text(stringResource(R.string.source_lines, row.firstLine, row.lastLine), style = MaterialTheme.typography.titleSmall)
            }
            val title = row.word?.bulgarianText ?: row.sentence?.russian
            if (!title.isNullOrEmpty()) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val answer = row.word?.translations?.joinToString("; ") { it.russianText } ?: row.sentence?.bulgarian
            if (!answer.isNullOrEmpty()) Text(answer, maxLines = 3, overflow = TextOverflow.Ellipsis)
            row.warnings.forEach { Text(issueLabel(it), style = MaterialTheme.typography.bodySmall,
                color = if (row.status == RowStatus.INVALID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            row.duplicateOf?.let { Text(stringResource(R.string.duplicate_row, it), style = MaterialTheme.typography.bodySmall) }
            if (row.existingId != null) Text(stringResource(R.string.existing_entry, row.existingId,
                row.existingTranslations.joinToString("; ")), maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (row.edited) Text(stringResource(R.string.corrected_preview), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { showSource = !showSource }) { Text(stringResource(R.string.source_text)) }
            if (showSource) Text(row.source.ifEmpty { stringResource(R.string.empty_source) }, style = MaterialTheme.typography.bodySmall)
            if (row.selectable) {
                if (enabled) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_preview)) }
                    Column {
                        listOf(ImportAction.CREATE, ImportAction.SKIP).plus(if (row.word != null) listOf(ImportAction.APPEND) else emptyList()).forEach { action ->
                            FilterChip(selected = row.action == action, onClick = { onAction(action) }, label = { Text(actionLabel(action)) })
                        }
                    }
                } else Text(actionLabel(row.action), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
