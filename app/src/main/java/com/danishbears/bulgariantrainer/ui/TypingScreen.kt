package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.typing.*

@Composable
fun TypingScreen(model: TypingViewModel, onDictionary: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val available by model.availability.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    val session = state.session
    val enabled = !state.busy && !state.loading && state.pending == null
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(session?.id, session?.question?.id) { scroll.scrollToItem(0) }
    if (confirmEnd && session?.active == true) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.flash_end_title)) },
        text = { Text(stringResource(R.string.typing_end_hint)) },
        confirmButton = { TextButton(enabled = enabled, onClick = { confirmEnd = false; model.complete(session.id) }) { Text(stringResource(R.string.flash_end)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.flash_continue)) } })
    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "title") {
                Text(stringResource(R.string.typing), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.typing_description), Modifier.padding(top = 8.dp))
            }
            state.error?.let { error -> item(key = "error") { FailureMessage(typingErrorLabel(error), model::retry) } }
            if (state.pending != null && !state.busy) item(key = "pending") {
                Text(stringResource(R.string.typing_pending))
                OutlinedButton(onClick = { model.reload(force = true) }) { Text(stringResource(R.string.flash_refresh)) }
            }
            when {
                state.loading -> item(key = "loading") { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
                session == null -> item(key = "setup") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (val count = available) {
                            TypingAvailability.Loading -> Text(stringResource(R.string.loading))
                            TypingAvailability.Failed -> FailureMessage(stringResource(R.string.load_error), model::retryAvailable)
                            is TypingAvailability.Ready -> {
                                Text(stringResource(R.string.flash_available, count.count))
                                when {
                                    count.count == 0L -> {
                                        Text(stringResource(R.string.flash_empty))
                                        OutlinedButton(onClick = onDictionary, enabled = enabled) { Text(stringResource(R.string.open_dictionary)) }
                                    }
                                    count.count < 30L -> {
                                        Text(stringResource(R.string.typing_short_hint, count.count, (count.count + 1) / 2, count.count / 2))
                                        Button(onClick = { model.start(allowShorter = true) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.typing_start_short, count.count))
                                        }
                                    }
                                    else -> {
                                        Text(stringResource(R.string.typing_full_hint))
                                        Button(onClick = { model.start() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.typing_start)) }
                                    }
                                }
                            }
                        }
                        if (state.hasHistory) TextButton(onClick = { model.reload(force = true) }, enabled = enabled) { Text(stringResource(R.string.flash_last_session)) }
                    }
                }
                session.question != null -> {
                    val question = session.question
                    val feedback = question.feedback
                    item(key = "progress") {
                        Text(stringResource(R.string.typing_progress, question.position, session.total, session.answered))
                        LinearProgressIndicator(progress = { session.answered.toFloat() / session.total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    item(key = "question-${question.id}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(typingDirectionLabel(question.direction), style = MaterialTheme.typography.labelLarge)
                                question.prompt.forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
                                if (feedback == null) {
                                    val valid = input.length <= 4000 && input.isNotBlank()
                                    OutlinedTextField(value = input, onValueChange = model::setInput, enabled = enabled, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.typing_answer)) },
                                        isError = input.length > 4000,
                                        supportingText = { if (input.length > 4000) Text(stringResource(R.string.typing_invalid)) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { if (enabled && valid) model.submit(session.id, question.id, input) }))
                                    Button(onClick = { model.submit(session.id, question.id, input) }, enabled = enabled && valid, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.typing_submit))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                        Text(stringResource(if (feedback.correct) R.string.typing_correct else R.string.typing_incorrect), style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(R.string.typing_submitted, feedback.submitted))
                                        Text(stringResource(R.string.typing_accepted), style = MaterialTheme.typography.titleMedium)
                                        feedback.accepted.forEach { Text(it) }
                                    }
                                    Button(onClick = { model.next(session.id, question.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(if (session.active) R.string.typing_next else R.string.typing_results))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "controls") {
                        Text(stringResource(R.string.typing_saved_hint))
                        if (session.active) OutlinedButton(onClick = { confirmEnd = true }, enabled = enabled) { Text(stringResource(R.string.flash_end)) }
                    }
                }
                else -> {
                    item(key = "summary") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (session.status == TypingStatus.ABANDONED) R.string.flash_abandoned else R.string.flash_completed), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.typing_summary, session.answered, session.total, session.correct, session.incorrect, session.remaining))
                            Text(stringResource(R.string.typing_mistakes), style = MaterialTheme.typography.titleLarge)
                            if (session.mistakes.isEmpty()) Text(stringResource(R.string.typing_no_mistakes))
                            Button(onClick = model::newSession, enabled = enabled) { Text(stringResource(R.string.flash_new_session)) }
                        }
                    }
                    items(session.mistakes.size, key = { "mistake-${session.mistakes[it].questionId}" }, contentType = { "typing-mistake" }) { index ->
                        val mistake = session.mistakes[index]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${mistake.position}. ${typingDirectionLabel(mistake.direction)}", style = MaterialTheme.typography.labelLarge)
                                mistake.prompt.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
                                Text(stringResource(R.string.typing_submitted, mistake.submitted))
                                Text(stringResource(R.string.typing_accepted))
                                mistake.accepted.forEach { Text(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun typingDirectionLabel(direction: TypingDirection) = stringResource(if (direction == TypingDirection.BG_TO_RU) R.string.flash_bg_ru else R.string.flash_ru_bg)
@Composable
private fun typingErrorLabel(error: TypingError) = stringResource(when (error) {
    TypingError.EMPTY_DICTIONARY -> R.string.flash_empty
    TypingError.SHORTER_SESSION_REQUIRED -> R.string.typing_short_required
    TypingError.INVALID_REQUEST -> R.string.typing_invalid
    TypingError.START_REQUEST_CHANGED -> R.string.flash_start_changed
    TypingError.SESSION_NOT_FOUND -> R.string.flash_session_missing
    TypingError.SESSION_CLOSED -> R.string.flash_session_closed
    TypingError.STALE_QUESTION -> R.string.typing_stale
    TypingError.ANSWER_CONFLICT -> R.string.typing_conflict
    TypingError.ANSWER_REQUIRED -> R.string.typing_answer_required
    TypingError.RESULTS_UNAVAILABLE -> R.string.flash_results_unavailable
    TypingError.STORAGE_FAILED -> R.string.flash_storage_failed
})
