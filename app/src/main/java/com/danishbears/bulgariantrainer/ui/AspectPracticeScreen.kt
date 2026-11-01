package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aspect.*

@Composable fun AspectPracticeScreen(model:AspectPracticeViewModel,onCollection:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val available by model.availability.collectAsStateWithLifecycle()
    val config by model.config.collectAsStateWithLifecycle();val size by model.size.collectAsStateWithLifecycle()
    val topic by model.topic.collectAsStateWithLifecycle();val namespace by model.namespace.collectAsStateWithLifecycle();val input by model.input.collectAsStateWithLifecycle()
    val settings=AspectSettings.read(config);val session=state.session;val enabled=!state.loading&&!state.busy&&state.pending==null
    val focus=LocalFocusManager.current;val scroll=rememberLazyListState()
    var end by rememberSaveable{mutableStateOf(false)};var all by rememberSaveable{mutableStateOf(false)}
    ReviewRefreshOnResume(model::retryAvailable)
    LaunchedEffect(session?.id,session?.question?.id){scroll.scrollToItem(0);all=false}
    LaunchedEffect(session?.question?.feedback){if(session?.question?.feedback!=null)focus.clearFocus()}
    if(end&&session?.active==true)AlertDialog(onDismissRequest={end=false},title={Text(stringResource(R.string.flash_end_title))},
        text={Text(stringResource(R.string.ap_end_hint))},confirmButton={TextButton(enabled=enabled,onClick={end=false;model.complete(session.id)}){Text(stringResource(R.string.flash_end))}},
        dismissButton={TextButton(onClick={end=false}){Text(stringResource(R.string.flash_continue))}})
    Column(Modifier.fillMaxSize().imePadding()) {
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.ap_title),style=MaterialTheme.typography.headlineMedium)
                TextButton(enabled=!state.busy,onClick=onCollection){Text(stringResource(R.string.aspect_title))} }
            state.error?.let{item{FailureMessage(it,model::retry)}}
            if(state.pending!=null&&!state.busy)item{Text(stringResource(R.string.typing_pending));OutlinedButton(onClick={model.reload(true)}){Text(stringResource(R.string.flash_refresh))}}
            when {
                state.loading->item{CircularProgressIndicator()}
                session==null->item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ap_settings_hint))
                        OutlinedTextField(size,model::setSize,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.ap_size))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                        OutlinedTextField(topic,model::setTopic,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aspect_topic_filter))},modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(namespace,model::setNamespace,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aspect_namespace_filter))},modifier=Modifier.fillMaxWidth())
                        Text(stringResource(R.string.ap_stages))
                        for(stage in 1..3)Row { Checkbox(stage in settings.stages,{model.stage(stage,it)},enabled=enabled);Text(stringResource(R.string.ap_stage,stage)) }
                        for(direction in AspectDirection.entries)FilterChip(selected=settings.direction==direction,enabled=enabled,onClick={model.setDirection(direction)},label={Text(directionLabel(direction))})
                        ReviewScopeSelector(settings.scope.name,model::setScope,enabled)
                        when(val a=available) {
                            AspectPracticeAvailability.Loading->CircularProgressIndicator()
                            is AspectPracticeAvailability.Failed->FailureMessage(a.message,model::retryAvailable)
                            is AspectPracticeAvailability.Ready->{
                                Text(stringResource(R.string.ap_available,a.value.eligible,a.value.forward,a.value.reverse))
                                if(a.value.eligible==0)Text(stringResource(R.string.ap_empty))
                                else {
                                    if(a.value.shorterRequired)Text(stringResource(R.string.ap_shorter,a.value.offered))
                                    Button(enabled=enabled,onClick={model.start(a.value.shorterRequired)},modifier=Modifier.fillMaxWidth()) {
                                        Text(stringResource(if(a.value.shorterRequired)R.string.ap_start_short else R.string.flash_start))
                                    }
                                }
                            }
                        }
                        if(state.hasHistory)TextButton(enabled=enabled,onClick={model.reload(true)}){Text(stringResource(R.string.flash_last_session))}
                    }
                }
                session.question!=null->{
                    val q=session.question
                    item { Text(stringResource(R.string.ap_position,q.position,session.total,session.answered,session.revealed))
                        LinearProgressIndicator(progress={(session.answered+session.revealed).toFloat()/session.total},modifier=Modifier.fillMaxWidth()) }
                    item(key="question-${q.id}") {
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(directionLabel(q.direction),style=MaterialTheme.typography.labelLarge)
                            AspectPromptView(q.prompt)
                            Text(stringResource(if(q.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)R.string.ap_ask_perfective else R.string.ap_ask_imperfective))
                            val feedback=q.feedback
                            if(feedback==null) {
                                val valid=input.isNotBlank()&&input.length<=4000
                                OutlinedTextField(input,model::setInput,enabled=enabled,modifier=Modifier.fillMaxWidth(),singleLine=true,
                                    label={Text(stringResource(R.string.typing_answer))},isError=input.length>4000,
                                    keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,imeAction=ImeAction.Done),
                                    keyboardActions=KeyboardActions(onDone={if(enabled&&valid)model.submit(session.id,q.id,input)}))
                                Button(enabled=enabled&&valid,onClick={model.submit(session.id,q.id,input)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.typing_submit))}
                                OutlinedButton(enabled=enabled,onClick={model.reveal(session.id,q.id)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_reveal))}
                                Text(stringResource(R.string.ap_reveal_hint))
                            } else {
                                Column(Modifier.semantics{liveRegion=LiveRegionMode.Polite},verticalArrangement=Arrangement.spacedBy(8.dp)){
                                    AspectFeedbackView(feedback)
                                }
                                Button(enabled=enabled,onClick={model.next(session.id,q.id)},modifier=Modifier.fillMaxWidth()) {
                                    Text(stringResource(if(session.active)R.string.typing_next else R.string.typing_results))
                                }
                            }
                        }}
                    }
                    item {Text(stringResource(R.string.ap_saved));if(session.active)OutlinedButton(enabled=enabled,onClick={end=true}){Text(stringResource(R.string.flash_end))}}
                }
                else->{
                    item {
                        Text(stringResource(if(session.status==AspectSessionStatus.ABANDONED)R.string.flash_abandoned else R.string.flash_completed),style=MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.ap_summary,session.answered,session.correct,session.incorrect,session.revealed,session.remaining))
                        Text(stringResource(R.string.ap_accuracy,session.accuracy?.toString()?:"—"))
                        Button(enabled=enabled,onClick=model::newSession){Text(stringResource(R.string.flash_new_session))}
                        Row{Checkbox(all,{all=it});Text(stringResource(R.string.ap_all_results))}
                        if(!all&&session.review.none{it.feedback?.outcome in setOf(AspectOutcome.INCORRECT,AspectOutcome.REVEALED)})Text(stringResource(R.string.typing_no_mistakes))
                    }
                    val review=if(all)session.review else session.review.filter{it.feedback?.outcome in setOf(AspectOutcome.INCORRECT,AspectOutcome.REVEALED)}
                    items(review.size,key={review[it].position}){i->val r=review[i]
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("${r.position}. "+directionLabel(r.direction));AspectPromptView(r.prompt)
                            r.feedback?.let{AspectFeedbackView(it)}?:Text(stringResource(R.string.sentence_skipped))
                        }}
                    }
                }
            }
        }
    }
}
@Composable private fun directionLabel(d:AspectDirection)=stringResource(when(d){
    AspectDirection.IMPERFECTIVE_TO_PERFECTIVE->R.string.ap_forward
    AspectDirection.PERFECTIVE_TO_IMPERFECTIVE->R.string.ap_reverse
    AspectDirection.MIXED->R.string.ap_mixed
})
@Composable private fun AspectPromptView(prompt:AspectPrompt) {
    Text(prompt.bulgarian,style=MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.ap_source_meaning,prompt.russian))
    Text(stringResource(R.string.ap_target_meaning,prompt.targetRussian))
}
@Composable private fun AspectFeedbackView(f:AspectFeedback) {
    Text(stringResource(when(f.outcome){AspectOutcome.CORRECT->R.string.typing_correct;AspectOutcome.INCORRECT->R.string.ap_mismatch;AspectOutcome.REVEALED->R.string.ap_revealed}),style=MaterialTheme.typography.titleMedium)
    f.submitted?.let{Text(stringResource(R.string.typing_submitted,it))}
    Text(stringResource(R.string.typing_accepted));f.accepted.forEach{Text(it)}
    AspectEntry(f.content)
}
