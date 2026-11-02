package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danishbears.bulgariantrainer.R

@Composable internal fun AspectHelpScreen(onBack:()->Unit) {
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),topBar={AspectTopBar(stringResource(R.string.aspect_help_title),onBack)}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item{AspectHeading(stringResource(R.string.aspect_help_aspect_title));Text(stringResource(R.string.aspect_help_aspect))}
            item{AspectHeading(stringResource(R.string.aspect_help_forms_title));Text(stringResource(R.string.aspect_help_forms))}
            item{AspectHeading(stringResource(R.string.aspect_help_particles_title));Text(stringResource(R.string.aspect_help_particles))}
            item{AspectHeading(stringResource(R.string.aspect_help_meaning_title));Text(stringResource(R.string.aspect_help_meaning))}
            item{AspectHeading(stringResource(R.string.ap_stages));Text(stringResource(R.string.aspect_help_stages))}
            item{AspectHeading(stringResource(R.string.aspect_help_reveal_title));Text(stringResource(R.string.ap_reveal_hint))}
            item{AspectHeading(stringResource(R.string.aspect_help_offline_title));Text(stringResource(R.string.aspect_help_offline));PronunciationSettings()}
        }
    }
}
