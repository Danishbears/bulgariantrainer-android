package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.pronunciation.PronunciationPolicy
import com.danishbears.bulgariantrainer.sentences.*

@Composable
fun SentencePracticeScreen(model: SentencePracticeViewModel, onCollection: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope by model.scope.collectAsStateWithLifecycle()
    ReviewRefreshOnResume(model::retryAvailable)
    val available by model.availability.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    val size by model.size.collectAsStateWithLifecycle()
    val session = state.session
    PronunciationBoundary("sentence:${session?.id}:${session?.status}:${session?.question?.id}")
    val enabled = !state.busy && !state.loading && state.pending == null
    var end by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(session?.id, session?.question?.id) { scroll.scrollToItem(0) }
    if (end && session?.active == true) AlertDialog(onDismissRequest = { end = false },
        title = { Text(stringResource(R.string.flash_end_title)) }, text = { Text(stringResource(R.string.sentence_end_hint)) },
        confirmButton = { TextButton(enabled = enabled, onClick = { end = false; model.complete(session.id) }) { Text(stringResource(R.string.flash_end)) } },
        dismissButton = { TextButton(onClick = { end = false }) { Text(stringResource(R.string.flash_continue)) } })
    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "title") {
                Text(stringResource(R.string.sentence_practice), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.sentence_practice_hint), Modifier.padding(top = 8.dp))
                PronunciationSettings()
                TextButton(onClick = onCollection, enabled = !state.busy) { Text(stringResource(R.string.sentences)) }
            }
            state.error?.let { item(key = "error") { FailureMessage(sentenceErrorLabel(it), model::retry) } }
            if (state.pending != null && !state.busy) item(key = "pending") {
                Text(stringResource(R.string.typing_pending))
                OutlinedButton(onClick = { model.reload(force = true) }) { Text(stringResource(R.string.flash_refresh)) }
            }
            when {
                state.loading -> item(key = "loading") { CircularProgressIndicator() }
                session == null -> item(key = "setup") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReviewScopeSelector(scope, model::setScope, enabled)
                            val parsed = size.toIntOrNull()
                        OutlinedTextField(size, model::setSize, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = parsed == null || parsed !in 1..100,
                            label = { Text(stringResource(R.string.sentence_size)) })
                        when (val count = available) {
                            SentencePracticeAvailability.Loading -> CircularProgressIndicator()
                            SentencePracticeAvailability.Failed -> FailureMessage(stringResource(R.string.load_error), model::retryAvailable)
                            is SentencePracticeAvailability.Ready -> {
                                Text(stringResource(R.string.sentence_available, count.count))
                                if (count.count == 0L) Text(stringResource(R.string.sentence_empty))
                                else if (parsed != null && count.count < parsed) Text(stringResource(R.string.sentence_shorter, count.count))
                                Button(onClick = model::start, enabled = enabled && count.count > 0 && parsed != null && parsed in 1..100,
                                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flash_start)) }
                            }
                        }
                        if (state.hasHistory) TextButton(onClick = { model.reload(force = true) }, enabled = enabled) { Text(stringResource(R.string.flash_last_session)) }
                    }
                }
                session.question != null -> {
                    val question = session.question
                    item(key = "progress") {
                        Text(stringResource(R.string.sentence_progress, question.position, session.total, session.evaluated, session.submitted))
                        LinearProgressIndicator(progress = { session.evaluated.toFloat() / session.total }, modifier = Modifier.fillMaxWidth())
                    }
                    item(key = "question-${question.id}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.flash_ru_bg), style = MaterialTheme.typography.labelLarge)
                                Text(question.russian, style = MaterialTheme.typography.headlineSmall)
                                val feedback = question.feedback
                                if (feedback == null) {
                                    OutlinedTextField(input, model::setInput, enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 4,
                                        label = { Text(stringResource(R.string.typing_answer)) }, isError = input.length > 4000,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Default),
                                        supportingText = { if (input.length > 4000) Text(stringResource(R.string.typing_invalid)) })
                                    Button(onClick = { model.submit(session.id, question.id, input) }, enabled = enabled && input.isNotBlank() && input.length <= 4000,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.typing_submit)) }
                                } else {
                                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(sentenceFeedbackLabel(feedback), style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(R.string.typing_submitted, feedback.submitted))
                                        Text(stringResource(R.string.sentence_approved), style = MaterialTheme.typography.titleMedium)
                                        PronunciationPolicy.sentence(session.id, question).forEach { target ->
                                            Text(target.text)
                                            PronunciationPlayback(target)
                                        }
                                        feedback.explanation?.let { Text(stringResource(R.string.sentence_explanation_text, it)) }
                                    }
                                    if (feedback.needsSelfAssessment) {
                                        Text(stringResource(R.string.sentence_mismatch_hint))
                                        Text(stringResource(R.string.sentence_self_assessment), style = MaterialTheme.typography.titleMedium)
                                        SentenceRating.entries.forEach { rating -> OutlinedButton(onClick = { model.selfRate(session.id, question.id, rating) },
                                            enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(sentenceRatingLabel(rating)) } }
                                    } else Button(onClick = { model.next(session.id, question.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(if (session.active) R.string.sentence_next else R.string.typing_results))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "controls") {
                        Text(stringResource(R.string.sentence_saved_hint))
                        if (session.active) OutlinedButton(onClick = { end = true }, enabled = enabled) { Text(stringResource(R.string.flash_end)) }
                    }
                }
                else -> {
                    item(key = "summary") {
                        Text(stringResource(if (session.status == SentenceStatus.ABANDONED) R.string.flash_abandoned else R.string.flash_completed), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.sentence_summary, session.submitted, session.total, session.correct, session.partial, session.incorrect, session.pending, session.unanswered))
                        Button(onClick = model::newSession, enabled = enabled) { Text(stringResource(R.string.flash_new_session)) }
                    }
                    items(session.review.size, key = { "review-${session.review[it].questionId}" }, contentType = { "sentence-review" }) { index ->
                        val review = session.review[index]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${review.position}. ${review.russian}", style = MaterialTheme.typography.titleMedium)
                                review.feedback?.let {
                                    Text(sentenceFeedbackLabel(it)); Text(stringResource(R.string.typing_submitted, it.submitted))
                                } ?: Text(stringResource(R.string.sentence_skipped))
                                Text(stringResource(R.string.sentence_approved)); review.accepted.forEach { Text(it) }
                                review.explanation?.let { Text(stringResource(R.string.sentence_explanation_text, it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun sentenceRatingLabel(rating: SentenceRating) = stringResource(when (rating) {
    SentenceRating.CORRECT -> R.string.sentence_self_correct
    SentenceRating.PARTIAL -> R.string.sentence_self_partial
    SentenceRating.INCORRECT -> R.string.sentence_self_incorrect
})
@Composable
private fun sentenceFeedbackLabel(feedback: SentenceFeedback): String = when {
    feedback.automaticMatch -> stringResource(R.string.sentence_auto_match)
    feedback.selfRating != null -> sentenceRatingLabel(feedback.selfRating)
    else -> stringResource(R.string.sentence_pending)
}
