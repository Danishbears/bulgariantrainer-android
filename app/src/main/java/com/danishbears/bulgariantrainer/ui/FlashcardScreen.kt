package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.pronunciation.PronunciationPolicy
import com.danishbears.bulgariantrainer.flashcards.*

@Composable
fun FlashcardScreen(model: FlashcardViewModel, onDictionary: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope by model.scope.collectAsStateWithLifecycle()
    ReviewRefreshOnResume(model::retryAvailable)
    val available by model.availability.collectAsStateWithLifecycle()
    val size by model.size.collectAsStateWithLifecycle()
    val direction by model.direction.collectAsStateWithLifecycle()
    var endConfirmation by rememberSaveable { mutableStateOf(false) }
    val session = state.session
    PronunciationBoundary("FlashcardScreen.kt:${session?.id}:${session?.status}:${session?.question?.id}")
    val scroll = rememberLazyListState()
    LaunchedEffect(session?.id, session?.question?.id, session?.status) { scroll.scrollToItem(0) }
    val enabled = !state.busy && !state.loading && state.pending == null
    BackHandler(enabled = state.busy) { /* Committed Room state, rather than a navigation callback, owns progress. */ }
    if (endConfirmation && session?.active == true) AlertDialog(onDismissRequest = { endConfirmation = false },
        title = { Text(stringResource(R.string.flash_end_title)) },
        text = { Text(stringResource(R.string.flash_end_hint)) },
        confirmButton = { TextButton(enabled = enabled, onClick = { endConfirmation = false; model.complete(session.id) }) {
            Text(stringResource(R.string.flash_end))
        } }, dismissButton = { TextButton(onClick = { endConfirmation = false }) { Text(stringResource(R.string.flash_continue)) } })

    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "title") {
                Text(stringResource(R.string.flashcards), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.flash_description), modifier = Modifier.padding(top = 8.dp))
                PronunciationSettings()
            }
            state.error?.let { error -> item(key = "error") { FailureMessage(flashcardErrorLabel(error), model::retry) } }
            if (state.pending != null && !state.busy) item(key = "pending") {
                Text(stringResource(R.string.flash_pending))
                OutlinedButton(onClick = { model.reload(force = true) }) { Text(stringResource(R.string.flash_refresh)) }
            }
            when {
                state.loading -> item(key = "loading") { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
                session == null -> {
                    item(key = "setup") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReviewScopeSelector(scope, model::setScope, enabled)
                            Text(stringResource(R.string.flash_settings), style = MaterialTheme.typography.titleLarge)
                            FlashcardDirection.entries.forEach { option -> FilterChip(selected = direction == option.name,
                                enabled = enabled, onClick = { model.setDirection(option) }, label = { Text(flashcardDirectionLabel(option)) }) }
                            val parsedSize = size.toIntOrNull()
                            OutlinedTextField(size, model::setSize, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.flash_size)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = parsedSize == null || parsedSize !in 1..100,
                                supportingText = { if (parsedSize == null || parsedSize !in 1..100) Text(stringResource(R.string.flash_size_error)) })
                            when (val count = available) {
                                FlashcardAvailability.Loading -> Text(stringResource(R.string.loading))
                                FlashcardAvailability.Failed -> FailureMessage(stringResource(R.string.load_error), model::retryAvailable)
                                is FlashcardAvailability.Ready -> {
                                    Text(stringResource(R.string.flash_available, count.count))
                                    if (count.count == 0L) {
                                        Text(stringResource(R.string.flash_empty))
                                        OutlinedButton(onClick = onDictionary, enabled = enabled) { Text(stringResource(R.string.open_dictionary)) }
                                    } else if (parsedSize != null && count.count < parsedSize) Text(stringResource(R.string.flash_shorter, count.count))
                                }
                            }
                            Button(onClick = model::start, enabled = enabled && parsedSize != null && parsedSize in 1..100 &&
                                (available as? FlashcardAvailability.Ready)?.count?.let { it > 0 } == true,
                                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_start)) }
                            if (state.hasHistory) TextButton(onClick = { model.reload(force = true) }, enabled = enabled) {
                                Text(stringResource(R.string.flash_last_session))
                            }
                        }
                    }
                }
                session.active -> {
                    item(key = "progress") {
                        Text(stringResource(R.string.flash_progress, session.currentPosition, session.total, session.answered))
                        LinearProgressIndicator(progress = { session.answered.toFloat() / session.total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text(stringResource(R.string.flash_short_session, session.total, session.settings.size),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    session.question?.let { question ->
                        item(key = "card-${question.id}") {
                            Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(flashcardDirectionLabel(if (question.direction == CardDirection.BG_TO_RU) FlashcardDirection.BG_TO_RU else FlashcardDirection.RU_TO_BG),
                                        style = MaterialTheme.typography.labelLarge)
                                    question.prompt.forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
                                    PronunciationPlayback(PronunciationPolicy.flashcard(session.id, question))
                                    if (question.revealed) {
                                        Text(stringResource(R.string.flash_answer), style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary)
                                        question.answer.forEach { Text(it, style = MaterialTheme.typography.titleLarge) }
                                    } else Text(stringResource(R.string.flash_hidden), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        item(key = "rating-${question.id}") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!question.revealed) Button(onClick = { model.reveal(session.id, question.id) }, enabled = enabled,
                                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_reveal)) }
                                else {
                                    Text(stringResource(R.string.flash_rate_hint))
                                    Button(onClick = { model.rate(session.id, question.id, true) }, enabled = enabled,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_know)) }
                                    OutlinedButton(onClick = { model.rate(session.id, question.id, false) }, enabled = enabled,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_dont_know)) }
                                }
                            }
                        }
                    }
                    item(key = "end") {
                        Text(stringResource(R.string.flash_saved_hint), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { endConfirmation = true }, enabled = enabled) { Text(stringResource(R.string.flash_end)) }
                    }
                }
                else -> {
                    item(key = "summary") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (session.status == FlashcardStatus.ABANDONED) R.string.flash_abandoned else R.string.flash_completed),
                                style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.flash_summary_counts, session.answered, session.total, session.known, session.unknown, session.remaining))
                            Text(stringResource(R.string.flash_summary_hint))
                            Button(onClick = model::newSession, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_new_session)) }
                        }
                    }
                    items(session.results.size, key = { "result-${session.results[it].cardId}" }, contentType = { "flashcard-result" }) { index ->
                        val result = session.results[index]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${result.position}. ${result.bulgarian}", style = MaterialTheme.typography.titleMedium)
                                result.russian.forEach { Text(it) }
                                Text(flashcardDirectionLabel(if (result.direction == CardDirection.BG_TO_RU) FlashcardDirection.BG_TO_RU else FlashcardDirection.RU_TO_BG), style = MaterialTheme.typography.labelMedium)
                                Text(stringResource(when (result.result) { CardResult.CORRECT -> R.string.flash_know; CardResult.INCORRECT -> R.string.flash_dont_know; CardResult.SKIPPED -> R.string.flash_unrated }),
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun flashcardDirectionLabel(direction: FlashcardDirection): String = stringResource(when (direction) {
    FlashcardDirection.BG_TO_RU -> R.string.flash_bg_ru
    FlashcardDirection.RU_TO_BG -> R.string.flash_ru_bg
    FlashcardDirection.MIXED -> R.string.flash_mixed
})
@Composable
private fun flashcardErrorLabel(error: FlashcardError): String = stringResource(when (error) {
    FlashcardError.INVALID_SETTINGS -> R.string.flash_size_error
    FlashcardError.EMPTY_REVIEW_QUEUE -> R.string.review_empty_due
    FlashcardError.EMPTY_NEW_QUEUE -> R.string.review_empty_new
    FlashcardError.EMPTY_DICTIONARY -> R.string.flash_empty
    FlashcardError.SESSION_NOT_FOUND -> R.string.flash_session_missing
    FlashcardError.SESSION_CLOSED -> R.string.flash_session_closed
    FlashcardError.STALE_CARD -> R.string.flash_stale
    FlashcardError.ANSWER_HIDDEN -> R.string.flash_hidden_error
    FlashcardError.RATING_CONFLICT -> R.string.flash_rating_conflict
    FlashcardError.START_REQUEST_CHANGED -> R.string.flash_start_changed
    FlashcardError.RESULTS_UNAVAILABLE -> R.string.flash_results_unavailable
    FlashcardError.STORAGE_FAILED -> R.string.flash_storage_failed
})
