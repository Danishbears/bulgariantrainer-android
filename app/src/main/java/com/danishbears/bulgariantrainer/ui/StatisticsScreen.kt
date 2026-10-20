package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.review.*
import com.danishbears.bulgariantrainer.statistics.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen(model: StatisticsViewModel, onWord: (Long) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    ReviewRefreshOnResume(model::reload)
    var month by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "title") {
            Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.statistics_time_hint, ZoneId.systemDefault().id))
            OutlinedButton(onClick = model::reload) { Text(stringResource(R.string.review_refresh)) }
        }
        when (val current = state) {
            StatisticsState.Loading -> item { CircularProgressIndicator() }
            StatisticsState.Failed -> item { FailureMessage(stringResource(R.string.load_error), model::reload) }
            is StatisticsState.Ready -> {
                val s = current.overview; val v = s.vocabulary
                item(key = "vocabulary") {
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dictionary), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.statistics_vocabulary, v.total, v.active, v.archived, v.newItems, v.dueItems, v.masteredItems))
                        Text(stringResource(R.string.statistics_mastery_hint))
                        Text(stringResource(R.string.statistics_totals, s.completedSessions, s.reviewAttempts, s.consecutiveStudyDays, s.reviewsScheduledToday))
                    } }
                }
                if (s.inheritedSchedules > 0) item(key = "legacy") { Text(stringResource(R.string.review_legacy_notice, s.inheritedSchedules)) }
                item(key = "performance") {
                    Text(stringResource(R.string.statistics_performance), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.statistics_accuracy_hint))
                    PerformanceRow(stringResource(R.string.statistics_all_modes), s.overall)
                    PerformanceRow(reviewModeLabel(ReviewMode.FLASHCARD), s.flashcards)
                    PerformanceRow(reviewModeLabel(ReviewMode.TYPING_BG_TO_RU), s.typingBgToRu)
                    PerformanceRow(reviewModeLabel(ReviewMode.TYPING_RU_TO_BG), s.typingRuToBg)
                    PerformanceRow(reviewModeLabel(ReviewMode.SENTENCE), s.sentences)
                }
                item(key = "categories") {
                    Text(stringResource(R.string.statistics_categories), style = MaterialTheme.typography.titleLarge)
                    s.categories.forEach { (mode, counts) ->
                        Text(reviewModeLabel(mode), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.statistics_category_counts, counts.newItems, counts.due, counts.learning, counts.mastered))
                    }
                }
                item(key = "activity") {
                    Text(stringResource(R.string.statistics_activity), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !month, onClick = { month = false }, label = { Text(stringResource(R.string.statistics_week)) })
                        FilterChip(selected = month, onClick = { month = true }, label = { Text(stringResource(R.string.statistics_month)) })
                    }
                }
                val days = if (month) s.last30Days else s.last7Days
                items(days.size, key = { "day-${days[it].date}" }, contentType = { "activity-day" }) { index ->
                    val day = days[index]
                    Text(stringResource(R.string.statistics_day, day.date.toString(), day.reviewAttempts, day.correctAnswers, day.completedSessions))
                }
                item(key = "recent-title") { Text(stringResource(R.string.statistics_recent), style = MaterialTheme.typography.titleLarge) }
                if (s.recentSessions.isEmpty()) item(key = "recent-empty") { Text(stringResource(R.string.statistics_empty)) }
                items(s.recentSessions.size, key = { "session-${s.recentSessions[it].mode}-${s.recentSessions[it].id}" }, contentType = { "recent-session" }) { index ->
                    val session = s.recentSessions[index]
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(practiceModeLabel(session.mode), style = MaterialTheme.typography.titleMedium)
                        Text(localTime(session.startedAt)); Text(sessionStatusLabel(session.status))
                        Text(stringResource(R.string.statistics_recent_rate, session.answeredQuestions, session.totalQuestions, session.correctAnswers, accuracy(session.accuracyPercent)))
                    } }
                }
                item(key = "difficult-title") { Text(stringResource(R.string.statistics_difficult), style = MaterialTheme.typography.titleLarge) }
                if (s.difficultWords.isEmpty()) item(key = "difficult-empty") { Text(stringResource(R.string.statistics_empty)) }
                items(s.difficultWords.size, key = { "word-${s.difficultWords[it].wordId}" }, contentType = { "difficult-word" }) { index ->
                    val word = s.difficultWords[index]
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(word.bulgarian, style = MaterialTheme.typography.titleMedium)
                        word.translations.forEach { Text(it) }
                        if (word.archived) Text(stringResource(R.string.sentence_archive))
                        Text(stringResource(R.string.statistics_wrong_count, word.incorrectAttempts))
                        TextButton(onClick = { onWord(word.wordId) }) { Text(stringResource(R.string.statistics_mistakes)) }
                    } }
                }
            }
        }
    }
}
@Composable
private fun PerformanceRow(title: String, performance: Performance) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.statistics_rate, performance.total.attempts, performance.total.correct, performance.total.partial, performance.total.incorrect, accuracy(performance.total.accuracyPercent)))
        Text(stringResource(R.string.statistics_grading, performance.automatic.attempts, performance.selfAssessed.attempts, performance.unclassified.attempts))
        Text(stringResource(R.string.statistics_grading_accuracy, accuracy(performance.automatic.accuracyPercent), accuracy(performance.selfAssessed.accuracyPercent)))
    }
}
@Composable
fun WordMistakesScreen(model: WordMistakesViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.statistics_mistakes), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text(stringResource(R.string.sentence_back)) } }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.failed) item { FailureMessage(stringResource(R.string.load_error), model::reload) }
        state.value?.let { word ->
            item { Text(word.bulgarian, style = MaterialTheme.typography.titleLarge)
                word.translations.forEach { Text(it) }
                Text(stringResource(R.string.statistics_wrong_count, word.incorrectAttempts)) }
            items(word.recent.size, key = { "${word.recent[it].mode}-${word.recent[it].attemptId}" }) { index ->
                val row = word.recent[index]
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(practiceModeLabel(row.mode)); Text(localTime(row.at))
                    row.direction?.let { Text(stringResource(if (it == "BG_TO_RU") R.string.flash_bg_ru else R.string.flash_ru_bg)) }
                    Text(stringResource(if (row.grading == GradingType.SELF_ASSESSED) R.string.statistics_self else R.string.statistics_auto))
                    row.submitted?.let { Text(stringResource(R.string.typing_submitted, it)) } ?: Text(stringResource(R.string.flash_dont_know))
                } }
            }
        }
    }
}
@Composable internal fun reviewModeLabel(mode: ReviewMode) = stringResource(when (mode) {
    ReviewMode.FLASHCARD -> R.string.flashcards; ReviewMode.TYPING_BG_TO_RU -> R.string.statistics_typing_bg
    ReviewMode.TYPING_RU_TO_BG -> R.string.statistics_typing_ru; ReviewMode.SENTENCE -> R.string.sentence_practice
})
@Composable private fun practiceModeLabel(mode: PracticeMode) = stringResource(when (mode) {
    PracticeMode.FLASHCARD -> R.string.flashcards; PracticeMode.TYPING -> R.string.typing; PracticeMode.SENTENCE -> R.string.sentence_practice
})
@Composable private fun sessionStatusLabel(status: String) = stringResource(when (status) {
    "COMPLETED" -> R.string.flash_completed; "ABANDONED" -> R.string.flash_abandoned; else -> R.string.statistics_in_progress
})
@Composable private fun accuracy(value: Double?): String = value?.let { "$it%" } ?: stringResource(R.string.statistics_undefined)
private fun localTime(at: Instant): String = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm z").withZone(ZoneId.systemDefault()).format(at)
