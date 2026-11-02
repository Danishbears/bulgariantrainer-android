package com.danishbears.bulgariantrainer.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.*

enum class BackupError { INVALID_FORMAT, UNSUPPORTED_VERSION, WRONG_APPLICATION, CHECKSUM_MISMATCH, INVALID_DATA, TOO_LARGE, READ_FAILED, WRITE_FAILED, RESTORE_FAILED }
class BackupException(val code: BackupError) : IllegalArgumentException(code.name)
data class BackupSummary(val createdAt: Instant, val words: Int, val translations: Int, val sentences: Int,
    val alternatives: Int, val sessions: Int, val attempts: Int, val progress: Int, val schedules: Int, val activeSessions: Int, val preferences: Boolean, val aspectPairs: Int = 0, val aspectDatasets: Int = 0,
    val sourceFormatVersion:Int=3)
class PreparedBackup internal constructor(internal val document: JsonObject, val summary: BackupSummary)
internal data class DecodedBackup(val document:JsonObject,val sourceFormatVersion:Int)

/** Explicit v3 logical snapshot; original v1/v2 contracts are authenticated before expansion. */
internal object BackupFormat {
    const val VERSION = 3
    const val SCHEMA = 9
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_ROWS = 250_000
    const val APPLICATION = "com.danishbears.bulgariantrainer"
    val legacyTables = listOf("words","translations","sentences","sentence_alternatives","import_receipts","trainer_preferences",
        "flashcard_sessions","flashcard_cards","flashcard_attempts","recognition_progress","typing_sessions","typing_questions","typing_attempts","typing_progress",
        "sentence_sessions","sentence_questions","sentence_attempts","sentence_progress","word_review_schedules","sentence_review_schedules")
    val aspectTables = listOf("aspect_datasets","aspect_pairs","aspect_answers","aspect_receipts")
    val practiceTables = listOf("aspect_sessions","aspect_questions","aspect_attempts","aspect_progress","aspect_review_schedules")
    val tables = legacyTables + aspectTables + practiceTables
    val legacySequences = listOf("words","translations","sentences","sentence_alternatives","flashcard_sessions","flashcard_cards","flashcard_attempts",
        "typing_sessions","typing_questions","typing_attempts","sentence_sessions","sentence_questions","sentence_attempts")
    val aspectSequences = listOf("aspect_datasets","aspect_pairs","aspect_answers")
    val practiceSequences = listOf("aspect_sessions","aspect_questions","aspect_attempts")
    val sequences = legacySequences + aspectSequences + practiceSequences
    private val keys = setOf("format","formatVersion","applicationId","schemaVersion","createdAt","tables","sequences","sha256")
    fun canonical(value: JsonElement): JsonElement = when(value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
    fun digest(value: JsonObject): String = MessageDigest.getInstance("SHA-256").digest(canonical(JsonObject(value.filterKeys { it != "sha256" })).toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(at: Instant, data: JsonObject, sequence: JsonObject): ByteArray {
        val base=buildJsonObject {
            put("format","bulgarian-trainer-full-backup");put("formatVersion",VERSION);put("applicationId",APPLICATION);put("schemaVersion",SCHEMA)
            put("createdAt",at.toString());put("tables",data);put("sequences",sequence)
        }
        val root=JsonObject(base+ ("sha256" to JsonPrimitive(digest(base))))
        return canonical(root).toString().toByteArray(Charsets.UTF_8).also { if(it.size>MAX_BYTES) fail(BackupError.TOO_LARGE) }
    }
    fun decode(bytes: ByteArray): JsonObject=decodeVersioned(bytes).document
    fun decodeVersioned(bytes:ByteArray):DecodedBackup {
        if(bytes.size>MAX_BYTES) fail(BackupError.TOO_LARGE)
        try {
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            checkJsonStructure(text)
            val root=Json.parseToJsonElement(text) as? JsonObject ?: fail(BackupError.INVALID_FORMAT)
            if(root["format"]!=JsonPrimitive("bulgarian-trainer-full-backup")) fail(BackupError.INVALID_FORMAT)
            if(root["applicationId"]!=JsonPrimitive(APPLICATION)) fail(BackupError.WRONG_APPLICATION)
            val legacy = root["formatVersion"]==JsonPrimitive(1) && root["schemaVersion"]==JsonPrimitive(7)
            val withoutAspectPractice = root["formatVersion"]==JsonPrimitive(2) && root["schemaVersion"]==JsonPrimitive(8)
            if(!legacy && !withoutAspectPractice && (root["formatVersion"]!=JsonPrimitive(VERSION) || root["schemaVersion"]!=JsonPrimitive(SCHEMA))) fail(BackupError.UNSUPPORTED_VERSION)
            if(root.keys!=keys)fail(BackupError.INVALID_FORMAT)
            validateUnicode(root)
            val date=(root["createdAt"] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: fail(BackupError.INVALID_FORMAT)
            Instant.parse(date)
            val tables=root["tables"] as? JsonObject ?: fail(BackupError.INVALID_FORMAT)
            if(tables.keys!=(if(legacy)legacyTables else if(withoutAspectPractice)legacyTables+aspectTables else this.tables).toSet() || tables.values.any { it !is JsonArray }) fail(BackupError.INVALID_FORMAT)
            if(tables.values.sumOf { (it as JsonArray).size.toLong() }>MAX_ROWS) fail(BackupError.TOO_LARGE)
            val seq=root["sequences"] as? JsonObject ?: fail(BackupError.INVALID_FORMAT)
            if(seq.keys!=(if(legacy)legacySequences else if(withoutAspectPractice)legacySequences+aspectSequences else sequences).toSet() || seq.values.any { it !is JsonPrimitive || it.isString || it.longOrNull==null || it.long<0 }) fail(BackupError.INVALID_FORMAT)
            if(root["sha256"]!=JsonPrimitive(digest(root))) fail(BackupError.CHECKSUM_MISMATCH)
            val sourceVersion=root.getValue("formatVersion").jsonPrimitive.int
            if(legacy||withoutAspectPractice) {
                // Authenticate original v1 contract/checksum before expanding the isolated staging payload.
                val expanded=JsonObject(tables + this.tables.filter{it !in tables}.associateWith { JsonArray(emptyList()) })
                val highWater=JsonObject(seq + sequences.filter{it !in seq}.associateWith { JsonPrimitive(0) })
                return DecodedBackup(decode(encode(Instant.parse(date),expanded,highWater)),sourceVersion)
            }
            return DecodedBackup(root,sourceVersion)
        } catch(e: BackupException) { throw e }
        catch(_: Exception) { fail(BackupError.INVALID_FORMAT) }
    }
    fun summary(root: JsonObject): BackupSummary {
        val data=root.getValue("tables").jsonObject
        fun size(name: String)=data.getValue(name).jsonArray.size
        val sessions=listOf("flashcard_sessions","typing_sessions","sentence_sessions","aspect_sessions")
        return BackupSummary(Instant.parse(root.getValue("createdAt").jsonPrimitive.content),size("words"),size("translations"),size("sentences"),size("sentence_alternatives"),
            sessions.sumOf(::size),listOf("flashcard_attempts","typing_attempts","sentence_attempts","aspect_attempts").sumOf(::size),
            listOf("recognition_progress","typing_progress","sentence_progress","aspect_progress").sumOf(::size),size("word_review_schedules")+size("sentence_review_schedules")+size("aspect_review_schedules"),
            sessions.sumOf { table -> data.getValue(table).jsonArray.count { it.jsonObject["status"]==JsonPrimitive("IN_PROGRESS") } },size("trainer_preferences")>0,size("aspect_pairs"),size("aspect_datasets"))
    }
    fun validateUnicode(value: JsonElement) {
        when(value) {
            is JsonObject -> value.values.forEach(::validateUnicode)
            is JsonArray -> value.forEach(::validateUnicode)
            is JsonPrimitive -> if(value.isString) {
                val text=value.content;var i=0
                while(i<text.length) {
                    val c=text[i]
                    if(Character.isHighSurrogate(c)) {
                        if(i+1>=text.length || !Character.isLowSurrogate(text[i+1])) fail(BackupError.INVALID_FORMAT)
                        i++
                    } else if(Character.isLowSurrogate(c)) fail(BackupError.INVALID_FORMAT)
                    i++
                }
            }
        }
    }
    fun checkJsonStructure(text: String) {
        data class Frame(val objectValue: Boolean, val keys: MutableSet<String> = mutableSetOf(), var expectingKey: Boolean = true)
        val stack=mutableListOf<Frame>();var i=0
        while(i<text.length) {
            when(text[i]) {
                '{','[' -> { stack.add(Frame(text[i]=='{'));if(stack.size>16) fail(BackupError.INVALID_FORMAT) }
                '}',']' -> { if(stack.isEmpty()) fail(BackupError.INVALID_FORMAT);stack.removeAt(stack.lastIndex) }
                ',' -> stack.lastOrNull()?.let { if(it.objectValue) it.expectingKey=true }
                '"' -> {
                    val start=i++;while(i<text.length && text[i]!='"') { if(text[i]=='\\')i++;i++ }
                    if(i>=text.length) fail(BackupError.INVALID_FORMAT)
                    stack.lastOrNull()?.let { frame -> if(frame.objectValue && frame.expectingKey) {
                        val key=Json.parseToJsonElement(text.substring(start,i+1)).jsonPrimitive.content
                        if(!frame.keys.add(key)) fail(BackupError.INVALID_FORMAT)
                        frame.expectingKey=false
                    } }
                }
            };i++
        }
    }
    fun fail(code: BackupError): Nothing = throw BackupException(code)
}
