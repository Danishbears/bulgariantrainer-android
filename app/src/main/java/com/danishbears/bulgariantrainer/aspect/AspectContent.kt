package com.danishbears.bulgariantrainer.aspect

import com.danishbears.bulgariantrainer.backup.BackupFormat
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.*

class AspectException(message: String) : IllegalArgumentException(message)
data class AspectIssue(val path: String, val message: String)
data class AspectDocument(val metadata: JsonObject, val entries: List<JsonObject>, val issues: List<AspectIssue>)
/** Separate content contract. Never changes vocabulary/sentence answer matching. */
object AspectContent {
    const val MAX_BYTES = 1024 * 1024
    const val FORMAT = "bulgariantrainer.aspect-pairs"
    val entryFields = setOf("id","imperfective","perfective","russianImperfective","russianPerfective","acceptedImperfective","acceptedPerfective","topic","studyStage","relationType","note","provenance","examples")
    private val rootFields = setOf("format","formatVersion","language","translationLanguage","entryForm","createdOn","sources","editorialStatus","entries")
    private val whitespace = Regex("[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")
    fun key(value: String): String = Normalizer.normalize(whitespace.replace(Normalizer.normalize(value,Normalizer.Form.NFC).replace("\u0301","").replace("\u0300","")," ").trim().lowercase(Locale.ROOT),Normalizer.Form.NFC)
    fun namespace(value: String) { if(!value.matches(Regex("[a-z0-9][a-z0-9._-]{0,99}"))) throw AspectException("Пространство имён: 1–100 строчных латинских букв, цифр, точек, _ или -.") }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun canonical(value: JsonElement): JsonElement = when(value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
    fun hash(value: JsonElement) = hash(canonical(value).toString().toByteArray(Charsets.UTF_8))
    fun strict(text: String): JsonElement {
        try {
            // Reuse the existing bounded duplicate-key and Unicode guards, not backup file decoding.
            BackupFormat.checkJsonStructure(text)
            return Json.parseToJsonElement(text).also { BackupFormat.validateUnicode(it) }
        } catch(_: Exception) { throw AspectException("Некорректный JSON: повтор ключа, структура, кодировка или лишний текст.") }
    }
    fun parse(bytes: ByteArray): AspectDocument {
        if(bytes.size>MAX_BYTES) throw AspectException("Максимальный размер JSON — 1 МиБ.")
        val text=try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
            catch(_: Exception){throw AspectException("Файл должен быть в UTF-8.")}
        val root=strict(text) as? JsonObject ?: throw AspectException("Ожидается объект JSON.")
        val errors=mutableListOf<AspectIssue>()
        fun issue(path:String,message:String){errors+=AspectIssue(path,message)}
        (root.keys-rootFields).forEach{issue("$."+it,"Неизвестное поле.")}
        if(root["format"]!=JsonPrimitive(FORMAT))issue("$.format","Неподдерживаемый формат.")
        if(root["formatVersion"]!=JsonPrimitive(1))issue("$.formatVersion","Поддерживается только целое formatVersion=1.")
        for((field,expected) in mapOf("language" to "bg","translationLanguage" to "ru","entryForm" to "1st_person_singular_present_citation_form"))
            if(root.containsKey(field)&&root[field]!=JsonPrimitive(expected))issue("$."+field,"Ожидается "+expected)
        if(root.containsKey("createdOn")) try { java.time.LocalDate.parse(string(root,"createdOn")) } catch(_:Exception){issue("$.createdOn","Ожидается дата ГГГГ-ММ-ДД.")}
        if(root.containsKey("editorialStatus")&&stringOrNull(root,"editorialStatus")==null)issue("$.editorialStatus","Ожидается строка.")
        val sources=root["sources"]
        val sourceIds=mutableSetOf<String>()
        if(sources!=null){
            if(sources !is JsonArray)issue("$.sources","Ожидается массив.")
            else sources.forEachIndexed { i,node ->
                val p="$.sources["+i+"]";val s=node as? JsonObject
                if(s==null)issue(p,"Ожидается объект.") else {
                    (s.keys-setOf("id","title","url","accessedOn","extractedTableRows")).forEach{issue(p+"."+it,"Неизвестное поле.")}
                    val id=stringOrNull(s,"id");if(id.isNullOrBlank()||!sourceIds.add(id))issue(p+".id","Нужен уникальный непустой ID.")
                    for(f in listOf("title","url","accessedOn"))if(s.containsKey(f)&&stringOrNull(s,f)==null)issue(p+"."+f,"Ожидается строка.")
                    if(s.containsKey("extractedTableRows")&&!integer(s["extractedTableRows"],1..Int.MAX_VALUE))issue(p+".extractedTableRows","Нужно положительное целое.")
                }
            }
        }
        val array=root["entries"] as? JsonArray
        if(array==null||array.size !in 1..1000)issue("$.entries","Нужно 1–1000 записей.")
        val entries=mutableListOf<JsonObject>();val ids=mutableSetOf<String>()
        array?.take(1000)?.forEachIndexed { i,node ->
            val p="$.entries["+i+"]";val e=node as? JsonObject
            if(e==null)issue(p,"Ожидается объект.") else {
                entries+=e;errors+=validate(e,p)
                stringOrNull(e,"id")?.let{if(!ids.add(it))issue(p+".id","Повтор внешнего ID.")}
                val provenance=e["provenance"] as? JsonObject
                if(provenance?.get("kind")==JsonPrimitive("presentation")&&stringOrNull(provenance,"sourceId") !in sourceIds)issue(p+".provenance.sourceId","Описание источника отсутствует.")
            }
        }
        return AspectDocument(JsonObject(root.filterKeys{it!="entries"}),entries,errors)
    }
    fun stringOrNull(e:JsonObject,field:String)=(e[field] as? JsonPrimitive)?.takeIf{it.isString}?.content
    fun string(e:JsonObject,field:String)=stringOrNull(e,field) ?: throw AspectException("Поле "+field+": ожидается строка.")
    fun answers(e:JsonObject,side:String)=(e[side] as? JsonArray)?.map{(it as? JsonPrimitive)?.takeIf{v->v.isString}?.content ?: throw AspectException("Варианты должны быть строками.")} ?: throw AspectException("Нужен массив ответов.")
    private fun integer(v:JsonElement?,range:IntRange)=v is JsonPrimitive&&!v.isString&&v.intOrNull in range && v.content.matches(Regex("-?[0-9]+"))
    fun validate(e:JsonObject,path:String="entry"):List<AspectIssue> {
        val errors=mutableListOf<AspectIssue>();fun issue(f:String,m:String){errors+=AspectIssue(path+"."+f,m)}
        (e.keys-entryFields).forEach{issue(it,"Неизвестное поле.")}
        for((f,max) in mapOf("id" to 100,"imperfective" to 255,"perfective" to 255,"russianImperfective" to 1000,"russianPerfective" to 1000,"topic" to 255)){
            val v=stringOrNull(e,f)
            if(v==null||key(v).isEmpty()||v.length>max||v.any{it.isISOControl()&&it !in "\n\r\t"})issue(f,"Нужен непустой текст, до "+max+" знаков.")
        }
        stringOrNull(e,"id")?.let{if(it!=it.trim())issue("id","Пробелы по краям ID запрещены.")}
        if(!integer(e["studyStage"],1..3))issue("studyStage","Нужно целое число 1–3.")
        if(stringOrNull(e,"relationType") !in setOf("core","resultative","bounded"))issue("relationType","Выберите core, resultative или bounded.")
        val note=stringOrNull(e,"note");if(note==null||note.length>4000)issue("note","Нужна строка до 4000 знаков; пустая разрешена.")
        for((side,primary) in mapOf("acceptedImperfective" to "imperfective","acceptedPerfective" to "perfective")){
            val a=e[side] as? JsonArray
            if(a==null||a.size !in 1..50)issue(side,"Нужно 1–50 ответов.")
            else {
                val keys=mutableSetOf<String>()
                a.forEachIndexed { i,v ->
                    val s=(v as? JsonPrimitive)?.takeIf{it.isString}?.content
                    if(s==null||key(s).isEmpty()||s.length>255||key(s).length>255||s.any{it.isISOControl()&&it !in "\n\r\t"})issue(side+"["+i+"]","Нужен непустой ответ до 255 знаков.")
                    else if(!keys.add(key(s)))issue(side+"["+i+"]","Повтор нормализованного ответа.")
                }
                stringOrNull(e,primary)?.let{if(key(it) !in keys)issue(side,"Основная форма должна входить в ответы.")}
            }
        }
        val p=e["provenance"] as? JsonObject
        if(p==null)issue("provenance","Нужен объект происхождения.") else {
            (p.keys-setOf("kind","sourceId","tableRow")).forEach{issue("provenance."+it,"Неизвестное поле.")}
            when(stringOrNull(p,"kind")){
                "added" -> if(p["sourceId"]!=null&&p["sourceId"]!=JsonNull||p["tableRow"]!=null&&p["tableRow"]!=JsonNull)issue("provenance","У добавленной записи нет строки источника.")
                "presentation" -> if(stringOrNull(p,"sourceId").isNullOrBlank()||(stringOrNull(p,"sourceId")?.length ?: 0)>100||stringOrNull(p,"sourceId")?.any{it.isISOControl()&&it !in "\n\r\t"}==true||!integer(p["tableRow"],1..Int.MAX_VALUE))issue("provenance","Нужны sourceId и положительный tableRow.")
                else -> issue("provenance.kind","Выберите added или presentation.")
            }
        }
        val examples=e["examples"]
        if(examples!=null&&examples!=JsonNull){
            val ex=examples as? JsonObject
            if(ex==null||ex.keys!=setOf("imperfective","perfective"))issue("examples","Нужны оба вида.") else for(side in ex.keys){
                val row=ex[side] as? JsonObject
                if(row==null||row.keys!=setOf("bg","ru"))issue("examples."+side,"Нужны bg и ru.")
                else for(f in listOf("bg","ru")){val s=stringOrNull(row,f);if(s==null||key(s).isEmpty()||s.length>4000||s.any{it.isISOControl()&&it !in "\n\r\t"})issue("examples."+side+"."+f,"Нужен текст до 4000 знаков.")}
            }
        }
        return errors
    }
    fun document(metadata:JsonObject,entries:List<JsonObject>)=JsonObject(metadata+("entries" to JsonArray(entries)))
}
