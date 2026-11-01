package com.danishbears.bulgariantrainer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    "topic" to R.string.aspect_topic,"studyStage" to R.string.aspect_stage,"relationType" to R.string.aspect_relation,
    "note" to R.string.aspect_note,"kind" to R.string.aspect_provenance_kind,"sourceId" to R.string.aspect_source_id,
    "tableRow" to R.string.aspect_table_row,"example_imperfective_bg" to R.string.aspect_example_ibg,
    "example_imperfective_ru" to R.string.aspect_example_iru,"example_perfective_bg" to R.string.aspect_example_pbg,
    "example_perfective_ru" to R.string.aspect_example_pru)
@Composable
fun AspectScreen(model:AspectViewModel){
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    var namespace by rememberSaveable{mutableStateOf("bulgarian-aspect-pairs-100")}
    var includeArchived by rememberSaveable{mutableStateOf(false)}
    var pickerNamespace by rememberSaveable{mutableStateOf("")}
    var exportNamespace by rememberSaveable{mutableStateOf("")}
    var exportArchived by rememberSaveable{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    var discard by remember{mutableStateOf(false)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.inspect(uri,pickerNamespace)}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)model.export(uri,exportNamespace,exportArchived)}
    BackHandler(state.editing){discard=true}
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text(stringResource(R.string.aspect_discard))},
        confirmButton={TextButton(onClick={discard=false;model.cancelEdit()}){Text(stringResource(R.string.cancel_edit))}},
        dismissButton={TextButton(onClick={discard=false}){Text(stringResource(R.string.flash_continue))}})
    if(confirm&&state.preview!=null)AlertDialog(onDismissRequest={confirm=false},
        title={Text(stringResource(R.string.aspect_confirm))},text={Text(stringResource(R.string.aspect_confirm_hint))},
        confirmButton={TextButton(enabled=!state.busy,onClick={confirm=false;model.confirm()}){Text(stringResource(R.string.aspect_save_selected))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel_edit))}})
    Column(Modifier.fillMaxSize().imePadding()){
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text(stringResource(R.string.aspect_title),style=MaterialTheme.typography.headlineMedium)}
            state.error?.let{item{FailureMessage(it)}}
            if(state.previewLost)item{Text(stringResource(R.string.aspect_preview_lost))}
            when{
                state.editing -> {
                    val fields=Json.parseToJsonElement(draft).jsonObject
                    item{Text(stringResource(R.string.aspect_editor_hint))}
                    items(aspectFields.size,key={"edit-"+aspectFields.keys.elementAt(it)}){index->
                        val field=aspectFields.keys.elementAt(index)
                        val existing=model.editingExisting
                        OutlinedTextField(fields[field]?.jsonPrimitive?.content.orEmpty(),{model.draftField(field,it)},enabled=!state.busy,
                            readOnly=existing&&(field=="namespace"||field=="id"),
                            label={Text(stringResource(aspectFields.getValue(field)))},modifier=Modifier.fillMaxWidth(),
                            minLines=if(field.startsWith("accepted")||field=="note")3 else 1)
                    }
                    item{
                        Button(enabled=!state.busy,onClick=model::save){Text(stringResource(R.string.aspect_save))}
                        TextButton(enabled=!state.busy,onClick={discard=true}){Text(stringResource(R.string.cancel_edit))}
                    }
                }
                state.viewed!=null -> {
                    val record=state.viewed!!
                    item{AspectEntry(record.entry)}
                    item{
                        Button(enabled=!state.busy,onClick={model.beginEdit(record)}){Text(stringResource(R.string.aspect_edit))}
                        OutlinedButton(enabled=!state.busy,onClick={model.archive(record)}){Text(stringResource(if(record.archived)R.string.aspect_restore else R.string.aspect_archive))}
                        TextButton(onClick=model::closeView){Text(stringResource(R.string.aspect_back))}
                    }
                }
                state.preview!=null -> {
                    val preview=state.preview!!
                    item{Text(preview.filename);Text(preview.namespace);Text(stringResource(R.string.aspect_preview_hint))}
                    preview.issues.forEach{issue->item{Text(issue.path+": "+issue.message,color=MaterialTheme.colorScheme.error)}}
                    items(preview.changes.size,key={preview.changes[it].externalId}){i->
                        val row=preview.changes[i]
                        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Row{Checkbox(row.externalId in state.selected,{model.select(row.externalId,it)},enabled=!state.busy);Text(row.externalId+" · "+stringResource(when(row.kind){"ADD"->R.string.aspect_addition;"UPDATE"->R.string.aspect_update;else->R.string.aspect_unchanged}))}
                            if(row.kind=="UPDATE"){Text(stringResource(R.string.aspect_before));row.before?.let{AspectEntry(it.entry)};Text(stringResource(R.string.aspect_after))}
                            AspectEntry(row.after)
                        }}
                    }
                    item{
                        Button(enabled=!state.busy&&preview.issues.isEmpty()&&state.selected.isNotEmpty(),onClick={confirm=true}){Text(stringResource(R.string.aspect_confirm))}
                        TextButton(enabled=!state.busy,onClick=model::closePreview){Text(stringResource(R.string.aspect_back))}
                    }
                }
                else -> {
                    state.summary?.let{s->item{Text(stringResource(R.string.aspect_summary,s.created,s.updated,s.unchanged,s.deselected))}}
                    item{
                        Text(stringResource(R.string.aspect_filter),style=MaterialTheme.typography.titleLarge)
                        for((i,label) in listOf(R.string.aspect_namespace_filter,R.string.aspect_search,R.string.aspect_stage_filter,R.string.aspect_topic_filter,R.string.aspect_relation_filter).withIndex())
                            OutlinedTextField(filter[i],{model.filterField(i,it)},enabled=!state.busy,label={Text(stringResource(label))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Row{Checkbox(filter[5]=="true",{model.filterField(5,it.toString())},enabled=!state.busy);Text(stringResource(R.string.aspect_archived_only))}
                        Button(enabled=!state.busy,onClick={model.reload()}){Text(stringResource(R.string.aspect_apply))}
                        OutlinedButton(enabled=!state.busy,onClick={model.beginEdit()}){Text(stringResource(R.string.aspect_new))}
                    }
                    val page=state.page
                    if(page==null&&!state.busy)item{Text(stringResource(R.string.load_error))}
                    if(page!=null){
                        item{Text(stringResource(R.string.aspect_count,page.total));if(page.total==0L)Text(stringResource(R.string.aspect_empty))}
                        items(page.rows.size,key={page.rows[it].id}){i->
                            val r=page.rows[i]
                            OutlinedButton(enabled=!state.busy,onClick={model.view(r.id)},modifier=Modifier.fillMaxWidth()){
                                Column{Text(AspectContent.string(r.entry,"imperfective")+" → "+AspectContent.string(r.entry,"perfective"));Text(AspectContent.string(r.entry,"russianImperfective")+" → "+AspectContent.string(r.entry,"russianPerfective"));Text(r.namespace+" / "+AspectContent.string(r.entry,"id"))}
                            }
                        }
                        item{
                            OutlinedButton(enabled=!state.busy&&page.offset>0,onClick={model.reload((page.offset-25).coerceAtLeast(0))}){Text(stringResource(R.string.aspect_previous))}
                            OutlinedButton(enabled=!state.busy&&page.offset+page.rows.size<page.total,onClick={model.reload(page.offset+25)}){Text(stringResource(R.string.aspect_next))}
                        }
                    }
                    item{
                        HorizontalDivider();Text(stringResource(R.string.aspect_transfer),style=MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.aspect_transfer_hint))
                        OutlinedTextField(namespace,{namespace=it},enabled=!state.busy,label={Text(stringResource(R.string.aspect_namespace))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Button(enabled=!state.busy&&namespace.isNotBlank(),onClick={pickerNamespace=namespace;open.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text(stringResource(R.string.aspect_import))}
                        OutlinedButton(enabled=!state.busy&&namespace.isNotBlank(),onClick={model.sample(namespace)}){Text(stringResource(R.string.aspect_sample))}
                        Row{Checkbox(includeArchived,{includeArchived=it},enabled=!state.busy);Text(stringResource(R.string.aspect_include_archived))}
                        OutlinedButton(enabled=!state.busy&&namespace.isNotBlank(),onClick={exportNamespace=namespace;exportArchived=includeArchived;create.launch("bulgarian-aspect-pairs.json")}){Text(stringResource(R.string.aspect_export))}
                    }
                }
            }
        }
    }
}
@Composable private fun AspectEntry(entry:JsonObject){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        for((key,value) in entry){
            val label=aspectFields[key]?.let{stringResource(it)}?:when(key){"provenance"->stringResource(R.string.aspect_provenance);else->stringResource(R.string.aspect_examples)}
            val text=when(value){is JsonPrimitive->value.content;is JsonArray->value.joinToString("; "){it.jsonPrimitive.content};else->value.toString()}
            Text(label+": "+text)
        }
    }
}
