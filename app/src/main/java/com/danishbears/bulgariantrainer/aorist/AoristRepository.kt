package com.danishbears.bulgariantrainer.aorist

import com.danishbears.bulgariantrainer.data.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class AoristRecord(val id:Long,val datasetId:String,val version:Long,val archived:Boolean,val entry:JsonObject)
data class AoristDataset(val datasetId:String,val revision:Long,val count:Long)
data class AoristFilter(val datasetId:String?=null,val query:String="",val aspect:String?=null,
    val studyGroup:String?=null,val reflexiveParticle:String?=null,val archived:Boolean=false)
data class AoristPage(val rows:List<AoristRecord>,val total:Long,val offset:Int)
enum class AoristChangeKind { ADD, UNCHANGED, UPDATE }
data class AoristChange(val externalId:String,val kind:AoristChangeKind,val before:AoristRecord?,val after:JsonObject)
data class AoristSummary(val created:Int,val updated:Int,val unchanged:Int,val deselected:Int)
class AoristPreview internal constructor(val id:String,val filename:String,val metadata:JsonObject,
    val issues:List<AoristIssue>,val changes:List<AoristChange>,val metadataChanged:Boolean,
    internal val fileHash:String,internal val datasetInternalId:Long?,internal val datasetVersion:Long?,
    internal val expires:Long,internal val epoch:Long)

class AoristRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC()) {
    private val dao=db.aoristDao()
    suspend fun datasets():List<AoristDataset> = db.withContentTransaction {
        dao.datasets().map{AoristDataset(it.externalId,it.revision,dao.count(it.externalId,false,null,null,null,null))}
    }
    suspend fun list(filter:AoristFilter=AoristFilter(),offset:Int=0,limit:Int=25):AoristPage {
        require(offset>=0&&limit in 1..100)
        filter.datasetId?.let(AoristContent::identifier)
        if(filter.query.length>255||filter.aspect!=null&&filter.aspect !in setOf("imperfective","perfective")
            ||filter.studyGroup!=null&&(filter.studyGroup.isBlank()||filter.studyGroup.length>100)
            ||filter.reflexiveParticle!=null&&filter.reflexiveParticle !in setOf("","се","си"))throw AoristException("Некорректный фильтр аориста.")
        val pattern=filter.query.takeIf{it.isNotBlank()}?.let(VocabularyText::searchPattern)
        return db.withContentTransaction { AoristPage(
            dao.list(filter.datasetId,filter.archived,filter.aspect,filter.studyGroup,filter.reflexiveParticle,pattern,limit,offset).map(::record),
            dao.count(filter.datasetId,filter.archived,filter.aspect,filter.studyGroup,filter.reflexiveParticle,pattern),offset) }
    }
    suspend fun get(id:Long):AoristRecord=db.withContentTransaction{record(dao.get(id)?:throw AoristException("Глагол не найден."))}
    private fun validate(entry:JsonObject) { AoristContent.validate(entry).firstOrNull()?.let{throw AoristException("${it.path}: ${it.message}")} }
    suspend fun create(datasetId:String,entry:JsonObject):AoristRecord {
        AoristContent.identifier(datasetId)
        val content=if("id" in entry)entry else JsonObject(entry+("id" to JsonPrimitive(UUID.randomUUID().toString())))
        validate(content)
        return db.withContentTransaction {
            val at=clock.millis()
            val dataset=dao.dataset(datasetId)?:run{val id=dao.insertDataset(AoristDatasetEntity(externalId=datasetId,revision=1,
                metadata=AoristContent.manualMetadata(datasetId).toString(),createdAt=at,updatedAt=at));dao.datasetById(id)!!}
            if(dao.identity(dataset.id,AoristContent.text(content,"id"))!=null)throw AoristException("Внешний ID уже существует, включая архив.")
            val id=write(null,dataset.id,content)
            dao.updateDataset(dataset.copy(version=dataset.version+1,updatedAt=at))
            record(dao.get(id)!!)
        }
    }
    suspend fun update(id:Long,version:Long,entry:JsonObject):AoristRecord {
        validate(entry)
        return db.withContentTransaction {
            val old=dao.get(id)?:throw AoristException("Глагол не найден.")
            if(old.verb.version!=version)throw AoristException("Глагол изменён. Откройте запись заново.")
            if(old.verb.externalId!=AoristContent.text(entry,"id"))throw AoristException("Внешний ID неизменяем.")
            write(old.verb,old.verb.datasetId,entry)
            val d=dao.datasetById(old.verb.datasetId)!!;dao.updateDataset(d.copy(version=d.version+1,updatedAt=clock.millis()))
            record(dao.get(id)!!)
        }
    }
    suspend fun archive(id:Long,version:Long,archived:Boolean):AoristRecord=db.withContentTransaction {
        val old=dao.get(id)?:throw AoristException("Глагол не найден.")
        if(old.verb.version!=version)throw AoristException("Глагол изменён. Обновите список.")
        if(old.verb.archived!=archived) {
            dao.update(old.verb.copy(archived=archived,version=version+1,updatedAt=clock.millis()))
            val d=dao.datasetById(old.verb.datasetId)!!;dao.updateDataset(d.copy(version=d.version+1,updatedAt=clock.millis()))
        }
        record(dao.get(id)!!)
    }
    private suspend fun write(old:AoristVerbEntity?,dataset:Long,e:JsonObject):Long {
        val hash=AoristContent.hash(e)
        if(old?.contentHash==hash)return old.id
        val at=clock.millis()
        val search=VocabularyText.key((listOf("id","lemma","russian","studyGroup").map{AoristContent.text(e,it)}+
            AoristContent.persons.flatMap{AoristContent.answers(e,it)}).joinToString(" "))
        val row=AoristVerbEntity(id=old?.id?:0,datasetId=dataset,externalId=AoristContent.text(e,"id"),
            lemma=AoristContent.text(e,"lemma"),russian=AoristContent.text(e,"russian"),aspect=AoristContent.text(e,"aspect"),
            tense=AoristContent.text(e,"tense"),reflexiveParticle=e.getValue("reflexiveParticle").takeIf{it!=JsonNull}?.jsonPrimitive?.content,
            studyGroup=AoristContent.text(e,"studyGroup"),note=AoristContent.text(e,"note"),source=e.getValue("source").toString(),
            searchText=search,contentHash=hash,archived=old?.archived?:false,version=old?.version?.plus(1)?:0,createdAt=old?.createdAt?:at,updatedAt=at)
        val id=if(old==null)dao.insert(row)else{dao.update(row);old.id}
        val existing=dao.forms(id).associateBy{it.person}
        for(person in AoristContent.persons) {
            // Never delete/recreate grammatical targets: later sessions/progress reference these IDs.
            val form=existing[person]?.id?:dao.insertForm(AoristFormEntity(verbId=id,person=person))
            dao.deleteAnswers(form)
            dao.insertAnswers(AoristContent.answers(e,person).mapIndexed{i,s->AoristAnswerEntity(formId=form,position=i+1,text=s,normalizedKey=AoristContent.key(s))})
        }
        return id
    }
    private fun record(graph:AoristGraph):AoristRecord {
        val p=graph.verb
        return AoristRecord(p.id,graph.namespace,p.version,p.archived,buildJsonObject {
            put("id",p.externalId);put("lemma",p.lemma);put("russian",p.russian);put("aspect",p.aspect);put("tense",p.tense)
            put("reflexiveParticle",p.reflexiveParticle?.let(::JsonPrimitive)?:JsonNull);put("studyGroup",p.studyGroup);put("note",p.note)
            put("source",AoristContent.strict(p.source))
            put("forms",buildJsonObject{for(person in AoristContent.persons){val form=graph.forms.single{it.form.person==person}
                put(person,JsonArray(form.answers.sortedBy{it.position}.map{JsonPrimitive(it.text)}))}})
        })
    }
    suspend fun preview(filename:String,bytes:ByteArray):AoristPreview {
        val epoch=db.contentEpoch.get();val input=bytes.copyOf()
        val parsed=withContext(Dispatchers.Default){AoristContent.parse(input)}
        return db.withContentTransaction {
            if(epoch!=db.contentEpoch.get())throw AoristException("Данные заменены; выберите файл заново.")
            val dataset=if(parsed.issues.isEmpty())dao.dataset(AoristContent.text(parsed.metadata,"datasetId"))else null
            val changes=if(parsed.issues.isEmpty())parsed.entries.map{e->
                val old=dataset?.let{dao.identity(it.id,AoristContent.text(e,"id"))}?.let{record(dao.get(it.id)!!)}
                AoristChange(AoristContent.text(e,"id"),when{old==null->AoristChangeKind.ADD
                    AoristContent.canonical(old.entry)==AoristContent.canonical(e)->AoristChangeKind.UNCHANGED
                    else->AoristChangeKind.UPDATE},old,e)
            }else emptyList()
            AoristPreview(UUID.randomUUID().toString(),filename,parsed.metadata,parsed.issues,changes,
                dataset!=null&&AoristContent.canonical(AoristContent.strict(dataset.metadata))!=AoristContent.canonical(parsed.metadata),
                AoristContent.hash(input),dataset?.id,dataset?.version,clock.millis()+30*60*1000,epoch)
        }
    }
    suspend fun confirm(preview:AoristPreview,selected:Set<String>,requestId:String=preview.id):AoristSummary=db.withContentTransaction {
        try{require(UUID.fromString(requestId).toString()==requestId)}catch(_:Exception){throw AoristException("Некорректный ID запроса.")}
        if(preview.epoch!=db.contentEpoch.get())throw AoristException("Данные заменены; выберите файл заново.")
        val requestHash=AoristContent.hash(buildJsonObject{put("preview",preview.id);put("selected",JsonArray(selected.sorted().map(::JsonPrimitive)))})
        dao.receipt(requestId)?.let{
            if(it.requestHash!=requestHash)throw AoristException("Запрос уже подтверждён с другим выбором.")
            return@withContentTransaction summary(AoristContent.strict(it.summary).jsonObject)
        }
        if(clock.millis()>=preview.expires)throw AoristException("Предпросмотр истёк. Выберите файл заново.")
        if(preview.issues.isNotEmpty()||selected.isEmpty()||!preview.changes.map{it.externalId}.containsAll(selected))throw AoristException("Нужен корректный файл и непустой выбор известных записей.")
        val external=AoristContent.text(preview.metadata,"datasetId");var dataset=dao.dataset(external)
        if(dataset?.id!=preview.datasetInternalId||dataset?.version!=preview.datasetVersion)throw AoristException("Набор изменился после предпросмотра. Повторите проверку файла.")
        for(row in preview.changes.filter{it.externalId in selected}) {
            val current=dataset?.let{dao.identity(it.id,row.externalId)}
            if(current?.id!=row.before?.id||current?.version!=row.before?.version)throw AoristException("Глагол изменился после предпросмотра.")
        }
        val at=clock.millis()
        if(dataset==null){val id=dao.insertDataset(AoristDatasetEntity(externalId=external,revision=AoristContent.revision(preview.metadata),metadata=preview.metadata.toString(),createdAt=at,updatedAt=at));dataset=dao.datasetById(id)!!}
        var created=0;var updated=0;var unchanged=0
        for(row in preview.changes.filter{it.externalId in selected}) {
            val old=dao.identity(dataset.id,row.externalId)
            when(row.kind){AoristChangeKind.ADD->created++;AoristChangeKind.UPDATE->updated++;AoristChangeKind.UNCHANGED->unchanged++}
            write(old,dataset.id,row.after)
        }
        dao.updateDataset(dataset.copy(revision=AoristContent.revision(preview.metadata),metadata=preview.metadata.toString(),version=dataset.version+1,updatedAt=at))
        val result=AoristSummary(created,updated,unchanged,preview.changes.size-selected.size)
        val payload=buildJsonObject{put("created",created);put("updated",updated);put("unchanged",unchanged);put("deselected",result.deselected)}
        dao.insertReceipt(AoristReceiptEntity(requestId,preview.id,dataset.id,requestHash,preview.fileHash,payload.toString(),at))
        result
    }
    private fun summary(e:JsonObject)=AoristSummary(e.getValue("created").jsonPrimitive.int,e.getValue("updated").jsonPrimitive.int,e.getValue("unchanged").jsonPrimitive.int,e.getValue("deselected").jsonPrimitive.int)
    suspend fun export(datasetId:String,includeArchived:Boolean=false):ByteArray {
        AoristContent.identifier(datasetId)
        return db.withContentTransaction {
            val d=dao.dataset(datasetId)?:throw AoristException("Набор не найден.")
            val rows=dao.export(d.id,includeArchived)
            if(rows.isEmpty()||rows.size>1000)throw AoristException("Для экспорта нужно 1–1000 глаголов.")
            val bytes=AoristContent.document(AoristContent.strict(d.metadata).jsonObject,rows.map{record(it).entry}).toString().toByteArray(Charsets.UTF_8)
            if(AoristContent.parse(bytes).issues.isNotEmpty())throw AoristException("Экспорт не соответствует формату аориста.")
            bytes
        }
    }
    /** Called on isolated staging and inside the final full-restore transaction. */
    internal suspend fun validateStored() {
        for(d in dao.datasets()) {
            require(d.id>0&&d.version>=0&&d.revision>0)
            AoristContent.identifier(d.externalId)
            val metadata=AoristContent.strict(d.metadata).jsonObject
            require("entries" !in metadata&&AoristContent.text(metadata,"datasetId")==d.externalId&&AoristContent.revision(metadata)==d.revision)
            require(AoristContent.parse(AoristContent.document(metadata,emptyList()).toString().toByteArray()).issues.all{it.path=="$.entries"})
        }
        for(id in dao.allIds()) {
            val graph=dao.get(id)!!;val row=graph.verb
            require(id>0&&row.version>=0&&graph.forms.size==6&&graph.forms.map{it.form.person}.toSet()==AoristContent.persons.toSet())
            val e=record(graph).entry;require(AoristContent.validate(e).isEmpty()&&row.contentHash==AoristContent.hash(e))
            val expectedSearch=VocabularyText.key((listOf("id","lemma","russian","studyGroup").map{AoristContent.text(e,it)}+AoristContent.persons.flatMap{AoristContent.answers(e,it)}).joinToString(" "))
            require(row.searchText==expectedSearch)
            for(form in graph.forms){require(form.form.id>0&&form.answers.map{it.position}.sorted()==(1..form.answers.size).toList())
                for(a in form.answers)require(a.id>0&&a.normalizedKey==AoristContent.key(a.text))}
        }
        for(r in dao.receipts()) {
            require(UUID.fromString(r.requestId).toString()==r.requestId&&UUID.fromString(r.previewId).toString()==r.previewId)
            require(listOf(r.fileHash,r.requestHash).all{it.matches(Regex("[a-f0-9]{64}"))})
            val e=AoristContent.strict(r.summary).jsonObject
            require(e.keys==setOf("created","updated","unchanged","deselected"))
            val values=e.values.map{it.jsonPrimitive};require(values.all{!it.isString&&it.intOrNull in 0..1000}&&values.sumOf{it.int} in 1..1000)
        }
    }
}
