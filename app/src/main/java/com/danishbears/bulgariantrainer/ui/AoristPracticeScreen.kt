package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.pronunciation.PronunciationTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AoristPracticeScreen(model:AoristPracticeViewModel,onCollection:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val available by model.availability.collectAsStateWithLifecycle()
    val config by model.config.collectAsStateWithLifecycle();val size by model.size.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle();val dataset by model.dataset.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle();val selection by model.selection.collectAsStateWithLifecycle()
    val settings=AoristSettings.read(config);val session=state.session;val enabled=!state.loading&&!state.busy&&state.pending==null
    val focus=LocalFocusManager.current;val scroll=rememberLazyListState()
    var end by rememberSaveable{mutableStateOf(false)}
    val back:()->Unit={model.leave(onCollection)}
    BackHandler{back()}
    PronunciationBoundary(session?.let{it.id to it.question?.id})
    ReviewRefreshOnResume(model::retryAvailable)
    LaunchedEffect(session?.id,session?.question?.id){scroll.scrollToItem(0)}
    LaunchedEffect(session?.question?.feedback){if(session?.question?.feedback!=null)focus.clearFocus()}
    if(end&&session?.active==true)AlertDialog(onDismissRequest={end=false},title={Text(stringResource(R.string.flash_end_title))},
        text={Text(stringResource(R.string.aop_end_hint))},confirmButton={TextButton(enabled=enabled,onClick={end=false;model.complete(session.id)}){Text(stringResource(R.string.flash_end))}},
        dismissButton={TextButton(onClick={end=false}){Text(stringResource(R.string.flash_continue))}})
    Scaffold(modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        TopAppBar(title={Text(stringResource(R.string.aop_title))},navigationIcon={TextButton(enabled=enabled,onClick=back){Text(stringResource(R.string.aspect_back))}})
    }){padding->Column(Modifier.fillMaxSize().padding(padding)) {
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            state.error?.let{item{FailureMessage(it,model::retry)}}
            if(state.draftError)item{AspectNotice(stringResource(R.string.aop_draft_error),true)}
            if(state.pending!=null&&!state.busy)item{Text(stringResource(R.string.typing_pending));TextButton(onClick={model.reload(true)}){Text(stringResource(R.string.flash_refresh))}}
            when {
                state.loading->item{CircularProgressIndicator();Text(stringResource(R.string.aop_loading))}
                session==null->item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.aop_settings_hint))
                    for(exercise in AoristExercise.entries)FilterChip(settings.exercise==exercise,{model.setExercise(exercise)},enabled=enabled,label={Text(aoristExerciseLabel(exercise))})
                    Text(stringResource(R.string.aop_persons))
                    for(person in AoristContent.persons)AspectCheckbox(aoristFieldLabel(person),person in settings.persons,enabled){model.person(person,it)}
                    TextButton(enabled=enabled,onClick={model.persons(AoristContent.persons)}){Text(stringResource(R.string.aop_all_persons))}
                    TextButton(enabled=enabled,onClick={model.persons(listOf("1sg"))}){Text(stringResource(R.string.aop_only_az))}
                    for(aspect in listOf(null,"imperfective","perfective"))FilterChip(settings.aspect==aspect,{model.setAspect(aspect)},enabled=enabled,
                        label={Text(stringResource(when(aspect){"imperfective"->R.string.aop_imperfective;"perfective"->R.string.aop_perfective;else->R.string.aop_both_aspects}))})
                    OutlinedTextField(size,model::setSize,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.ap_size))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next))
                    OutlinedTextField(group,model::setGroup,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aorist_group_filter))},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(dataset,model::setDataset,enabled=enabled,singleLine=true,label={Text(stringResource(R.string.aorist_dataset_filter))},modifier=Modifier.fillMaxWidth())
                    ReviewScopeSelector(settings.scope.name,model::setScope,enabled)
                    when(val a=available) {
                        AoristPracticeAvailability.Loading->CircularProgressIndicator()
                        is AoristPracticeAvailability.Failed->FailureMessage(a.message,model::retryAvailable)
                        is AoristPracticeAvailability.Ready->{
                            Text(stringResource(R.string.aop_available,a.value.eligible))
                            if(a.value.eligible==0)Text(stringResource(R.string.aop_empty))
                            if(a.value.unavailableChoices>0)Text(stringResource(R.string.aop_unavailable_choices,a.value.unavailableChoices))
                            if(a.value.fewerOptions>0)Text(stringResource(R.string.aop_fewer_options))
                            if(a.value.eligible>0){
                                if(a.value.shorterRequired)Text(stringResource(R.string.aop_shorter,a.value.offered))
                                Button(enabled=enabled,onClick={focus.clearFocus();model.start(a.value.shorterRequired,a.value.fewerOptions>0)},modifier=Modifier.fillMaxWidth()){
                                    Text(stringResource(when{a.value.fewerOptions>0->R.string.aop_start_fewer;a.value.shorterRequired->R.string.ap_start_short;else->R.string.flash_start}))
                                }
                            }
                            if(settings.exercise==AoristExercise.CHOICE&&(a.value.unavailableChoices>0||a.value.fewerOptions>0))OutlinedButton(enabled=enabled,onClick={model.setExercise(AoristExercise.TYPED)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aop_offer_typed))}
                        }
                    }
                    if(state.hasHistory)TextButton(enabled=enabled,onClick={model.reload(true)}){Text(stringResource(R.string.flash_last_session))}
                    PronunciationSettings()
                }}
                session.question!=null->{
                    val q=session.question
                    if(state.restored)item{AspectNotice(stringResource(R.string.aop_resumed))}
                    item{Text(stringResource(R.string.ap_position,q.position,session.total,session.answered,session.revealed))
                        LinearProgressIndicator(progress={(session.answered+session.revealed).toFloat()/session.total},modifier=Modifier.fillMaxWidth())}
                    item(key="aorist-${q.id}"){AoristQuestionCard(session.id,q,input,selection,enabled,
                        {if(model.state.value.session?.question?.id==q.id)model.setInput(it)},
                        {if(model.state.value.session?.question?.id==q.id)model.select(it)},
                        {model.submit(session.id,q.id,input,selection)},{model.reveal(session.id,q.id)},{model.next(session.id,q.id)})}
                    item{Text(stringResource(R.string.aop_saved));OutlinedButton(enabled=enabled,onClick={end=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_end))}}
                }
                else->{
                    item{Text(stringResource(R.string.aop_summary),style=MaterialTheme.typography.headlineSmall)
                        Text(aoristExerciseLabel(session.settings.exercise));Text(stringResource(if(session.status==AoristSessionStatus.COMPLETED)R.string.aop_completed else R.string.aop_abandoned))
                        Text(stringResource(R.string.aop_counts,session.answered,session.correct,session.incorrect,session.revealed,session.skipped))
                        Text(stringResource(R.string.aop_mistakes));if(session.incorrect+session.revealed==0)Text(stringResource(R.string.aop_no_mistakes))}
                    val review=session.review.filter{it.feedback?.outcome!=AoristOutcome.CORRECT}
                    items(review.size,key={review[it].position}){i->val item=review[i]
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            AoristPromptView(item.prompt,item.exercise)
                            item.feedback?.let{AoristFeedbackView(it,"aorist-review-${session.id}-${item.position}")}
                                ?:Text(stringResource(R.string.aop_skipped))
                        }}
                    }
                    item{Button(enabled=enabled,onClick=model::newSession,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_new_session))}}
                }
            }
        }
    }}
}

@Composable internal fun aoristExerciseLabel(exercise:AoristExercise)=stringResource(if(exercise==AoristExercise.TYPED)R.string.aop_typed else R.string.aop_choice)
@Composable private fun AoristPromptView(prompt:AoristPrompt,exercise:AoristExercise) {
    Text("${prompt.lemma} — ${prompt.russian}",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics{heading()})
    Text(stringResource(if(exercise==AoristExercise.TYPED)R.string.aop_instruction_typed else R.string.aop_instruction_choice,aoristFieldLabel(prompt.person)))
}
@Composable internal fun AoristQuestionCard(sessionId:Long,q:AoristQuestion,input:String,selection:String?,enabled:Boolean,
    onInput:(String)->Unit,onSelection:(String)->Unit,onSubmit:()->Unit,onReveal:()->Unit,onNext:()->Unit) {
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        AoristPromptView(q.prompt,q.exercise)
        PronunciationPlayback(PronunciationTarget("aorist-$sessionId-${q.id}-source",q.prompt.lemma))
        if(q.feedback==null) {
            if(q.exercise==AoristExercise.TYPED)OutlinedTextField(input,onInput,enabled=enabled,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text(stringResource(R.string.ap_answer))},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={if(enabled&&AoristContent.key(input).isNotEmpty())onSubmit()}))
            else Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.aop_option_count,q.options.size))
                for(option in q.options)Row(Modifier.fillMaxWidth().selectable(selection==option.id,enabled=enabled,role=Role.RadioButton,onClick={onSelection(option.id)}).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    RadioButton(selected=selection==option.id,onClick=null,enabled=enabled);Text(option.text)
                }
            }
            val valid=if(q.exercise==AoristExercise.TYPED)AoristContent.key(input).isNotEmpty()&&input.length<=4000 else q.options.any{it.id==selection}
            Button(enabled=enabled&&valid,onClick=onSubmit,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.typing_submit))}
            OutlinedButton(enabled=enabled,onClick=onReveal,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.flash_reveal))}
            Text(stringResource(R.string.aop_reveal_hint))
        }else {
            AoristFeedbackView(q.feedback,"aorist-$sessionId-${q.id}")
            Button(enabled=enabled,onClick=onNext,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.ap_next))}
        }
    }}
}
@Composable private fun AoristFeedbackView(f:AoristFeedback,owner:String) {
    Text(stringResource(when(f.outcome){AoristOutcome.CORRECT->R.string.typing_correct;AoristOutcome.INCORRECT->R.string.ap_mismatch;AoristOutcome.REVEALED->R.string.aop_revealed}),
        style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite;heading()})
    f.submitted?.let{Text(stringResource(R.string.typing_submitted,it))}
    Text(stringResource(R.string.typing_accepted))
    f.accepted.forEachIndexed{i,text->Text(text);PronunciationPlayback(PronunciationTarget("$owner-approved-$i",text))}
    Text(AoristContent.text(f.content,"note"))
}
