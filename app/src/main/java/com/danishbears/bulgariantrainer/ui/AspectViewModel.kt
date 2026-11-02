package com.danishbears.bulgariantrainer.ui

import android.net.Uri
import androidx.lifecycle.*
import com.danishbears.bulgariantrainer.aspect.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AspectUiState(val busy:Boolean=false,val error:String?=null,val page:AspectPage?=null,val preview:AspectPreview?=null,
    val selected:Set<String> = emptySet(),val summary:AspectSummary?=null,val viewed:AspectRecord?=null,val editing:Boolean=false,val previewLost:Boolean=false,
    val fieldErrors:Map<String,String> = emptyMap(),val notice:String?=null)
class AspectViewModel(private val repository:AspectRepository,private val documents:AspectDocuments,private val saved:SavedStateHandle):ViewModel(){
    private val mutable=MutableStateFlow(AspectUiState(editing=saved["aspectEditing"]?:false,previewLost=saved["aspectHadPreview"]?:false))
    val state=mutable.asStateFlow()
    val filter=saved.getStateFlow("aspectFilter",listOf("","","","","","false"))
    val draft=saved.getStateFlow("aspectDraft","{}")
    init{act{refresh();saved.get<Long>("aspectViewId")?.let{id->val record=repository.get(id);mutable.update{it.copy(viewed=record)}}}}
    fun filterField(index:Int,text:String){if(!mutable.value.busy){val next=filter.value.toMutableList();next[index]=text;saved["aspectFilter"]=next.toList()}}
    private fun currentFilter():AspectFilter{
        val f=filter.value
        if(f[2].isNotEmpty()&&f[2].toIntOrNull() !in 1..3)throw AspectException("Стадия фильтра — 1, 2 или 3.")
        return AspectFilter(f[0].ifBlank{null},f[1],f[2].toIntOrNull(),f[3].ifBlank{null},f[4].ifBlank{null},f[5]=="true")
    }
    private fun act(block:suspend()->Unit){
        if(mutable.value.busy)return
        mutable.update{it.copy(busy=true,error=null,notice=null)}
        viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:AspectException){mutable.update{it.copy(error=e.message)}}catch(_:Exception){mutable.update{it.copy(error="Не удалось выполнить операцию. Данные не подтверждены; повторите после проверки.")}}
            finally{mutable.update{it.copy(busy=false)}}}
    }
    private suspend fun refresh(offset:Int=0){val result=repository.list(currentFilter(),offset);mutable.update{it.copy(page=result)}}
    fun reload(offset:Int=0)=act{refresh(offset)}
    fun view(id:Long)=act{val pair=repository.get(id);saved["aspectViewId"]=id;mutable.update{it.copy(viewed=pair)}}
    fun closeView(){if(!mutable.value.busy){saved["aspectViewId"]=null;mutable.update{it.copy(viewed=null)}}}
    fun archive(record:AspectRecord)=act{repository.archive(record.id,record.version,!record.archived);saved["aspectViewId"]=null;mutable.update{it.copy(viewed=null,notice=if(record.archived)"Пара возвращена из архива." else "Пара перенесена в архив. История сохранена.")};refresh(mutable.value.page?.offset?:0)}
    fun resetFilters(){if(!mutable.value.busy){saved["aspectFilter"]=listOf("","","","","","false");reload()}}
    fun beginEdit(record:AspectRecord?=null){
        if(mutable.value.busy)return
        val e=record?.entry
        val data=buildJsonObject{
            put("namespace",record?.namespace?:filter.value[0].ifBlank{"my-aspect-pairs"})
            for(f in listOf("id","imperfective","perfective","russianImperfective","russianPerfective","topic","note","relationType"))
                put(f,e?.get(f)?.jsonPrimitive?.content?:when(f){"id"->UUID.randomUUID().toString();"relationType"->"core";else->""})
            put("studyStage",e?.get("studyStage")?.jsonPrimitive?.content?:"1")
            for(f in listOf("acceptedImperfective","acceptedPerfective"))put(f,(e?.get(f) as? JsonArray)?.joinToString("\n"){it.jsonPrimitive.content}?:"")
            val p=e?.get("provenance") as? JsonObject
            put("kind",p?.get("kind")?.jsonPrimitive?.content?:"added");put("sourceId",p?.get("sourceId")?.takeIf{it!=JsonNull}?.jsonPrimitive?.content?:"");put("tableRow",p?.get("tableRow")?.takeIf{it!=JsonNull}?.jsonPrimitive?.content?:"")
            for(side in listOf("imperfective","perfective"))for(lang in listOf("bg","ru"))
                put("example_"+side+"_"+lang,((e?.get("examples") as? JsonObject)?.get(side) as? JsonObject)?.get(lang)?.jsonPrimitive?.content?:"")
        }
        saved["aspectDraft"]=data.toString();saved["aspectEditId"]=record?.id;saved["aspectEditVersion"]=record?.version
        saved["aspectViewId"]=null;saved["aspectEditing"]=true;mutable.update{it.copy(editing=true,viewed=null,error=null,fieldErrors=emptyMap(),notice=null)}
    }
    val editingExisting:Boolean get()=saved.get<Long>("aspectEditId")!=null
    fun draftField(field:String,text:String){
        if(mutable.value.busy)return
        val old=Json.parseToJsonElement(draft.value).jsonObject
        saved["aspectDraft"]=JsonObject(old+(field to JsonPrimitive(text))).toString()
        mutable.update{it.copy(fieldErrors=it.fieldErrors-field)}
    }
    fun cancelEdit(){if(!mutable.value.busy){saved["aspectEditing"]=false;mutable.update{it.copy(editing=false)}}}
    private fun entry(fields:JsonObject):JsonObject{
        fun text(f:String)=fields[f]?.jsonPrimitive?.content.orEmpty()
        return buildJsonObject{
            for(f in listOf("id","imperfective","perfective","russianImperfective","russianPerfective","topic","note","relationType"))put(f,text(f))
            put("studyStage",text("studyStage").toIntOrNull()?:throw AspectException("Нужна стадия 1–3."))
            for(f in listOf("acceptedImperfective","acceptedPerfective"))
                put(f,JsonArray(text(f).split('\n').filter{it.isNotBlank()}.map(::JsonPrimitive)))
            put("provenance",buildJsonObject{
                put("kind",text("kind"))
                if(text("kind")=="presentation"){put("sourceId",text("sourceId"));put("tableRow",text("tableRow").toIntOrNull()?:throw AspectException("Нужен номер строки источника."))}
            })
            if(listOf("imperfective","perfective").any{s->listOf("bg","ru").any{text("example_"+s+"_"+it).isNotEmpty()}})
                put("examples",buildJsonObject{for(s in listOf("imperfective","perfective"))put(s,buildJsonObject{for(l in listOf("bg","ru"))put(l,text("example_"+s+"_"+l))})})
        }
    }
    fun save(){
        if(mutable.value.busy)return
        val fields=Json.parseToJsonElement(draft.value).jsonObject
        val e=try{entry(fields)}catch(e:AspectException){mutable.update{it.copy(error=e.message)};return}
        val issues=AspectContent.validate(e).associate{issue->
            val path=issue.path.removePrefix("entry.").substringBefore('[')
            val key=if(path.startsWith("examples."))"example_"+path.removePrefix("examples.").replace('.','_') else path.removePrefix("provenance.")
            key to issue.message
        }.toMutableMap()
        try{AspectContent.namespace(fields.getValue("namespace").jsonPrimitive.content)}catch(e:AspectException){issues["namespace"]=e.message.orEmpty()}
        if(issues.isNotEmpty()){mutable.update{it.copy(fieldErrors=issues,error="Проверьте отмеченные поля.")};return}
        act{
        val id=saved.get<Long>("aspectEditId")
        if(id==null)repository.create(fields.getValue("namespace").jsonPrimitive.content,e)
        else repository.update(id,saved.get<Long>("aspectEditVersion")!!,e)
        saved["aspectEditing"]=false;mutable.update{it.copy(editing=false,fieldErrors=emptyMap(),notice="Пара сохранена.")};refresh()
        }
    }
    fun inspect(uri:Uri,namespace:String)=act{clearPreview();val (name,bytes)=documents.read(uri);preview(namespace,name,bytes)}
    fun sample(namespace:String)=act{clearPreview();val(name,bytes)=documents.sample();preview(namespace,name,bytes)}
    private fun clearPreview(){saved["aspectHadPreview"]=false;mutable.update{it.copy(preview=null,selected=emptySet(),summary=null)}}
    private suspend fun preview(namespace:String,name:String,bytes:ByteArray){
        val p=repository.preview(namespace,name,bytes)
        saved["aspectHadPreview"]=true
        mutable.update{it.copy(preview=p,selected=p.changes.filter{r->r.kind=="ADD"}.map{r->r.externalId}.toSet(),summary=null,previewLost=false)}
    }
    fun select(id:String,value:Boolean){if(!mutable.value.busy)mutable.update{it.copy(selected=if(value)it.selected+id else it.selected-id)}}
    fun closePreview(){if(!mutable.value.busy){saved["aspectHadPreview"]=false;mutable.update{it.copy(preview=null,summary=null)}}}
    fun confirm()=act{
        val p=mutable.value.preview?:return@act;val summary=repository.confirm(p,mutable.value.selected)
        saved["aspectHadPreview"]=false;mutable.update{it.copy(preview=null,summary=summary)};refresh()
    }
    fun export(uri:Uri,namespace:String,archived:Boolean)=act{val bytes=repository.export(namespace,archived);documents.write(uri,bytes);mutable.update{it.copy(notice="Учебный JSON сохранён. Сессии и прогресс не входят в этот файл.")}}
}
