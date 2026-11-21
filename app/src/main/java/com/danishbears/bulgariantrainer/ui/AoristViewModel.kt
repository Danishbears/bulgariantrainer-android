package com.danishbears.bulgariantrainer.ui

import android.net.Uri
import androidx.lifecycle.*
import com.danishbears.bulgariantrainer.aorist.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AoristUiState(val busy:Boolean=false,val error:String?=null,val page:AoristPage?=null,
    val viewed:AoristRecord?=null,val editing:Boolean=false,val preview:AoristPreview?=null,
    val selected:Set<String> = emptySet(),val previewLost:Boolean=false,val summary:AoristSummary?=null,
    val fieldErrors:Map<String,String> = emptyMap(),val notice:String?=null)

class AoristViewModel(private val repository:AoristRepository,private val documents:AoristDocuments,
    private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(AoristUiState(editing=saved["aoristEditing"]?:false,previewLost=saved["aoristHadPreview"]?:false))
    val state=mutable.asStateFlow()
    val filter=saved.getStateFlow("aoristFilter",listOf("","","","","all","false"))
    val draft=saved.getStateFlow("aoristDraft","{}")
    val editingExisting get()=saved.get<Long>("aoristEditId")!=null
    init{act{refresh();saved.get<Long>("aoristViewId")?.let{id->val record=repository.get(id);mutable.update{it.copy(viewed=record)}}}}
    private fun act(block:suspend()->Unit) {
        if(mutable.value.busy)return
        mutable.update{it.copy(busy=true,error=null,notice=null)}
        viewModelScope.launch {
            try{block()}catch(e:CancellationException){throw e}
            catch(e:AoristException){mutable.update{it.copy(error=e.message)}}
            catch(_:Exception){mutable.update{it.copy(error="Операция не подтверждена. Проверьте данные и повторите запрос.")}}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    fun filterField(index:Int,text:String){if(!mutable.value.busy){val f=filter.value.toMutableList();f[index]=text;saved["aoristFilter"]=f}}
    private suspend fun refresh(offset:Int=0) {
        val f=filter.value;val query=AoristFilter(f[0].ifBlank{null},f[1],f[2].ifBlank{null},f[3].ifBlank{null},f[4].takeIf{it!="all"},f[5]=="true")
        var p=repository.list(query,offset)
        if(offset>0&&p.rows.isEmpty())p=repository.list(query,if(p.total==0L)0 else ((p.total-1)/25*25).toInt())
        mutable.update{it.copy(page=p)}
    }
    fun reload(offset:Int=0)=act{refresh(offset)}
    fun view(id:Long)=act{val r=repository.get(id);saved["aoristViewId"]=id;mutable.update{it.copy(viewed=r)}}
    fun closeView(){if(!mutable.value.busy){saved["aoristViewId"]=null;mutable.update{it.copy(viewed=null)}}}
    fun archive(record:AoristRecord)=act {
        repository.archive(record.id,record.version,!record.archived);saved["aoristViewId"]=null
        mutable.update{it.copy(viewed=null,notice=if(record.archived)"Глагол возвращён из архива." else "Глагол в архиве. Идентификаторы и история сохранены.")}
        refresh(mutable.value.page?.offset?:0)
    }
    fun beginEdit(record:AoristRecord?=null) {
        if(mutable.value.busy)return
        val e=record?.entry
        val value=buildJsonObject {
            put("datasetId",record?.datasetId?:"my-aorist")
            for(f in listOf("id","lemma","russian","aspect","studyGroup","note"))put(f,e?.get(f)?.jsonPrimitive?.content?:when(f){"id"->UUID.randomUUID().toString();"aspect"->"imperfective";"studyGroup"->"manual";else->""})
            put("reflexiveParticle",e?.get("reflexiveParticle")?.takeIf{it!=JsonNull}?.jsonPrimitive?.content.orEmpty())
            for(person in AoristContent.persons)put(person,e?.let{AoristContent.answers(it,person).joinToString("\n")}.orEmpty())
            val source=e?.get("source") as? JsonObject
            for(f in listOf("title","url","section","checkedOn"))put("source_$f",source?.get(f)?.jsonPrimitive?.content?:when(f){"title"->"Личная запись";"section"->"Введено вручную";"checkedOn"->LocalDate.now().toString();else->""})
        }
        saved["aoristDraft"]=value.toString();saved["aoristEditId"]=record?.id;saved["aoristEditVersion"]=record?.version
        saved["aoristEditing"]=true;saved["aoristViewId"]=null
        mutable.update{it.copy(viewed=null,editing=true,error=null,fieldErrors=emptyMap())}
    }
    fun draftField(field:String,value:String) {
        if(!mutable.value.busy){val old=Json.parseToJsonElement(draft.value).jsonObject;saved["aoristDraft"]=JsonObject(old+(field to JsonPrimitive(value))).toString();mutable.update{it.copy(fieldErrors=emptyMap())}}
    }
    fun cancelEdit(){if(!mutable.value.busy){saved["aoristEditing"]=false;mutable.update{it.copy(editing=false)}}}
    fun save() {
        if(mutable.value.busy)return
        val fields=Json.parseToJsonElement(draft.value).jsonObject
        fun text(f:String)=fields[f]?.jsonPrimitive?.content.orEmpty()
        val entry=buildJsonObject {
            for(f in listOf("id","lemma","russian","aspect","studyGroup","note"))put(f,text(f))
            put("tense","aorist");put("reflexiveParticle",text("reflexiveParticle").takeIf{it.isNotEmpty()}?.let(::JsonPrimitive)?:JsonNull)
            put("forms",buildJsonObject{for(person in AoristContent.persons)put(person,JsonArray(text(person).split('\n').map{JsonPrimitive(it.trim())}))})
            put("source",buildJsonObject{for(f in listOf("title","url","section","checkedOn"))put(f,text("source_$f"))})
        }
        val issues=AoristContent.validate(entry).associate{it.path.removePrefix("entry.") to it.message}.toMutableMap()
        try{AoristContent.identifier(text("datasetId"))}catch(e:AoristException){issues["datasetId"]=e.message.orEmpty()}
        if(issues.isNotEmpty()){mutable.update{it.copy(fieldErrors=issues,error="Проверьте поля: "+issues.entries.joinToString{"${it.key}: ${it.value}"})};return}
        act {
            val id=saved.get<Long>("aoristEditId")
            if(id==null)repository.create(text("datasetId"),entry)else repository.update(id,saved.get<Long>("aoristEditVersion")!!,entry)
            saved["aoristEditing"]=false;mutable.update{it.copy(editing=false,notice="Глагол сохранён.")};refresh()
        }
    }
    private fun clearPreview(){saved["aoristHadPreview"]=false;mutable.update{it.copy(preview=null,selected=emptySet(),summary=null)}}
    private suspend fun preview(name:String,bytes:ByteArray) {
        val p=repository.preview(name,bytes);saved["aoristHadPreview"]=true
        mutable.update{it.copy(preview=p,selected=p.changes.filter{r->r.kind==AoristChangeKind.ADD}.map{r->r.externalId}.toSet(),previewLost=false)}
    }
    fun inspect(uri:Uri)=act{clearPreview();val(name,bytes)=documents.read(uri);preview(name,bytes)}
    fun sample()=act{clearPreview();val(name,bytes)=documents.sample();preview(name,bytes)}
    fun closePreview(){if(!mutable.value.busy){clearPreview();mutable.update{it.copy(previewLost=false)}}}
    fun select(id:String,checked:Boolean){if(!mutable.value.busy)mutable.update{it.copy(selected=if(checked)it.selected+id else it.selected-id)}}
    fun confirm()=act {
        val p=mutable.value.preview?:return@act
        val result=repository.confirm(p,mutable.value.selected);saved["aoristHadPreview"]=false
        mutable.update{it.copy(preview=null,selected=emptySet(),summary=result)};refresh()
    }
    fun export(uri:Uri,datasetId:String,archived:Boolean)=act {
        documents.write(uri,repository.export(datasetId,archived));mutable.update{it.copy(notice="Учебный JSON аориста сохранён. Это не резервная копия прогресса.")}
    }
}
