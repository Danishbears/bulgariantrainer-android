package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aspect.*
import kotlinx.serialization.json.*

private val aspectFields=linkedMapOf(
    "namespace" to R.string.aspect_namespace,"id" to R.string.aspect_external_id,
    "imperfective" to R.string.aspect_imperfective,"perfective" to R.string.aspect_perfective,
    "russianImperfective" to R.string.aspect_ru_imperfective,"russianPerfective" to R.string.aspect_ru_perfective,
    "acceptedImperfective" to R.string.aspect_accepted_imperfective,"acceptedPerfective" to R.string.aspect_accepted_perfective,
    "topic" to R.string.aspect_topic,"note" to R.string.aspect_note,
    "sourceId" to R.string.aspect_source_id,"tableRow" to R.string.aspect_table_row,
    "example_imperfective_bg" to R.string.aspect_example_ibg,"example_imperfective_ru" to R.string.aspect_example_iru,
    "example_perfective_bg" to R.string.aspect_example_pbg,"example_perfective_ru" to R.string.aspect_example_pru)

@Composable fun AspectScreen(model:AspectViewModel,onPractice:()->Unit,onBack:()->Unit,onHelp:()->Unit,onStatistics:()->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    var namespace by rememberSaveable{mutableStateOf("bulgarian-aspect-pairs-100")}
    var includeArchived by rememberSaveable{mutableStateOf(false)}
    var pickerNamespace by rememberSaveable{mutableStateOf("")}
    var exportNamespace by rememberSaveable{mutableStateOf("")}
    var exportArchived by rememberSaveable{mutableStateOf(false)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    var discard by rememberSaveable{mutableStateOf(false)}
    var filters by rememberSaveable{mutableStateOf(false)}
    var metadata by rememberSaveable{mutableStateOf(false)}
    var examples by rememberSaveable{mutableStateOf(false)}
    val scroll=rememberLazyListState()
    val focus=LocalFocusManager.current
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.inspect(uri,pickerNamespace)}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)model.export(uri,exportNamespace,exportArchived)}
    val requestBack:()->Unit={if(!state.busy)when{state.editing->discard=true;state.viewed!=null->model.closeView();state.preview!=null->model.closePreview();else->onBack()}}
    BackHandler{requestBack()}
    LaunchedEffect(state.editing,state.viewed?.id,state.preview?.id,state.page?.offset){scroll.scrollToItem(0);focus.clearFocus()}
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text(stringResource(R.string.aspect_discard))},
        confirmButton={TextButton(enabled=!state.busy,onClick={discard=false;model.cancelEdit()}){Text(stringResource(R.string.cancel_edit))}},
        dismissButton={TextButton(onClick={discard=false}){Text(stringResource(R.string.flash_continue))}})
    if(confirm&&state.preview!=null)AlertDialog(onDismissRequest={confirm=false},
        title={Text(stringResource(R.string.aspect_confirm))},text={Text(stringResource(R.string.aspect_confirm_hint))},
        confirmButton={TextButton(enabled=!state.busy,onClick={confirm=false;model.confirm()}){Text(stringResource(R.string.aspect_save_selected))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel_edit))}})
    val title=stringResource(when{state.editing->R.string.aspect_edit_title;state.viewed!=null->R.string.aspect_view_title;state.preview!=null->R.string.aspect_preview_title;else->R.string.aspect_title})
    Scaffold(modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),topBar={AspectTopBar(title,requestBack,onHelp,!state.busy)}){padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                state.error?.let{item{AspectNotice(it,true)}}
                state.notice?.let{item{AspectNotice(it)}}
                if(state.previewLost)item{AspectNotice(stringResource(R.string.aspect_preview_lost))}
                when {
                    state.editing->{
                        val fields=Json.parseToJsonElement(draft).jsonObject
                        item{Text(stringResource(R.string.aspect_editor_hint))}
                        items(8,key={"edit-"+listOf("imperfective","perfective","russianImperfective","russianPerfective","acceptedImperfective","acceptedPerfective","topic","note")[it]}){i->
                            val field=listOf("imperfective","perfective","russianImperfective","russianPerfective","acceptedImperfective","acceptedPerfective","topic","note")[i]
                            AspectDraftField(field,fields,model,state)
                        }
                        item{
                            AspectHeading(stringResource(R.string.ap_stages))
                            for(stage in 1..3)FilterChip(fields["studyStage"]?.jsonPrimitive?.content==stage.toString(),{model.draftField("studyStage",stage.toString())},enabled=!state.busy,label={Text(stringResource(R.string.ap_stage,stage))})
                            state.fieldErrors["studyStage"]?.let{AspectNotice(it,true)}
                            Text(stringResource(R.string.aspect_relation))
                            for(relation in listOf("core","resultative","bounded"))FilterChip(fields["relationType"]?.jsonPrimitive?.content==relation,{model.draftField("relationType",relation)},enabled=!state.busy,label={Text(aspectRelationLabel(relation))})
                            state.fieldErrors["relationType"]?.let{AspectNotice(it,true)}
                        }
                        item{
                            TextButton(onClick={metadata=!metadata}){Text(stringResource(R.string.aspect_identifiers))}
                            if(metadata||state.fieldErrors.keys.any{it in setOf("namespace","id","kind","sourceId","tableRow")})Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                for(field in listOf("namespace","id"))AspectDraftField(field,fields,model,state,readOnly=model.editingExisting)
                                Text(stringResource(R.string.aspect_source_kind_title))
                                for(kind in listOf("added","presentation"))FilterChip(fields["kind"]?.jsonPrimitive?.content==kind,{model.draftField("kind",kind)},enabled=!state.busy,label={Text(stringResource(if(kind=="added")R.string.aspect_origin_added else R.string.aspect_origin_presentation))})
                                if(fields["kind"]?.jsonPrimitive?.content=="presentation")for(field in listOf("sourceId","tableRow"))AspectDraftField(field,fields,model,state)
                            }
                        }
                        item{
                            TextButton(onClick={examples=!examples}){Text(stringResource(R.string.aspect_optional_examples))}
                            if(examples||state.fieldErrors.keys.any{it.startsWith("example_")})Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                for(side in listOf("imperfective","perfective"))for(lang in listOf("bg","ru"))AspectDraftField("example_${side}_$lang",fields,model,state)
                            }
                        }
                        item{
                            Button(enabled=!state.busy,onClick={focus.clearFocus();model.save()},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_save))}
                            TextButton(enabled=!state.busy,onClick={discard=true}){Text(stringResource(R.string.cancel_edit))}
                        }
                    }
                    state.viewed!=null->{
                        val record=state.viewed!!
                        item{Text(record.namespace);AspectEntry(record.entry,"aspect-pair-${record.id}-${record.version}")}
                        item{
                            Button(enabled=!state.busy,onClick={model.beginEdit(record)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_edit))}
                            OutlinedButton(enabled=!state.busy,onClick={model.archive(record)},modifier=Modifier.fillMaxWidth()){Text(stringResource(if(record.archived)R.string.aspect_restore else R.string.aspect_archive))}
                        }
                    }
                    state.preview!=null->{
                        val preview=state.preview!!
                        item{
                            Text(preview.filename);Text(preview.namespace);Text(stringResource(R.string.aspect_preview_hint))
                            (preview.metadata["editorialStatus"] as? JsonPrimitive)?.takeIf{it.isString}?.let{AspectNotice(it.content)}
                            AspectNotice(stringResource(R.string.aspect_preview_selection,state.selected.size,preview.changes.count{it.kind=="ADD"},preview.changes.count{it.kind=="UPDATE"},preview.changes.count{it.kind=="UNCHANGED"}))
                            Button(enabled=!state.busy&&preview.issues.isEmpty()&&state.selected.isNotEmpty(),onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_confirm))}
                            TextButton(enabled=!state.busy,onClick=model::closePreview){Text(stringResource(R.string.aspect_back))}
                        }
                        preview.issues.forEach{issue->item{AspectNotice(issue.path+": "+issue.message,true)}}
                        items(preview.changes.size,key={preview.changes[it].externalId}){i->
                            val row=preview.changes[i]
                            var details by rememberSaveable(preview.id,row.externalId){mutableStateOf(false)}
                            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                val label=stringResource(when(row.kind){"ADD"->R.string.aspect_addition;"UPDATE"->R.string.aspect_update;else->R.string.aspect_unchanged})
                                AspectCheckbox(stringResource(R.string.aspect_select_record,row.externalId,label),row.externalId in state.selected,!state.busy){model.select(row.externalId,it)}
                                Text(AspectContent.string(row.after,"imperfective")+" — "+AspectContent.string(row.after,"perfective"))
                                Text(AspectContent.string(row.after,"russianImperfective")+" — "+AspectContent.string(row.after,"russianPerfective"))
                                TextButton(onClick={details=!details}){Text(stringResource(if(details)R.string.aspect_hide_details else R.string.aspect_show_details))}
                                if(details){
                                    if(row.kind=="UPDATE"){Text(stringResource(R.string.aspect_before));row.before?.let{AspectEntry(it.entry)};Text(stringResource(R.string.aspect_after))}
                                    AspectEntry(row.after)
                                }
                            }}
                        }
                    }
                    else->{
                        item{
                            Button(enabled=!state.busy,onClick=onPractice,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.ap_title))}
                            OutlinedButton(enabled=!state.busy,onClick=onStatistics,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_statistics_title))}
                            OutlinedButton(enabled=!state.busy,onClick={model.beginEdit()},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_new))}
                            PronunciationSettings()
                        }
                        state.summary?.let{s->item{AspectNotice(stringResource(R.string.aspect_summary,s.created,s.updated,s.unchanged,s.deselected))}}
                        item{
                            OutlinedTextField(filter[1],{model.filterField(1,it)},enabled=!state.busy,label={Text(stringResource(R.string.aspect_search))},modifier=Modifier.fillMaxWidth(),singleLine=true,
                                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();model.reload()}))
                            TextButton(onClick={filters=!filters}){Text(stringResource(if(filters)R.string.aspect_less_filters else R.string.aspect_more_filters))}
                            if(filters)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                for((i,label)in listOf(0 to R.string.aspect_namespace_filter,3 to R.string.aspect_topic_filter))OutlinedTextField(filter[i],{model.filterField(i,it)},enabled=!state.busy,label={Text(stringResource(label))},modifier=Modifier.fillMaxWidth(),singleLine=true,
                                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();model.reload()}))
                                for(stage in listOf("","1","2","3"))FilterChip(filter[2]==stage,{model.filterField(2,stage)},enabled=!state.busy,label={Text(if(stage.isEmpty())stringResource(R.string.aspect_all_stages) else stringResource(R.string.ap_stage,stage.toInt()))})
                                for(relation in listOf("","core","resultative","bounded"))FilterChip(filter[4]==relation,{model.filterField(4,relation)},enabled=!state.busy,label={Text(if(relation.isEmpty())stringResource(R.string.aspect_all_relations) else aspectRelationLabel(relation))})
                                AspectCheckbox(stringResource(R.string.aspect_archived_only),filter[5]=="true",!state.busy){model.filterField(5,it.toString())}
                            }
                            Button(enabled=!state.busy,onClick={focus.clearFocus();model.reload()}){Text(stringResource(R.string.aspect_apply))}
                            if(filter.take(5).any{it.isNotBlank()}||filter[5]=="true")TextButton(enabled=!state.busy,onClick=model::resetFilters){Text(stringResource(R.string.aspect_clear_filters))}
                        }
                        val page=state.page
                        if(page==null)item{if(state.busy)Text(stringResource(R.string.aspect_loading)) else FailureMessage(stringResource(R.string.load_error),{model.reload()})}
                        if(page!=null){
                            item{Text(stringResource(R.string.aspect_count,page.total));if(page.total==0L)Text(stringResource(if(filter.take(5).any{it.isNotBlank()}||filter[5]=="true")R.string.aspect_no_matches else R.string.aspect_empty))}
                            items(page.rows.size,key={page.rows[it].id}){i->val r=page.rows[i]
                                OutlinedButton(enabled=!state.busy,onClick={model.view(r.id)},modifier=Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()){
                                        Text(AspectContent.string(r.entry,"imperfective")+" → "+AspectContent.string(r.entry,"perfective"))
                                        Text(AspectContent.string(r.entry,"russianImperfective")+" → "+AspectContent.string(r.entry,"russianPerfective"))
                                        Text(r.namespace+" / "+AspectContent.string(r.entry,"id"),style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            item{
                                OutlinedButton(enabled=!state.busy&&page.offset>0,onClick={model.reload((page.offset-25).coerceAtLeast(0))},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_previous))}
                                OutlinedButton(enabled=!state.busy&&page.offset+page.rows.size<page.total,onClick={model.reload(page.offset+25)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_next))}
                            }
                        }
                        item{
                            HorizontalDivider();AspectHeading(stringResource(R.string.aspect_transfer));Text(stringResource(R.string.aspect_transfer_hint))
                            OutlinedTextField(namespace,{namespace=it},enabled=!state.busy,label={Text(stringResource(R.string.aspect_namespace))},modifier=Modifier.fillMaxWidth(),singleLine=true,
                                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done,autoCorrectEnabled=false),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
                            Button(enabled=!state.busy&&namespace.isNotBlank(),onClick={pickerNamespace=namespace;open.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_import))}
                            OutlinedButton(enabled=!state.busy&&namespace.isNotBlank(),onClick={model.sample(namespace)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_sample))}
                            AspectCheckbox(stringResource(R.string.aspect_include_archived),includeArchived,!state.busy){includeArchived=it}
                            OutlinedButton(enabled=!state.busy&&namespace.isNotBlank(),onClick={exportNamespace=namespace;exportArchived=includeArchived;create.launch("bulgarian-aspect-pairs.json")},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_export))}
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AspectDraftField(field:String,fields:JsonObject,model:AspectViewModel,state:AspectUiState,readOnly:Boolean=false) {
    val focus=LocalFocusManager.current
    val multiline=field.startsWith("accepted")||field=="note"||field.startsWith("example_")
    val error=state.fieldErrors[field]
    OutlinedTextField(fields[field]?.jsonPrimitive?.content.orEmpty(),{model.draftField(field,it)},enabled=!state.busy,readOnly=readOnly,
        label={Text(stringResource(aspectFields.getValue(field)))},modifier=Modifier.fillMaxWidth(),singleLine=!multiline,minLines=if(multiline)3 else 1,
        isError=error!=null,supportingText={error?.let{Text(it)}},
        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,keyboardType=if(field=="tableRow")KeyboardType.Number else KeyboardType.Text,imeAction=if(multiline)ImeAction.Default else ImeAction.Next),
        keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)},onDone={focus.clearFocus()}))
}
