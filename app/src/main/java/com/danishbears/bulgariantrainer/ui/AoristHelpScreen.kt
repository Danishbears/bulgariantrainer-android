package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.danishbears.bulgariantrainer.R

/** Learning guidance only; no sentence generation or sentence-gap exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AoristHelpScreen(onBack:()->Unit) {
    BackHandler(onBack=onBack)
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        AspectTopBar(stringResource(R.string.aorist_help_title),onBack)
    }){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            for((title,body)in listOf(
                R.string.aorist_help_tense_title to R.string.aorist_help_tense,
                R.string.aorist_help_same_title to R.string.aorist_help_same,
                R.string.aorist_help_reflexive_title to R.string.aorist_help_reflexive,
                R.string.aorist_help_rules_title to R.string.aorist_help_rules,
                R.string.aorist_help_groups_title to R.string.aorist_help_groups,
                R.string.aorist_help_offline_title to R.string.aorist_help_offline
            ))item{
                Text(stringResource(title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})
                Text(stringResource(body))
            }
            item{PronunciationSettings()}
        }
    }
}
