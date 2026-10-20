package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.pronunciation.PronunciationPolicy
import com.danishbears.bulgariantrainer.data.VocabularyWord

@Composable
fun VocabularyScreen(model: VocabularyViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val controls by model.controls.collectAsStateWithLifecycle()
    PronunciationBoundary("vocabulary:${controls.query}:${controls.archived}")
    val busy by model.busy.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val lifecyclePages = remember(model, owner) { model.words.flowWithLifecycle(owner.lifecycle) }
    val words = lifecyclePages.collectAsLazyPagingItems()
    val scroll = rememberLazyListState()
    LaunchedEffect(controls.query, controls.archived) { scroll.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dictionary), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_word)) }
            }
            OutlinedTextField(value = controls.query, onValueChange = model::setQuery,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.search_label)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (controls.query.isNotEmpty()) IconButton(onClick = { model.setQuery("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                } }, isError = controls.queryTooLong,
                supportingText = { if (controls.queryTooLong) Text(stringResource(R.string.search_too_long)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !controls.archived, onClick = { model.showArchived(false) },
                    label = { Text(stringResource(R.string.active_filter)) })
                FilterChip(selected = controls.archived, onClick = { model.showArchived(true) },
                    label = { Text(stringResource(R.string.archive_filter)) })
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "pronunciation-settings") { PronunciationSettings() }
            if (!controls.queryTooLong) {
                when {
                    words.loadState.refresh is LoadState.Loading -> item(key = "loading") { LoadingRow() }
                    words.loadState.refresh is LoadState.Error -> item(key = "error") {
                        FailureMessage(stringResource(R.string.load_error), words::retry)
                    }
                    words.itemCount == 0 -> item(key = "empty") {
                        val searching = controls.query.isNotBlank()
                        Text(stringResource(when {
                            searching -> R.string.nothing_found
                            controls.archived -> R.string.archive_empty
                            else -> R.string.active_empty
                        }), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(when {
                            searching -> R.string.nothing_found_hint
                            controls.archived -> R.string.archive_empty_hint
                            else -> R.string.active_empty_hint
                        }), modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(count = words.itemCount, key = words.itemKey { it.id },
                    contentType = words.itemContentType { "word" }) { index ->
                    words[index]?.let { word ->
                        WordCard(word, word.id in busy, onEdit = { onEdit(word.id) },
                            onArchive = { model.archive(word.id, !word.archived) })
                    }
                }
                when (words.loadState.append) {
                    is LoadState.Loading -> item(key = "append_loading") { LoadingRow() }
                    is LoadState.Error -> item(key = "append_error") {
                        FailureMessage(stringResource(R.string.load_error), words::retry)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(stringResource(R.string.loading))
    }
}

@Composable
private fun WordCard(word: VocabularyWord, busy: Boolean, onEdit: () -> Unit, onArchive: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word.bulgarianText, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            PronunciationPlayback(PronunciationPolicy.vocabulary(word.id, word.bulgarianText))
            val metadata = listOfNotNull(word.partOfSpeech, word.grammaticalGender).joinToString(" · ")
            if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.labelLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Bounded previews keep list composition cheap even with 50 accepted translations per word.
            word.translations.take(3).forEach {
                Text(it.russianText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = if (it.isPrimary) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
            }
            if (word.translations.size > 3) Text(stringResource(R.string.more_translations, word.translations.size - 3),
                style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onArchive, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (word.archived) R.string.restore_word else R.string.archive_word))
                }
            }
        }
    }
}
