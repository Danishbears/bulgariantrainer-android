package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R

@Composable internal fun AspectStatisticsScreen(model:AspectStatisticsViewModel,onBack:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val namespace by model.namespace.collectAsStateWithLifecycle()
    val focus=LocalFocusManager.current
    ReviewRefreshOnResume(model::reload)
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),modifier=Modifier.imePadding(),topBar={AspectTopBar(stringResource(R.string.aspect_statistics_title),onBack)}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.aspect_statistics_scope))
                OutlinedTextField(namespace,model::draft,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(stringResource(R.string.aspect_namespace_filter))},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search,autoCorrectEnabled=false),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();model.apply()}))
                Button(onClick={focus.clearFocus();model.apply()}){Text(stringResource(R.string.aspect_apply))}
                TextButton(onClick=model::reload){Text(stringResource(R.string.flash_refresh))}
            }
            if(state.loading)item{CircularProgressIndicator();Text(stringResource(R.string.aspect_statistics_loading))}
            else if(state.refreshing)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let{item{FailureMessage(it,model::reload)}}
            state.value?.let{s->
                item{AspectHeading(s.namespace?.let{stringResource(R.string.aspect_statistics_namespace,it)}?:stringResource(R.string.aspect_statistics_all))}
                for((label,value) in listOf(R.string.aspect_statistics_answered to s.answered,R.string.aspect_statistics_correct to s.correct,R.string.aspect_statistics_incorrect to s.incorrect,R.string.aspect_statistics_due to s.dueForReview))item{
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(stringResource(label));Text(value.toString(),style=MaterialTheme.typography.headlineMedium)}}
                }
                item{Text(stringResource(R.string.aspect_statistics_revealed,s.revealed));Text(stringResource(R.string.aspect_statistics_due_directions,s.dueDirections));Text(stringResource(R.string.aspect_statistics_explanation))}
                for(d in s.directions)item{
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        AspectHeading(directionLabel(d.direction))
                        Text(stringResource(R.string.aspect_statistics_direction_counts,d.answered,d.correct,d.incorrect,d.revealed,d.dueForReview))
                    }}
                }
            }
        }
    }
}
