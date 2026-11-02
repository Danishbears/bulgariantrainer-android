package com.danishbears.bulgariantrainer.aspect

import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.data.withContentTransaction as withTransaction
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class AspectRecord(val id:Long,val namespace:String,val version:Long,val archived:Boolean,val entry:JsonObject)
data class AspectDataset(val namespace:String,val revision:String?,val count:Long)
data class AspectFilter(val namespace:String?=null,val query:String="",val stage:Int?=null,val topic:String?=null,val relation:String?=null,val archived:Boolean=false)
data class AspectPage(val rows:List<AspectRecord>,val total:Long,val offset:Int)
data class AspectChange(val externalId:String,val kind:String,val before:AspectRecord?,val after:JsonObject)
data class AspectSummary(val created:Int,val updated:Int,val unchanged:Int,val deselected:Int)
class AspectPreview internal constructor(val id:String,val filename:String,val namespace:String,val issues:List<AspectIssue>,val changes:List<AspectChange>,
    internal val metadata:JsonObject,internal val fileHash:String,internal val datasetId:Long?,internal val datasetVersion:Long?,
    internal val expires:Long,internal val epoch:Long)

class AspectRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC()){
    private val dao=db.aspectDao()
    suspend fun datasets():List<AspectDataset> = db.withTransaction { dao.datasets().map { AspectDataset(it.namespace,it.revision,dao.count(it.namespace,false,null,null,null,null)) } }
    suspend fun list(filter:AspectFilter=AspectFilter(),offset:Int=0,limit:Int=25):AspectPage {
        require(offset>=0&&limit in 1..100)
        filter.namespace?.let(AspectContent::namespace)
        if(filter.query.length>255||filter.stage!=null&&filter.stage !in 1..3||filter.relation!=null&&filter.relation !in setOf("core","resultative","bounded"))throw AspectException("Некорректный фильтр.")
        val pattern=filter.query.takeIf{it.isNotBlank()}?.let(VocabularyText::searchPattern)
        return db.withTransaction {
            AspectPage(dao.list(filter.namespace,filter.archived,filter.stage,filter.topic,filter.relation,pattern,limit,offset).map(::record),
                dao.count(filter.namespace,filter.archived,filter.stage,filter.topic,filter.relation,pattern),offset)
        }
    }
    suspend fun get(id:Long):AspectRecord=db.withTransaction{record(dao.get(id)?:throw AspectException("Пара не найдена."))}
    private fun requireEntry(entry:JsonObject) {
        AspectContent.validate(entry).firstOrNull()?.let{throw AspectException(it.path+": "+it.message)}
    }
    private fun emptyMetadata()=buildJsonObject{put("format",AspectContent.FORMAT);put("formatVersion",1)}
    private fun context(entry:JsonObject,metadata:JsonObject,fallback:JsonObject=JsonObject(emptyMap())):JsonObject{
        val p=entry.getValue("provenance").jsonObject
        if(p["kind"]!=JsonPrimitive("presentation"))return JsonObject(emptyMap())
        val id=AspectContent.string(p,"sourceId")
        val source=(metadata["sources"] as? JsonArray)?.firstOrNull{(it as? JsonObject)?.get("id")==JsonPrimitive(id)} ?: fallback[id]
            ?:throw AspectException("Нет описания источника "+id+".")
        return JsonObject(mapOf(id to source))
    }
    suspend fun create(namespace:String,entry:JsonObject):AspectRecord {
        AspectContent.namespace(namespace);requireEntry(entry)
        return db.withTransaction {
            var dataset=dao.dataset(namespace)
            val at=clock.millis()
            if(dataset==null){val id=dao.insertDataset(AspectDatasetEntity(namespace=namespace,metadata=emptyMetadata().toString(),createdAt=at,updatedAt=at));dataset=dao.datasetById(id)!!}
            val external=AspectContent.string(entry,"id")
            if(dao.identity(dataset.id,external)!=null)throw AspectException("Такой внешний ID уже есть, включая архив.")
            val id=write(null,dataset.id,entry,context(entry,AspectContent.strict(dataset.metadata).jsonObject))
            dao.updateDataset(dataset.copy(version=dataset.version+1,updatedAt=at))
            record(dao.get(id)!!)
        }
    }
    suspend fun update(id:Long,version:Long,entry:JsonObject):AspectRecord {
        requireEntry(entry)
        return db.withTransaction {
            val old=dao.get(id)?:throw AspectException("Пара не найдена.")
            if(old.pair.version!=version)throw AspectException("Пара изменена. Откройте актуальную запись заново.")
            if(old.pair.externalId!=AspectContent.string(entry,"id"))throw AspectException("Внешний ID неизменяем.")
            val dataset=dao.datasetById(old.pair.datasetId)!!
            val source=if(entry["provenance"]==AspectContent.strict(old.pair.provenance))AspectContent.strict(old.pair.sourceContext).jsonObject
                else context(entry,AspectContent.strict(dataset.metadata).jsonObject,AspectContent.strict(old.pair.sourceContext).jsonObject)
            write(old.pair,dataset.id,entry,source)
            dao.updateDataset(dataset.copy(version=dataset.version+1,updatedAt=clock.millis()))
            record(dao.get(id)!!)
        }
    }
    suspend fun archive(id:Long,version:Long,archived:Boolean):AspectRecord=db.withTransaction{
        val p=dao.get(id)?:throw AspectException("Пара не найдена.")
        if(p.pair.version!=version)throw AspectException("Пара изменена. Обновите список.")
        if(p.pair.archived!=archived){
            dao.update(p.pair.copy(archived=archived,version=version+1,updatedAt=clock.millis()))
            val d=dao.datasetById(p.pair.datasetId)!!;dao.updateDataset(d.copy(version=d.version+1,updatedAt=clock.millis()))
        };record(dao.get(id)!!)
    }
    private suspend fun write(old:AspectPairEntity?,dataset:Long,e:JsonObject,source:JsonObject):Long{
        val hash=AspectContent.hash(JsonObject(e.filter { (k,v) -> k!="examples" || v!=JsonNull }))
        if(old!=null&&old.contentHash==hash)return old.id
        val at=clock.millis()
        val search=VocabularyText.key((listOf("id","imperfective","perfective","russianImperfective","russianPerfective","topic").map{AspectContent.string(e,it)}
            +AspectContent.answers(e,"acceptedImperfective")+AspectContent.answers(e,"acceptedPerfective")).joinToString(" "))
        val row=AspectPairEntity(id=old?.id?:0,datasetId=dataset,externalId=AspectContent.string(e,"id"),
            imperfective=AspectContent.string(e,"imperfective"),perfective=AspectContent.string(e,"perfective"),
            russianImperfective=AspectContent.string(e,"russianImperfective"),russianPerfective=AspectContent.string(e,"russianPerfective"),
            topic=AspectContent.string(e,"topic"),studyStage=e.getValue("studyStage").jsonPrimitive.int,relationType=AspectContent.string(e,"relationType"),
            note=AspectContent.string(e,"note"),provenance=e.getValue("provenance").toString(),examples=e["examples"]?.takeIf{it!=JsonNull}?.toString(),
            sourceContext=source.toString(),searchText=search,contentHash=hash,archived=old?.archived?:false,version=(old?.version?.plus(1))?:0,
            createdAt=old?.createdAt?:at,updatedAt=at)
        val id=if(old==null)dao.insert(row) else {dao.update(row);old.id}
        dao.deleteAnswers(id)
        dao.insertAnswers(listOf("acceptedImperfective" to "IMPERFECTIVE","acceptedPerfective" to "PERFECTIVE").flatMap{(field,side)->
            AspectContent.answers(e,field).mapIndexed{i,s->AspectAnswerEntity(pairId=id,side=side,position=i+1,text=s,normalizedKey=AspectContent.key(s))}})
        return id
    }
    private fun record(graph:AspectGraph):AspectRecord{
        val p=graph.pair
        val e=buildJsonObject{
            put("id",p.externalId);put("imperfective",p.imperfective);put("perfective",p.perfective)
            put("russianImperfective",p.russianImperfective);put("russianPerfective",p.russianPerfective)
            put("topic",p.topic);put("studyStage",p.studyStage);put("relationType",p.relationType);put("note",p.note)
            put("provenance",AspectContent.strict(p.provenance))
            p.examples?.let{put("examples",AspectContent.strict(it))}
            for((field,side) in listOf("acceptedImperfective" to "IMPERFECTIVE","acceptedPerfective" to "PERFECTIVE"))
                put(field,JsonArray(graph.answers.filter{it.side==side}.sortedBy{it.position}.map{JsonPrimitive(it.text)}))
        }
        return AspectRecord(p.id,graph.namespace,p.version,p.archived,e)
    }
    suspend fun preview(namespace:String,filename:String,bytes:ByteArray):AspectPreview{
        AspectContent.namespace(namespace)
        val epoch=db.contentEpoch.get()
        val input=bytes.copyOf()
        val parsed=withContext(Dispatchers.Default){AspectContent.parse(input)}
        return db.withTransaction{
            if(epoch!=db.contentEpoch.get())throw AspectException("Данные заменены. Выберите файл заново.")
            val dataset=dao.dataset(namespace)
            val changes=if(parsed.issues.isEmpty())parsed.entries.map { e ->
                val existing=dataset?.let{dao.identity(it.id,AspectContent.string(e,"id"))}?.let{record(dao.get(it.id)!!)}
                AspectChange(AspectContent.string(e,"id"),if(existing==null)"ADD" else if(AspectContent.canonical(existing.entry)==AspectContent.canonical(JsonObject(e.filter { (k,v) -> k!="examples" || v!=JsonNull })))"UNCHANGED" else "UPDATE",existing,e)
            } else emptyList()
            AspectPreview(UUID.randomUUID().toString(),filename,namespace,parsed.issues,changes,parsed.metadata,AspectContent.hash(input),
                dataset?.id,dataset?.version,clock.millis()+30*60*1000,epoch)
        }
    }
    suspend fun confirm(preview:AspectPreview,selected:Set<String>,requestId:String=preview.id):AspectSummary=db.withTransaction{
        UUID.fromString(requestId)
        val requestHash=AspectContent.hash(buildJsonObject{put("preview",preview.id);put("selected",JsonArray(selected.sorted().map(::JsonPrimitive)))})
        dao.receipt(requestId)?.let{
            if(it.requestHash!=requestHash)throw AspectException("Этот запрос уже сохранён с другим выбором.")
            return@withTransaction summary(AspectContent.strict(it.summary).jsonObject)
        }
        if(preview.epoch!=db.contentEpoch.get())throw AspectException("Данные заменены; выберите файл заново.")
        if(clock.millis()>=preview.expires)throw AspectException("Предпросмотр истёк. Выберите файл заново.")
        if(preview.issues.isNotEmpty()||selected.isEmpty()||!preview.changes.map{it.externalId}.containsAll(selected))throw AspectException("Нужен корректный файл и непустой выбор известных записей.")
        var dataset=dao.dataset(preview.namespace)
        if(dataset?.id!=preview.datasetId||dataset?.version!=preview.datasetVersion)throw AspectException("Набор изменился после предпросмотра. Выберите файл заново.")
        for(row in preview.changes.filter{it.externalId in selected}){
            val current=dataset?.let{dao.identity(it.id,row.externalId)}
            if(current?.id!=row.before?.id||current?.version!=row.before?.version)throw AspectException("Пара изменилась после предпросмотра.")
        }
        val at=clock.millis()
        if(dataset==null){val id=dao.insertDataset(AspectDatasetEntity(namespace=preview.namespace,metadata=preview.metadata.toString(),createdAt=at,updatedAt=at));dataset=dao.datasetById(id)!!}
        var created=0;var updated=0;var unchanged=0
        for(row in preview.changes.filter{it.externalId in selected}){
            val old=dao.identity(dataset.id,row.externalId)
            if(old==null)created++ else if(row.kind=="UNCHANGED")unchanged++ else updated++
            write(old,dataset.id,row.after,context(row.after,preview.metadata))
        }
        dao.updateDataset(dataset.copy(metadata=preview.metadata.toString(),revision=preview.fileHash,version=dataset.version+1,updatedAt=at))
        val result=AspectSummary(created,updated,unchanged,preview.changes.size-selected.size)
        val payload=buildJsonObject{put("created",created);put("updated",updated);put("unchanged",unchanged);put("deselected",result.deselected)}
        dao.insertReceipt(AspectReceiptEntity(requestId,preview.id,dataset.id,requestHash,preview.fileHash,preview.metadata.toString(),payload.toString(),at))
        result
    }
    private fun summary(e:JsonObject)=AspectSummary(e.getValue("created").jsonPrimitive.int,e.getValue("updated").jsonPrimitive.int,e.getValue("unchanged").jsonPrimitive.int,e.getValue("deselected").jsonPrimitive.int)
    suspend fun export(namespace:String,includeArchived:Boolean=false):ByteArray {
        AspectContent.namespace(namespace)
        return db.withTransaction{
            val d=dao.dataset(namespace)?:throw AspectException("Набор не найден.")
            val rows=dao.export(d.id,includeArchived)
            if(rows.isEmpty()||rows.size>1000)throw AspectException("Для одного файла нужно 1–1000 записей. Измените выбор.")
            val sources=linkedMapOf<String,JsonElement>()
            for(value in (AspectContent.strict(d.metadata).jsonObject["sources"] as? JsonArray).orEmpty()){
                val source=value.jsonObject
                sources[AspectContent.string(source,"id")]=source
            }
            // Per-entry source contexts survive later imports which omit earlier source definitions.
            for(row in rows)for((id,value) in AspectContent.strict(row.pair.sourceContext).jsonObject){
                if(sources[id]!=null&&sources[id]!=value)throw AspectException("Конфликт описаний источника "+id+". Экспорт отменён.")
                sources[id]=value
            }
            val metadata=JsonObject(AspectContent.strict(d.metadata).jsonObject+mapOf("format" to JsonPrimitive(AspectContent.FORMAT),"formatVersion" to JsonPrimitive(1),"sources" to JsonArray(sources.values.toList())))
            val bytes=AspectContent.document(metadata,rows.map{record(it).entry}).toString().toByteArray(Charsets.UTF_8)
            val parsed=AspectContent.parse(bytes)
            if(parsed.issues.isNotEmpty())throw AspectException("Экспорт не соответствует формату: "+parsed.issues.first().path)
            bytes
        }
    }
    /** Entire restored content is validated in the isolated backup database. */
    internal suspend fun validateStored() {
        db.withTransaction{
            for(d in dao.datasets()){
                AspectContent.namespace(d.namespace)
                val metadata=AspectContent.strict(d.metadata).jsonObject
                if("entries" in metadata)throw AspectException("В метаданных не должно быть записей.")
                val errors=AspectContent.parse(AspectContent.document(metadata,emptyList()).toString().toByteArray()).issues.filter{it.path!="$.entries"}
                if(errors.isNotEmpty())throw AspectException("Повреждены метаданные набора.")
                if(d.version<0)throw AspectException("Некорректная версия набора.")
                d.revision?.let{if(!it.matches(Regex("[a-f0-9]{64}")))throw AspectException("Некорректная ревизия.")}
            }
            for(receipt in dao.receipts()){
                if(UUID.fromString(receipt.requestId).toString()!=receipt.requestId||UUID.fromString(receipt.previewId).toString()!=receipt.previewId)throw AspectException("Некорректная квитанция.")
                if(!receipt.requestHash.matches(Regex("[a-f0-9]{64}"))||!receipt.fileHash.matches(Regex("[a-f0-9]{64}")))throw AspectException("Повреждена квитанция.")
                val summary=AspectContent.strict(receipt.summary).jsonObject
                if(summary.keys!=setOf("created","updated","unchanged","deselected"))throw AspectException("Повреждён итог импорта.")
                val counts=summary.values.map{it.jsonPrimitive}
                if(counts.any{it.isString||it.intOrNull==null||it.int !in 0..1000}||counts.sumOf{it.int}>1000||counts.sumOf{it.int}==0)throw AspectException("Некорректный итог импорта.")
                val metadata=AspectContent.strict(receipt.metadata).jsonObject
                if("entries" in metadata||AspectContent.parse(AspectContent.document(metadata,emptyList()).toString().toByteArray()).issues.any{it.path!="$.entries"})throw AspectException("Повреждены метаданные квитанции.")
            }
            for(p in dao.all()){
                val graph=dao.get(p.id)!!;val record=record(graph);requireEntry(record.entry)
                if(p.version<0||p.id<=0||p.contentHash!=AspectContent.hash(record.entry))throw AspectException("Повреждённая запись пары.")
                for(side in listOf("IMPERFECTIVE","PERFECTIVE")){
                    val ordered=graph.answers.filter{it.side==side}.map{it.position}.sorted()
                    if(ordered!=(1..ordered.size).toList())throw AspectException("Нарушен порядок вариантов.")
                }
                for(a in graph.answers)if(a.side !in setOf("IMPERFECTIVE","PERFECTIVE")||a.position<1||a.normalizedKey!=AspectContent.key(a.text))throw AspectException("Повреждены варианты.")
                val sources=AspectContent.strict(p.sourceContext).jsonObject
                val metadata=buildJsonObject{put("format",AspectContent.FORMAT);put("formatVersion",1);put("sources",JsonArray(sources.values.toList()))}
                if(AspectContent.parse(AspectContent.document(metadata,listOf(record.entry)).toString().toByteArray()).issues.isNotEmpty())throw AspectException("Повреждено описание источника.")
                context(record.entry,metadata)
            }
        }
    }
}
