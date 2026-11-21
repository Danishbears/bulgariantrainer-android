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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aorist.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AoristScreen(model:AoristViewModel,onBack:()->Unit,onPractice:()->Unit={},onHelp:()->Unit={},onStatistics:()->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val focus=LocalFocusManager.current;val scroll=rememberLazyListState()
    var exportDataset by rememberSaveable{mutableStateOf("bg-aorist-core-40")}
    var includeArchived by rememberSaveable{mutableStateOf(false)}
    var pendingExport by rememberSaveable{mutableStateOf("")}
    var pendingArchive by rememberSaveable{mutableStateOf(false)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    var discard by rememberSaveable{mutableStateOf(false)}
    var filters by rememberSaveable{mutableStateOf(false)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.inspect(uri)}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)model.export(uri,pendingExport,pendingArchive)}
    val back:()->Unit={if(!state.busy)when{state.editing->discard=true;state.viewed!=null->model.closeView();state.preview!=null->model.closePreview();else->onBack()}}
    BackHandler{back()}
    LaunchedEffect(state.editing,state.viewed?.id,state.preview?.id,state.page?.offset){focus.clearFocus();scroll.scrollToItem(0)}
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text(stringResource(R.string.aorist_discard))},
        confirmButton={TextButton(enabled=!state.busy,onClick={discard=false;model.cancelEdit()}){Text(stringResource(R.string.cancel_edit))}},
        dismissButton={TextButton(onClick={discard=false}){Text(stringResource(R.string.flash_continue))}})
    if(confirm&&state.preview!=null)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.aorist_confirm))},
        text={Text(stringResource(R.string.aorist_confirm_hint,state.selected.size))},
        confirmButton={TextButton(enabled=!state.busy,onClick={confirm=false;model.confirm()}){Text(stringResource(R.string.aorist_save_selected))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel_edit))}})
    Scaffold(modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        AspectTopBar(stringResource(R.string.aorist_title),back,enabled=!state.busy)
    }){padding->Column(Modifier.fillMaxSize().padding(padding)) {
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            state.error?.let{item{AspectNotice(it,true)}}
            state.notice?.let{item{AspectNotice(it)}}
            if(state.previewLost)item{AspectNotice(stringResource(R.string.aorist_preview_lost))}
            when {
                state.editing->{
                    val fields=Json.parseToJsonElement(draft).jsonObject
                    item{Text(stringResource(R.string.aorist_edit_hint))}
                    val editFields=listOf("lemma","russian","aspect","reflexiveParticle","studyGroup","note")+AoristContent.persons+
                        listOf("datasetId","id","source_title","source_url","source_section","source_checkedOn")
                    items(editFields.size,key={editFields[it]}){i->val field=editFields[i]
                        val multiline=field in AoristContent.persons||field=="note"
                        val value=fields[field]?.jsonPrimitive?.content.orEmpty()
                        val error=state.fieldErrors.entries.firstOrNull{(path,_)->path==field||path.startsWith("$field.")||path.startsWith("forms.$field")||path==field.replace("source_","source.")}?.value
                        if(field=="aspect")AoristAspectSelector(value.ifBlank{null},!state.busy,includeAll=false){model.draftField(field,it.orEmpty())}
                        else OutlinedTextField(value,{model.draftField(field,it)},
                            enabled=!state.busy,readOnly=model.editingExisting&&field in setOf("id","datasetId"),
                            label={Text(aoristFieldLabel(field))},modifier=Modifier.fillMaxWidth(),singleLine=!multiline,minLines=if(multiline)2 else 1,
                            isError=error!=null,supportingText=error?.let{message->{Text(message)}},
                            keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,imeAction=if(multiline)ImeAction.Default else ImeAction.Next))
                    }
                    item{Button(enabled=!state.busy,onClick={focus.clearFocus();model.save()},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_save))}
                        TextButton(enabled=!state.busy,onClick={discard=true}){Text(stringResource(R.string.cancel_edit))}}
                }
                state.viewed!=null->{
                    val r=state.viewed!!
                    item{Text(r.datasetId+" / "+AoristContent.text(r.entry,"id"));AoristEntry(r.entry)}
                    item{Button(enabled=!state.busy,onClick={model.beginEdit(r)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_edit))}
                        OutlinedButton(enabled=!state.busy,onClick={model.archive(r)},modifier=Modifier.fillMaxWidth()){Text(stringResource(if(r.archived)R.string.aorist_restore else R.string.aorist_archive))}}
                }
                state.preview!=null->{
                    val p=state.preview!!
                    item{Text(p.filename);Text(stringResource(R.string.aorist_preview_dataset,
                            (p.metadata["datasetId"] as? JsonPrimitive)?.takeIf{it.isString}?.content.orEmpty(),p.metadata["datasetRevision"]?.toString().orEmpty()))
                        Text(stringResource(R.string.aorist_preview_hint))
                        Text(stringResource(R.string.aorist_preview_counts,p.changes.count{it.kind==AoristChangeKind.ADD},p.changes.count{it.kind==AoristChangeKind.UNCHANGED},p.changes.count{it.kind==AoristChangeKind.UPDATE}))
                        if(p.metadataChanged)AspectNotice(stringResource(R.string.aorist_metadata_changed))
                        Button(enabled=!state.busy&&p.issues.isEmpty()&&state.selected.isNotEmpty(),onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_confirm))}}
                    p.issues.forEach{issue->item{AspectNotice(issue.path+": "+issue.message,true)}}
                    items(p.changes.size,key={p.changes[it].externalId}){i->val row=p.changes[i]
                        var details by rememberSaveable(p.id,row.externalId){mutableStateOf(false)}
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            val kind=stringResource(when(row.kind){AoristChangeKind.ADD->R.string.aorist_new;AoristChangeKind.UNCHANGED->R.string.aorist_unchanged;AoristChangeKind.UPDATE->R.string.aorist_changed})
                            AspectCheckbox(row.externalId+" — "+kind,row.externalId in state.selected,!state.busy){model.select(row.externalId,it)}
                            Text(AoristContent.text(row.after,"lemma")+" — "+AoristContent.text(row.after,"russian"))
                            TextButton(onClick={details=!details}){Text(stringResource(R.string.aspect_show_details))}
                            if(details){row.before?.let{Text(stringResource(R.string.aspect_before));AoristEntry(it.entry)};Text(stringResource(R.string.aspect_after));AoristEntry(row.after)}
                        }}
                    }
                }
                else->{
                    item{Button(enabled=!state.busy,onClick=onPractice,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aop_title))}
                        TextButton(enabled=!state.busy,onClick=onHelp){Text(stringResource(R.string.aorist_help_title))}
                        TextButton(enabled=!state.busy,onClick=onStatistics){Text(stringResource(R.string.aos_title))}
                        Text(stringResource(R.string.aorist_content_stage))
                        Button(enabled=!state.busy,onClick={model.beginEdit()},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_create))}}
                    state.summary?.let{s->item{AspectNotice(stringResource(R.string.aorist_summary,s.created,s.updated,s.unchanged,s.deselected))}}
                    item{
                        OutlinedTextField(filter[1],{model.filterField(1,it)},label={Text(stringResource(R.string.aorist_search))},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),singleLine=true,
                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();model.reload()}))
                        TextButton(onClick={filters=!filters}){Text(stringResource(R.string.aorist_filters))}
                        if(filters)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            AoristAspectSelector(filter[2].ifBlank{null},!state.busy){model.filterField(2,it.orEmpty())}
                            for((i,label)in listOf(0 to R.string.aorist_dataset_filter,3 to R.string.aorist_group_filter,4 to R.string.aorist_particle_filter))
                                OutlinedTextField(filter[i],{model.filterField(i,it)},enabled=!state.busy,label={Text(stringResource(label))},singleLine=true,modifier=Modifier.fillMaxWidth())
                            AspectCheckbox(stringResource(R.string.aorist_archived_only),filter[5]=="true",!state.busy){model.filterField(5,it.toString())}
                        }
                        Button(enabled=!state.busy,onClick={focus.clearFocus();model.reload()}){Text(stringResource(R.string.aorist_apply))}
                    }
                    val page=state.page
                    if(page==null)item{if(state.busy)CircularProgressIndicator()else FailureMessage(stringResource(R.string.load_error),{model.reload()})}
                    if(page!=null){
                        item{Text(stringResource(R.string.aorist_count,page.total));if(page.total==0L)Text(stringResource(R.string.aorist_empty))}
                        items(page.rows.size,key={page.rows[it].id}){i->val r=page.rows[i]
                            OutlinedButton(enabled=!state.busy,onClick={model.view(r.id)},modifier=Modifier.fillMaxWidth()){
                                Column(Modifier.fillMaxWidth()){Text(AoristContent.text(r.entry,"lemma")+" — "+AoristContent.text(r.entry,"russian"));Text(r.datasetId+" / "+AoristContent.text(r.entry,"id"),style=MaterialTheme.typography.bodySmall)}
                            }}
                        item{OutlinedButton(enabled=!state.busy&&page.offset>0,onClick={model.reload((page.offset-25).coerceAtLeast(0))},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_previous))}
                            OutlinedButton(enabled=!state.busy&&page.offset+page.rows.size<page.total,onClick={model.reload(page.offset+25)},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aspect_next))}}
                    }
                    item{HorizontalDivider();Text(stringResource(R.string.aorist_transfer_hint))
                        Button(enabled=!state.busy,onClick={open.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_import))}
                        OutlinedButton(enabled=!state.busy,onClick=model::sample,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_sample))}
                        OutlinedTextField(exportDataset,{exportDataset=it},enabled=!state.busy,label={Text(stringResource(R.string.aorist_dataset))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        AspectCheckbox(stringResource(R.string.aorist_include_archived),includeArchived,!state.busy){includeArchived=it}
                        OutlinedButton(enabled=!state.busy&&exportDataset.isNotBlank(),onClick={pendingExport=exportDataset;pendingArchive=includeArchived;create.launch("bulgarian-aorist.json")},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.aorist_export))}
                    }
                }
            }
        }
    }}
}

@Composable internal fun aoristFieldLabel(field:String):String=stringResource(when(field){
    "lemma"->R.string.aorist_lemma;"russian"->R.string.aorist_russian;"aspect"->R.string.aorist_aspect
    "studyGroup"->R.string.aorist_group;"note"->R.string.aorist_note;"reflexiveParticle"->R.string.aorist_particle
    "datasetId"->R.string.aorist_dataset;"id"->R.string.aorist_id
    "source_title"->R.string.aorist_source_title;"source_url"->R.string.aorist_source_url
    "source_section"->R.string.aorist_source_section;"source_checkedOn"->R.string.aorist_source_date
    "1sg"->R.string.aorist_1sg;"2sg"->R.string.aorist_2sg;"3sg"->R.string.aorist_3sg
    "1pl"->R.string.aorist_1pl;"2pl"->R.string.aorist_2pl;else->R.string.aorist_3pl
})
@Composable internal fun AoristEntry(e:JsonObject) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(AoristContent.text(e,"lemma")+" — "+AoristContent.text(e,"russian"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()})
        Text(stringResource(R.string.aop_tense))
        Text(stringResource(R.string.aorist_table),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})
        // Weighted wrapping columns: no fixed row height, horizontal clipping or synthetic sentences.
        for(person in AoristContent.persons){
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true){},horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(aoristFieldLabel(person),Modifier.weight(1f))
                Text(AoristContent.answers(e,person).joinToString("; "),Modifier.weight(1f))
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.aorist_aspect_display,aoristAspectLabel(AoristContent.text(e,"aspect"))))
        for(field in listOf("studyGroup","note"))Text(aoristFieldLabel(field)+": "+AoristContent.text(e,field))
        val source=e.getValue("source").jsonObject
        for(f in listOf("title","url","section","checkedOn"))Text(aoristFieldLabel("source_$f")+": "+AoristContent.text(source,f),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun aoristPersonLabel(person:String)=stringResource(when(person){
    "1sg"->R.string.aorist_person_1sg;"2sg"->R.string.aorist_person_2sg;"3sg"->R.string.aorist_person_3sg
    "1pl"->R.string.aorist_person_1pl;"2pl"->R.string.aorist_person_2pl;else->R.string.aorist_person_3pl
})
@Composable private fun aoristAspectLabel(aspect:String)=stringResource(if(aspect=="perfective")R.string.aop_perfective else R.string.aop_imperfective)
@Composable internal fun AoristAspectSelector(value:String?,enabled:Boolean,includeAll:Boolean=true,onValue:(String?)->Unit) {
    Column {
        Text(stringResource(R.string.aorist_aspect_title))
        for(aspect in if(includeAll)listOf(null,"imperfective","perfective")else listOf("imperfective","perfective"))
            FilterChip(value==aspect,{onValue(aspect)},enabled=enabled,label={Text(if(aspect==null)stringResource(R.string.aop_both_aspects)else aoristAspectLabel(aspect))})
    }
}
