package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.data.FieldIssue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(model: WordEditorViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = {
        if (!state.saving) {
            if (state.dirty) confirmDiscard = true else onClose()
        }
    }
    BackHandler { requestClose() }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_text)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.continue_editing)) } })

    Scaffold(modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(if (model.isNew) R.string.new_word else R.string.edit_word)) },
                windowInsets = WindowInsets(0, 0, 0, 0), navigationIcon = {
                    IconButton(onClick = { requestClose() }, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                })
        },
        bottomBar = {
            if (!state.loading && state.loadFailure == null) Button(onClick = model::save,
                enabled = !state.saving && !state.saved,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (state.saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(if (state.saving) R.string.saving else R.string.save_word))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.loading -> item { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
                state.loadFailure != null -> item {
                    FailureMessage(stringResource(if (state.loadFailure == EditorFailure.NOT_FOUND) R.string.word_missing else R.string.load_error),
                        if (state.loadFailure == EditorFailure.LOAD) model::load else null)
                }
                else -> {
                    val draft = state.draft
                    item(key = "bulgarian") {
                        EditorField(draft.bulgarian, { value -> model.change { it.copy(bulgarian = value) } },
                            R.string.bulgarian_text, state.errors.bulgarian, 255, !state.saving, singleLine = false,
                            duplicate = R.string.word_duplicate)
                    }
                    item(key = "translation_heading") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.russian_translations), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.translation_hint), style = MaterialTheme.typography.bodySmall)
                            if (state.errors.translationGroup != null) Text(stringResource(R.string.translation_group_error),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    itemsIndexed(draft.translations, key = { _, row -> row.key }) { index, row ->
                        Column {
                            EditorField(row.text, { model.translation(row.key, it) }, R.string.translation_label,
                                state.errors.translations[index], 255, !state.saving, labelIndex = index + 1,
                                singleLine = false, duplicate = R.string.translation_duplicate)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val primaryLabel = stringResource(R.string.primary_translation)
                                RadioButton(selected = draft.primary == index,
                                    onClick = { model.change { it.copy(primary = index) } }, enabled = !state.saving,
                                    modifier = Modifier.semantics { contentDescription = "$primaryLabel ${index + 1}" })
                                Text(stringResource(if (draft.primary == index) R.string.primary_translation else R.string.make_primary),
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { model.removeTranslation(index) },
                                    enabled = !state.saving && draft.translations.size > 1) {
                                    Icon(Icons.Default.Close, stringResource(R.string.remove_translation, index + 1))
                                }
                            }
                        }
                    }
                    item(key = "add_translation") {
                        OutlinedButton(onClick = model::addTranslation, enabled = !state.saving && draft.translations.size < 50) {
                            Text(stringResource(R.string.add_translation))
                        }
                    }
                    item(key = "metadata") { Text(stringResource(R.string.metadata), style = MaterialTheme.typography.titleMedium) }
                    item(key = "part") {
                        EditorField(draft.part, { value -> model.change { it.copy(part = value) } }, R.string.part_of_speech,
                            state.errors.part, 100, !state.saving, optional = true)
                    }
                    item(key = "gender") {
                        EditorField(draft.gender, { value -> model.change { it.copy(gender = value) } }, R.string.gender,
                            state.errors.gender, 100, !state.saving, optional = true)
                    }
                    item(key = "notes") {
                        EditorField(draft.notes, { value -> model.change { it.copy(notes = value) } }, R.string.notes,
                            state.errors.notes, 4000, !state.saving, optional = true, singleLine = false)
                    }
                    if (state.saveFailure != null) item(key = "save_error") {
                        FailureMessage(stringResource(if (state.saveFailure == EditorFailure.NOT_FOUND) R.string.word_missing else R.string.save_error))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String, onChange: (String) -> Unit, label: Int, issue: FieldIssue?, limit: Int, enabled: Boolean,
    labelIndex: Int? = null, optional: Boolean = false, singleLine: Boolean = true,
    duplicate: Int = R.string.translation_duplicate,
) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(if (labelIndex == null) stringResource(label) else stringResource(label, labelIndex)) },
        enabled = enabled, singleLine = singleLine, minLines = if (limit == 4000) 3 else 1,
        maxLines = if (singleLine) 1 else if (limit == 4000) 8 else 3, isError = issue != null,
        supportingText = {
            Column {
                when (issue) {
                    FieldIssue.REQUIRED -> Text(stringResource(R.string.required_error))
                    FieldIssue.TOO_LONG -> Text(stringResource(R.string.length_error, limit))
                    FieldIssue.DUPLICATE -> Text(stringResource(duplicate))
                    FieldIssue.PRIMARY -> Text(stringResource(R.string.translation_group_error))
                    null -> if (optional) Text(stringResource(R.string.optional_hint))
                }
                Text("${value.length}/$limit", style = MaterialTheme.typography.labelSmall)
            }
        })
}
