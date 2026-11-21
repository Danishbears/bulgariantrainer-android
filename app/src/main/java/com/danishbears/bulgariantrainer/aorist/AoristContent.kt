package com.danishbears.bulgariantrainer.aorist

import com.danishbears.bulgariantrainer.backup.BackupFormat
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.*

class AoristException(message:String):IllegalArgumentException(message)
data class AoristIssue(val path:String,val message:String)
data class AoristDocument(val metadata:JsonObject,val entries:List<JsonObject>,val issues:List<AoristIssue>)

/** The supplied aorist JSON v1 contract, independent of other content and full backups. */
object AoristContent {
    const val FORMAT="bulgariantrainer.aorist"
    const val MAX_BYTES=1024*1024
    val persons=listOf("1sg","2sg","3sg","1pl","2pl","3pl")
    val entryFields=setOf("id","lemma","russian","aspect","tense","reflexiveParticle","studyGroup","note","source","forms")
    private val rootFields=setOf("format","formatVersion","datasetId","datasetRevision","createdOn","language","translationLanguage","tense","entryForm","notes","entries")
    private val whitespace=Regex("[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")
    fun key(value:String):String=Normalizer.normalize(whitespace.replace(Normalizer.normalize(value,Normalizer.Form.NFC).replace("\u0301","").replace("\u0300","")," ").trim().lowercase(Locale.ROOT),Normalizer.Form.NFC)
    fun canonical(value:JsonElement):JsonElement=when(value) {
        is JsonObject->JsonObject(value.toSortedMap().mapValues{canonical(it.value)})
        is JsonArray->JsonArray(value.map(::canonical))
        else->value
    }
    fun hash(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun hash(value:JsonElement)=hash(canonical(value).toString().toByteArray(Charsets.UTF_8))
    fun strict(text:String):JsonElement=try {
        BackupFormat.checkJsonStructure(text)
        Json.parseToJsonElement(text).also{BackupFormat.validateUnicode(it)}
    }catch(_:Exception){throw AoristException("Некорректный JSON: структура, повтор ключа или символы Unicode.")}
    fun text(e:JsonObject,field:String):String=(e[field] as? JsonPrimitive)?.takeIf{it.isString}?.content
        ?:throw AoristException("Поле $field: ожидается строка.")
    fun identifier(value:String) {
        if(!value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")))throw AoristException("ID: 1–100 латинских букв, цифр, точек, _ или -.")
    }
    fun answers(e:JsonObject,person:String):List<String> =(e.getValue("forms").jsonObject.getValue(person) as JsonArray).map{it.jsonPrimitive.content}
    private fun number(e:JsonElement?):Long?=(e as? JsonPrimitive)?.takeIf{!it.isString&&it.content.matches(Regex("[0-9]+"))}?.longOrNull
    fun revision(e:JsonObject):Long=number(e["datasetRevision"])?.takeIf{it>0}?:throw AoristException("datasetRevision: нужно положительное целое число.")
    private fun validText(v:JsonElement?,max:Int,blank:Boolean=false):Boolean {
        val p=(v as? JsonPrimitive)?.takeIf{it.isString}?:return false
        return p.content.length<=max&&(blank||key(p.content).isNotEmpty())&&p.content.none{it.isISOControl()&&it !in "\t\n\r"}
    }
    internal fun requireUnicode(value:String) {
        try { BackupFormat.validateUnicode(JsonPrimitive(value)) }
        catch(_:Exception){ throw AoristException("Некорректные символы Unicode.") }
    }
    fun parse(bytes:ByteArray):AoristDocument {
        if(bytes.size>MAX_BYTES)throw AoristException("Максимальный размер JSON — 1 МиБ.")
        val text=try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")}catch(_:Exception){throw AoristException("Файл должен быть в UTF-8.")}
        val root=strict(text) as? JsonObject?:throw AoristException("Ожидается объект аориста JSON.")
        val issues=mutableListOf<AoristIssue>()
        fun issue(p:String,m:String){issues+=AoristIssue(p,m)}
        (root.keys-rootFields).forEach{issue("$.$it","Неизвестное поле; обновите приложение вместо потери данных.")}
        if(root["format"]!=JsonPrimitive(FORMAT))issue("$.format","Ожидается $FORMAT; другие импорты и полные копии не подходят.")
        if(root["formatVersion"]!=JsonPrimitive(1))issue("$.formatVersion","Поддерживается только целое formatVersion=1.")
        try{identifier(text(root,"datasetId"))}catch(e:AoristException){issue("$.datasetId",e.message.orEmpty())}
        try{revision(root)}catch(e:AoristException){issue("$.datasetRevision",e.message.orEmpty())}
        for((field,value) in mapOf("language" to "bg","translationLanguage" to "ru","tense" to "aorist","entryForm" to "first_person_singular_present_dictionary_form"))
            if(field in root&&root[field]!=JsonPrimitive(value))issue("$.$field","Ожидается $value.")
        if("createdOn" in root)try{LocalDate.parse(text(root,"createdOn"))}catch(_:Exception){issue("$.createdOn","Ожидается дата ГГГГ-ММ-ДД.")}
        if("notes" in root){val notes=root["notes"] as? JsonArray;if(notes==null||notes.size>100||notes.any{!validText(it,4000)})issue("$.notes","Нужен массив непустых строк, до 100 заметок.")}
        val rows=root["entries"] as? JsonArray
        if(rows==null||rows.size !in 1..1000)issue("$.entries","Нужно 1–1000 записей.")
        val entries=mutableListOf<JsonObject>();val ids=mutableSetOf<String>()
        rows?.take(1000)?.forEachIndexed{i,v->
            val path="$.entries[$i]";val e=v as? JsonObject
            if(e==null)issue(path,"Ожидается объект.")else {
                entries+=e;issues+=validate(e,path)
                (e["id"] as? JsonPrimitive)?.takeIf{it.isString}?.content?.let{if(!ids.add(it))issue("$path.id","Повтор внешнего ID в одном наборе.")}
            }
        }
        return AoristDocument(JsonObject(root.filterKeys{it!="entries"}),entries,issues)
    }
    fun validate(e:JsonObject,path:String="entry"):List<AoristIssue> {
        val errors=mutableListOf<AoristIssue>()
        fun issue(f:String,m:String){errors+=AoristIssue("$path.$f",m)}
        (e.keys-entryFields).forEach{issue(it,"Неизвестное поле.")}
        (entryFields-e.keys).forEach{issue(it,"Обязательное поле отсутствует.")}
        for((f,max)in mapOf("id" to 100,"lemma" to 255,"russian" to 1000,"studyGroup" to 100))
            if(!validText(e[f],max))issue(f,"Нужен непустой текст, до $max знаков.")
        try{identifier(text(e,"id"))}catch(ex:AoristException){issue("id",ex.message.orEmpty())}
        if(e["aspect"] !in setOf(JsonPrimitive("imperfective"),JsonPrimitive("perfective")))issue("aspect","Выберите imperfective или perfective.")
        if(e["tense"]!=JsonPrimitive("aorist"))issue("tense","Поддерживается tense=aorist.")
        if(e["reflexiveParticle"] !in setOf(JsonNull,JsonPrimitive("се"),JsonPrimitive("си")))issue("reflexiveParticle","Нужно null, се или си.")
        if(!validText(e["note"],4000,true))issue("note","Нужна строка до 4000 знаков; пустая разрешена.")
        val source=e["source"] as? JsonObject
        if(source==null||source.keys!=setOf("title","url","section","checkedOn"))issue("source","Нужны title, url, section, checkedOn; неизвестные поля не отбрасываются.")
        else {
            for(f in source.keys)if(!validText(source[f],if(f=="url")2000 else 1000,f=="url"))issue("source.$f","Нужен непустой текст.")
            try{LocalDate.parse(text(source,"checkedOn"))}catch(_:Exception){issue("source.checkedOn","Ожидается дата ГГГГ-ММ-ДД.")}
            try{val value=text(source,"url");if(value.isNotEmpty()){val uri=java.net.URI(value);require(uri.scheme in setOf("https","http")&&!uri.host.isNullOrEmpty()&&uri.userInfo==null)}}catch(_:Exception){issue("source.url","Нужен абсолютный HTTP(S) URL источника.")}
        }
        val forms=e["forms"] as? JsonObject
        if(forms==null||forms.keys!=persons.toSet())issue("forms","Нужны ровно шесть массивов: ${persons.joinToString()}.")
        for(person in persons) {
            val values=forms?.get(person) as? JsonArray
            if(values==null||values.size !in 1..50)issue("forms.$person","Нужно 1–50 явно заданных ответов.")
            else {
                val keys=mutableSetOf<String>()
                values.forEachIndexed{i,v->if(!validText(v,255))issue("forms.$person[$i]","Нужен непустой ответ до 255 знаков.")
                    else if(!keys.add(key(v.jsonPrimitive.content)))issue("forms.$person[$i]","Повтор нормализованного ответа этого лица.")}
            }
        }
        return errors
    }
    fun document(metadata:JsonObject,entries:List<JsonObject>)=JsonObject(metadata+("entries" to JsonArray(entries)))
    internal fun manualMetadata(id:String)=buildJsonObject{put("format",FORMAT);put("formatVersion",1);put("datasetId",id);put("datasetRevision",1)}
}
