package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
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
import com.danishbears.bulgariantrainer.pronunciation.*

@Composable fun AspectPracticeScreen(model:AspectPracticeViewModel,onCollection:()->Unit,onHelp:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val available by model.availability.collectAsStateWithLifecycle()
    val config by model.config.collectAsStateWithLifecycle();val size by model.size.collectAsStateWithLifecycle()
    val topic by model.topic.collectAsStateWithLifecycle();val namespace by model.namespace.collectAsStateWithLifecycle();val input by model.input.collectAsStateWithLifecycle()
    val settings=AspectSettings.read(config);val session=state.session;val enabled=!state.loading&&!state.busy&&state.pending==null
    val focus=LocalFocusManager.current;val scroll=rememberLazyListState()
    var end by rememberSaveable{mutableStateOf(false)};var all by rememberSaveable{mutableStateOf(false)}
    BackHandler{if(!state.busy)onCollection()}
    PronunciationBoundary(session?.let{it.id to it.question?.id})
    ReviewRefreshOnResume(model::retryAvailable)
    LaunchedEffect(session?.id,session?.question?.id){scroll.scrollToItem(0);all=false}
    LaunchedEffect(session?.question?.feedback){if(session?.question?.feedback!=null)focus.clearFocus()}
    if(end&&session?.active==true)AlertDialog(onDismissRequest={end=false},title={Text(stringResource(R.string.flash_end_title))},
        text={Text(stringResource(R.string.ap_end_hint))},confirmButton={TextButton(enabled=enabled,onClick={end=false;model.complete(session.id)}){Text(stringResource(R.string.flash_end))}},
        dismissButton={TextButton(onClick={end=false}){Text(stringResource(R.string.flash_continue))}})
    Scaffold(modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),
        topBar={AspectTopBar(stringResource(R.string.ap_title),onCollection,onHelp,!state.busy)}){padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                state.error?.let{item{FailureMessage(it,model::retry)}}
                if(state.pending!=null&&!state.busy)item{Text(stringResource(R.string.typing_pending));OutlinedButton(onClick={model.reload(true)}){Text(stringResource(R.string.flash_refresh))}}
                when {
                    state.loading->item{CircularProgressIndicator();Text(stringResource(R.string.aspect_loading))}
                    session==null->item {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.ap_settings_hint))
                            OutlinedTextField(size,model::setSize,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.ap_size))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next))
                            OutlinedTextField(topic,model::setTopic,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aspect_topic_filter))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next))
                            OutlinedTextField(namespace,model::setNamespace,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aspect_namespace_filter))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
                            AspectHeading(stringResource(R.string.ap_stages))
                            for(stage in 1..3)AspectCheckbox(stringResource(R.string.ap_stage,stage),stage in settings.stages,enabled){model.stage(stage,it)}
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
                                        Button(enabled=enabled,onClick={focus.clearFocus();model.start(a.value.shorterRequired)},modifier=Modifier.fillMaxWidth()) {
                                            Text(stringResource(if(a.value.shorterRequired)R.string.ap_start_short else R.string.flash_start))
                                        }
                                    }
                                }
                            }
                            if(state.hasHistory)TextButton(enabled=enabled,onClick={model.reload(true)}){Text(stringResource(R.string.flash_last_session))}
                            PronunciationSettings()
                        }
                    }
                    session.question!=null->{
                        val q=session.question
                        if(state.restored)item{AspectNotice(stringResource(R.string.aspect_resume_notice))}
                        item {
                            Text(stringResource(R.string.ap_position,q.position,session.total,session.answered,session.revealed))
                            LinearProgressIndicator(progress={(session.answered+session.revealed).toFloat()/session.total},modifier=Modifier.fillMaxWidth())
                        }
                        item(key="question-${q.id}") {
                            AspectQuestionCard(session.id,q,input,enabled,model::setInput,
                                onSubmit={model.submit(session.id,q.id,input)},onReveal={model.reveal(session.id,q.id)},onNext={model.next(session.id,q.id)})
                        }
                        item{Text(stringResource(R.string.ap_saved));if(session.active)OutlinedButton(enabled=enabled,onClick={end=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_end))}}
                        item{PronunciationSettings()}
                    }
                    else->{
                        item {
                            AspectHeading(stringResource(if(session.status==AspectSessionStatus.ABANDONED)R.string.flash_abandoned else R.string.flash_completed))
                            Text(stringResource(R.string.ap_summary,session.answered,session.correct,session.incorrect,session.revealed,session.remaining))
                            Text(stringResource(R.string.ap_accuracy,session.accuracy?.toString()?:"—"))
                            Button(enabled=enabled,onClick=model::newSession,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_new_session))}
                            AspectCheckbox(stringResource(R.string.ap_all_results),all){all=it}
                            if(!all&&session.review.none{it.feedback?.outcome in setOf(AspectOutcome.INCORRECT,AspectOutcome.REVEALED)})Text(stringResource(R.string.typing_no_mistakes))
                        }
                        val review=if(all)session.review else session.review.filter{it.feedback?.outcome in setOf(AspectOutcome.INCORRECT,AspectOutcome.REVEALED)}
                        items(review.size,key={review[it].position}){i->val r=review[i]
                            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("${r.position}. "+directionLabel(r.direction));AspectPromptView(r.prompt,r.direction)
                                r.feedback?.let{AspectFeedbackView(it,PronunciationPolicy.aspectReviewAnswers(session.id,r))}?:Text(stringResource(R.string.sentence_skipped))
                            }}
                        }
                    }
                }
            }
        }
    }
}

/** Contains no target-content nodes or TTS registrations until feedback is present. */
@Composable internal fun AspectQuestionCard(sessionId:Long,q:AspectQuestion,input:String,enabled:Boolean,
    onInput:(String)->Unit,onSubmit:()->Unit,onReveal:()->Unit,onNext:()->Unit) {
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        AspectPromptView(q.prompt,q.direction)
        PronunciationPlayback(PronunciationPolicy.aspectSource(sessionId,q))
        val feedback=q.feedback
        if(feedback==null) {
            val valid=input.isNotBlank()&&input.length<=4000
            OutlinedTextField(input,onInput,enabled=enabled,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text(stringResource(R.string.ap_answer))},isError=input.length>4000,
                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={if(enabled&&valid)onSubmit()}))
            Button(enabled=enabled&&valid,onClick=onSubmit,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.typing_submit))}
            OutlinedButton(enabled=enabled,onClick=onReveal,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_reveal))}
            Text(stringResource(R.string.ap_reveal_hint))
        } else {
            AspectFeedbackView(feedback,PronunciationPolicy.aspectAnswers(sessionId,q))
            Button(enabled=enabled,onClick=onNext,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.ap_next))}
        }
    }}
}
@Composable internal fun directionLabel(d:AspectDirection)=stringResource(when(d){
    AspectDirection.IMPERFECTIVE_TO_PERFECTIVE->R.string.ap_forward
    AspectDirection.PERFECTIVE_TO_IMPERFECTIVE->R.string.ap_reverse
    AspectDirection.MIXED->R.string.ap_mixed
})
@Composable private fun AspectPromptView(prompt:AspectPrompt,direction:AspectDirection) {
    Text(stringResource(R.string.ap_prompt_form,prompt.bulgarian,prompt.russian),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics{heading()})
    Text(stringResource(if(direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)R.string.ap_instruction_perfective else R.string.ap_instruction_imperfective,prompt.targetRussian),style=MaterialTheme.typography.bodyLarge)
}
@Composable internal fun AspectFeedbackView(f:AspectFeedback,targets:List<PronunciationTarget> = emptyList()) {
    val title=stringResource(when(f.outcome){AspectOutcome.CORRECT->R.string.typing_correct;AspectOutcome.INCORRECT->R.string.ap_mismatch;AspectOutcome.REVEALED->R.string.ap_revealed})
    Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite;heading()})
    f.submitted?.let{Text(stringResource(R.string.typing_submitted,it))}?:Text(stringResource(R.string.ap_revealed_no_submission))
    Text(stringResource(R.string.typing_accepted));f.accepted.forEachIndexed{i,text->Text(text);PronunciationPlayback(targets.getOrNull(i))}
    AspectEntry(f.content)
}
