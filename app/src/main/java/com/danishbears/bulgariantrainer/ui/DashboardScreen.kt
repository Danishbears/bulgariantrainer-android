package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danishbears.bulgariantrainer.R
import java.text.NumberFormat

@Composable
fun DashboardScreen(state: DashboardState, onRetry: () -> Unit, onDictionary: () -> Unit, onAdd: () -> Unit, onSentences: () -> Unit, onStatistics: () -> Unit, onBackup: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.dashboard_subtitle), style = MaterialTheme.typography.bodyLarge)
            }
        }
        when (state) {
            DashboardState.Loading -> item { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
            DashboardState.Failed -> item { FailureMessage(stringResource(R.string.load_error), onRetry) }
            is DashboardState.Ready -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CountCard(R.string.total_words, state.counts.total, Modifier.weight(1f))
                            CountCard(R.string.translation_count, state.counts.translations, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CountCard(R.string.active_words, state.counts.active, Modifier.weight(1f))
                            CountCard(R.string.archived_words, state.counts.archived, Modifier.weight(1f))
                        }
                    }
                }
                if (state.counts.total == 0L) item {
                    Text(stringResource(R.string.first_word), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.first_word_hint), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_title)) }
                OutlinedButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.statistics)) }
                OutlinedButton(onClick = onSentences, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sentences)) }
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_word)) }
                OutlinedButton(onClick = onDictionary, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_dictionary)) }
            }
        }
    }
}

@Composable
private fun CountCard(label: Int, count: Long, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(NumberFormat.getIntegerInstance().format(count), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun FailureMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
