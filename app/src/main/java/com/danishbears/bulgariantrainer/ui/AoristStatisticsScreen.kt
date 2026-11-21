package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aorist.AoristStatisticsCounts

@Composable internal fun AoristStatisticsScreen(model:AoristStatisticsViewModel,onBack:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val dataset by model.dataset.collectAsStateWithLifecycle()
    var breakdown by rememberSaveable{mutableIntStateOf(0)}
    val focus=LocalFocusManager.current
    BackHandler(onBack=onBack)
    ReviewRefreshOnResume(model::reload)
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),modifier=Modifier.imePadding(),topBar={AspectTopBar(stringResource(R.string.aos_title),onBack)}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item{
                Text(stringResource(R.string.aos_scope))
                OutlinedTextField(dataset,model::draft,modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text(stringResource(R.string.aorist_dataset_filter))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search,autoCorrectEnabled=false),
                    keyboardActions=KeyboardActions(onSearch={focus.clearFocus();model.apply()}))
                Button(onClick={focus.clearFocus();model.apply()}){Text(stringResource(R.string.aorist_apply))}
                TextButton(onClick=model::reload){Text(stringResource(R.string.flash_refresh))}
            }
            if(state.loading)item{CircularProgressIndicator();Text(stringResource(R.string.aos_loading))}
            else if(state.refreshing)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let{item{FailureMessage(it,model::reload)}}
            state.value?.let{s->
                item{AspectHeading(s.datasetId?.let{stringResource(R.string.aos_dataset,it)}?:stringResource(R.string.aos_all))}
                for((label,value)in listOf(R.string.aos_answered to s.counts.answered,R.string.aos_correct to s.counts.correct,
                    R.string.aos_incorrect to s.counts.incorrect,R.string.aos_due to s.counts.dueForReview))item{
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(stringResource(label));Text(value.toString(),style=MaterialTheme.typography.headlineMedium)}}
                }
                item{
                    Text(stringResource(R.string.aos_revealed,s.counts.revealed));Text(stringResource(R.string.aos_rules))
                    if(s.counts.answered==0L&&s.counts.revealed==0L)Text(stringResource(R.string.aos_empty))
                    for((i,label)in listOf(R.string.aos_by_person,R.string.aos_by_exercise,R.string.aos_by_both).withIndex())
                        FilterChip(breakdown==i,{breakdown=i},label={Text(stringResource(label))})
                }
                when(breakdown){
                    0->s.persons.forEach{(person,counts)->item{AoristStatisticsCard(aoristFieldLabel(person),counts)}}
                    1->s.exercises.forEach{(exercise,counts)->item{AoristStatisticsCard(aoristExerciseLabel(exercise),counts)}}
                    else->s.cells.forEach{cell->item{AoristStatisticsCard(aoristFieldLabel(cell.person)+" · "+aoristExerciseLabel(cell.exercise),cell.counts)}}
                }
            }
        }
    }
}
@Composable private fun AoristStatisticsCard(title:String,counts:AoristStatisticsCounts) {
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        AspectHeading(title)
        Text(stringResource(R.string.aos_counts,counts.answered,counts.correct,counts.incorrect,counts.dueForReview))
        Text(stringResource(R.string.aos_revealed,counts.revealed))
    }}
}
