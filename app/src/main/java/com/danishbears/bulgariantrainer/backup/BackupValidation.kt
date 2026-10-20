package com.danishbears.bulgariantrainer.backup

import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.FlashcardRepository
import com.danishbears.bulgariantrainer.typing.TypingRepository
import com.danishbears.bulgariantrainer.sentences.SentencePracticeRepository
import kotlinx.serialization.json.*
import java.util.UUID

/** Structural/semantic checks, not a replay or regrading of historical learning facts. */
internal object BackupValidation {
    private val directions=setOf("BG_TO_RU","RU_TO_BG")
    private val scopes=setOf("NEW","DUE","MIXED")
    private val statuses=setOf("IN_PROGRESS","COMPLETED","ABANDONED")
    private fun check(value: Boolean) { if(!value) BackupFormat.fail(BackupError.INVALID_DATA) }
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.long
    private fun JsonObject.nullValue(name: String) = getValue(name)==JsonNull
    suspend fun validate(db: TrainerDatabase, document: JsonObject) {
        val tables=document.getValue("tables").jsonObject
        fun rows(name: String)=tables.getValue(name).jsonArray.map{it.jsonObject}
        val flags=mapOf("words" to setOf("archived"),"translations" to setOf("is_primary"),"sentences" to setOf("archived","approved"),
            "flashcard_attempts" to setOf("known"),"typing_sessions" to setOf("allow_shorter"),"typing_questions" to setOf("acknowledged"),
            "typing_attempts" to setOf("correct"),"sentence_questions" to setOf("acknowledged"),"sentence_attempts" to setOf("automatic_match"),
            "word_review_schedules" to setOf("inherited"),"sentence_review_schedules" to setOf("inherited"))
        val integers=setOf("attempts","known","correct","partial","incorrect","successes","streak","interval_days","consecutive_known","consecutive_correct", "created","translations_added","skipped","source_row")
        for(table in BackupFormat.tables) for(row in rows(table)) {
            for((name,value) in row) {
                if(name in flags[table].orEmpty()) check(value.jsonPrimitive.longOrNull?.let{it in 0L..1L}==true)
                if(name in integers) check(value.jsonPrimitive.longOrNull?.let{it in 0..Int.MAX_VALUE.toLong()}==true)
                if(name in setOf("word_id","sentence_id","session_id","question_id","card_id") || name=="id" && table!="import_receipts") check(value.jsonPrimitive.longOrNull?.let{it>0}==true)
                if(name=="position") check(value.jsonPrimitive.longOrNull?.let{it in (if(table=="sentence_alternatives")0 else 1)..Int.MAX_VALUE.toLong()}==true)
                if(name=="difficulty" && value!=JsonNull) check(value.jsonPrimitive.longOrNull?.let{it in 1L..5L}==true)
            }
        }
        fun content(row: JsonObject,text: String,key: String,max: Int) {
            check(row.text(text).length<=max && VocabularyText.clean(row.text(text)).isNotEmpty())
            check(row.text(key)==VocabularyText.key(row.text(text)))
        }
        rows("words").forEach{content(it,"bulgarian_text","normalized_text",255)}
        rows("translations").forEach{content(it,"russian_text","normalized_text",255)}
        rows("translations").groupBy{it.number("word_id")}.values.forEach{check(it.size<=50 && it.count{row->row.number("is_primary")==1L}==1)}
        rows("sentences").forEach { content(it,"russian_text","normalized_russian",4000);check(it.text("bulgarian_text").isNotBlank() && it.text("bulgarian_text").length<=4000) }
        rows("sentence_alternatives").forEach{content(it,"bulgarian_text","normalized_text",4000)}
        rows("sentence_alternatives").groupBy{it.number("sentence_id")}.values.forEach{check(it.size<=50 && it.map{row->row.number("position")}.distinct().size==it.size)}
        for(table in listOf("flashcard_cards","typing_questions","sentence_questions")) for(row in rows(table)) {
            if(table!="sentence_questions") { check(row.text("direction") in directions);check(row.text("bulgarian_text").isNotBlank()) }
            else check(row.text("russian_text").isNotBlank())
            val snapshot=row.text(if(table=="sentence_questions") "accepted_json" else "russian_json")
            BackupFormat.checkJsonStructure(snapshot)
            val array=Json.parseToJsonElement(snapshot) as? JsonArray ?: BackupFormat.fail(BackupError.INVALID_DATA)
            BackupFormat.validateUnicode(array)
            check(array.isNotEmpty() && array.size<=51 && array.all{it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length<=4000})
        }
        for(table in listOf("typing_attempts","sentence_attempts")) for(row in rows(table)) {
            check(row.text("submitted").length<=4000 && row.text("submitted").isNotBlank())
            // Preserve saved grading; validation never derives a new correct/incorrect result.
            check(row.text("normalized_answer")==VocabularyText.key(row.text("submitted")))
        }
        rows("sentence_attempts").forEach { row ->
            val auto=row.number("automatic_match")==1L;val manual=!row.nullValue("self_rating")
            if(manual) check(row.text("self_rating") in setOf("CORRECT","PARTIAL","INCORRECT"))
            check(!(auto && manual));check(row.nullValue("evaluated_at")==(!auto && !manual))
        }
        for(table in listOf("recognition_progress","typing_progress","sentence_progress","word_review_schedules","sentence_review_schedules")) for(row in rows(table)) {
            val attempts=row.number("attempts")
            val correct=row.number(if(table=="recognition_progress")"known" else if(table.endsWith("schedules"))"successes" else "correct")
            val streak=row.number(if(table=="recognition_progress")"consecutive_known" else if(table.endsWith("schedules"))"streak" else "consecutive_correct")
            check(correct<=attempts && streak<=correct)
            if(table=="sentence_progress") check(correct+row.number("partial")+row.number("incorrect")==attempts)
            if(table.endsWith("schedules")) check(row.number("interval_days")<=365)
            if(table=="word_review_schedules") check(row.text("mode") in setOf("FLASHCARD","TYPING_BG_TO_RU","TYPING_RU_TO_BG"))
            // Unknown legacy typing_progress direction strings are retained, not guessed or discarded.
        }
        val preferences=rows("trainer_preferences");check(preferences.size<=1)
        preferences.forEach { check(it.number("id")==1L && it.number("flashcard_size") in 1..100 && it.number("sentence_size") in 1..100)
            check(it.text("flashcard_direction") in directions+"MIXED");for(k in listOf("flashcard_scope","typing_scope","sentence_scope"))check(it.text(k) in scopes) }
        val groups=listOf("flashcard_sessions" to "flashcard_cards","typing_sessions" to "typing_questions","sentence_sessions" to "sentence_questions")
        for((table,children) in groups) {
            val bySession=rows(children).groupBy{it.number("session_id")}
            for(row in rows(table)) {
                val id=row.number("id");val total=row.number("total");val position=row.number("current_position")
                check(total in 1..(if(table=="typing_sessions")30 else 100) && position in 1..total+1)
                check(row.text("status") in statuses && row.text("study_scope") in scopes)
                check(row.nullValue("completed_at")== (row.text("status")=="IN_PROGRESS"))
                check(UUID.fromString(row.text("request_id")).toString()==row.text("request_id"))
                if(table!="typing_sessions") check(row.number("requested_size") in 1..100 && total<=row.number("requested_size"))
                if(table=="flashcard_sessions") check(row.text("direction") in directions+"MIXED") else check(row.number("revision")>=0)
                val questions=bySession[id].orEmpty();check(questions.size.toLong()==total)
                check(questions.map{it.number("position")}.sorted()==(1L..total).toList())
                // Exercise the exact graph-to-UI conversion for every restored session, not only the latest.
                when(table) {
                    "flashcard_sessions" -> FlashcardRepository(db).get(id)
                    "typing_sessions" -> TypingRepository(db).get(id)
                    else -> SentencePracticeRepository(db).get(id)
                }
            }
        }
        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { check(it.moveToFirst() && it.getString(0)=="ok") }
    }
}
