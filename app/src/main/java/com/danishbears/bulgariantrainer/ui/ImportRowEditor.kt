package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.data.TranslationInput
import com.danishbears.bulgariantrainer.transfer.ImportRow
import com.danishbears.bulgariantrainer.transfer.SentenceDraft

@Composable
fun ImportRowEditor(row: ImportRow, busy: Boolean, showErrors: Boolean,
    onChange: ((ImportRow) -> ImportRow) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    fun sentence(change: (SentenceDraft) -> SentenceDraft) = onChange { it.copy(sentence = it.sentence?.let(change)) }
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(.95f).fillMaxHeight(.92f).imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.edit_preview), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.source_lines, row.firstLine, row.lastLine), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.word?.let { word ->
                        item { ImportField(word.bulgarianText, { text -> onChange { it.copy(word = word.copy(bulgarianText = text)) } },
                            stringResource(R.string.bulgarian_text), 255, !busy, showErrors, required = true) }
                        item { Text(stringResource(R.string.russian_translations), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.translation_hint), style = MaterialTheme.typography.bodySmall) }
                        itemsIndexed(word.translations, key = { index, _ -> "translation-$index" }) { index, translation ->
                            Column {
                                ImportField(translation.russianText, { text -> onChange {
                                    it.copy(word = word.copy(translations = word.translations.mapIndexed { i, t -> if (i == index) t.copy(russianText = text) else t }))
                                } }, stringResource(R.string.translation_label, index + 1), 255, !busy, showErrors, required = true)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val label = stringResource(R.string.primary_translation)
                                    RadioButton(selected = translation.isPrimary, enabled = !busy, onClick = { onChange {
                                        it.copy(word = word.copy(translations = word.translations.mapIndexed { i, t -> t.copy(isPrimary = i == index) }))
                                    } }, modifier = Modifier.semantics { contentDescription = "$label ${index + 1}" })
                                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                    TextButton(enabled = !busy && word.translations.size > 1, onClick = { onChange {
                                        val remaining = word.translations.filterIndexed { i, _ -> i != index }
                                        it.copy(word = word.copy(translations = if (remaining.none { t -> t.isPrimary }) remaining.mapIndexed { i, t -> t.copy(isPrimary = i == 0) } else remaining))
                                    } }) { Text(stringResource(R.string.remove_short)) }
                                }
                            }
                        }
                        item { OutlinedButton(enabled = !busy && word.translations.size < 50, onClick = { onChange {
                            it.copy(word = word.copy(translations = word.translations + TranslationInput("")))
                        } }) { Text(stringResource(R.string.add_translation)) } }
                        item { ImportField(word.partOfSpeech.orEmpty(), { text -> onChange { it.copy(word = word.copy(partOfSpeech = text)) } },
                            stringResource(R.string.part_of_speech), 100, !busy, showErrors) }
                    }
                    row.sentence?.let { draft ->
                        item { ImportField(draft.sourceId, { text -> sentence { it.copy(sourceId = text) } }, stringResource(R.string.source_id), null, !busy, showErrors, true) }
                        item { ImportField(draft.russian, { text -> sentence { it.copy(russian = text) } }, stringResource(R.string.russian_source), 4000, !busy, showErrors, true) }
                        item { ImportField(draft.bulgarian, { text -> sentence { it.copy(bulgarian = text) } }, stringResource(R.string.bulgarian_reference), 4000, !busy, showErrors, true) }
                        item { Text(stringResource(R.string.approved_alternatives), style = MaterialTheme.typography.titleMedium) }
                        draft.unresolvedAlternatives?.let { original -> item {
                            FailureMessage(stringResource(R.string.invalid_alternatives))
                            Text(original, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !busy, onClick = { sentence { it.copy(unresolvedAlternatives = null, alternatives = emptyList()) } }) {
                                Text(stringResource(R.string.replace_alternatives))
                            }
                        } }
                        itemsIndexed(draft.alternatives, key = { index, _ -> "alternative-$index" }) { index, value ->
                            Column {
                                ImportField(value, { text -> sentence { it.copy(alternatives = it.alternatives.mapIndexed { i, old -> if (i == index) text else old }) } },
                                    stringResource(R.string.alternative_label, index + 1), 4000, !busy, showErrors, true)
                                TextButton(enabled = !busy, onClick = { sentence { it.copy(alternatives = it.alternatives.filterIndexed { i, _ -> i != index }) } }) {
                                    Text(stringResource(R.string.remove_short))
                                }
                            }
                        }
                        item { OutlinedButton(enabled = !busy && draft.alternatives.size < 50 && draft.unresolvedAlternatives == null,
                            onClick = { sentence { it.copy(alternatives = it.alternatives + "") } }) { Text(stringResource(R.string.add_alternative)) } }
                        item { ImportField(draft.explanation, { text -> sentence { it.copy(explanation = text) } }, stringResource(R.string.explanation), 4000, !busy, showErrors) }
                        item { ImportField(draft.difficulty, { text -> sentence { it.copy(difficulty = text) } }, stringResource(R.string.difficulty), null, !busy, showErrors, true) }
                        item { ImportField(draft.topic, { text -> sentence { it.copy(topic = text) } }, stringResource(R.string.topic), 255, !busy, showErrors) }
                    }
                }
                if (showErrors) row.issues().forEach { Text(issueLabel(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !busy, onClick = onCancel) { Text(stringResource(R.string.cancel_edit)) }
                    Button(enabled = !busy, onClick = onSave) { Text(stringResource(R.string.apply_corrections)) }
                }
            }
        }
    }
}

@Composable
private fun ImportField(value: String, onChange: (String) -> Unit, label: String, max: Int?, enabled: Boolean,
    errors: Boolean, required: Boolean = false) {
    val invalid = (max != null && value.length > max) || (errors && required && value.isBlank())
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, enabled = enabled, minLines = if (max == 4000) 2 else 1, maxLines = if (max == 4000) 6 else 3,
        isError = invalid, supportingText = { Text(when {
            (max != null && value.length > max) -> stringResource(R.string.length_error, requireNotNull(max))
            errors && required && value.isBlank() -> stringResource(R.string.required_error)
            else -> if (max == null) value.length.toString() else "${value.length}/$max"
        }) })
}
