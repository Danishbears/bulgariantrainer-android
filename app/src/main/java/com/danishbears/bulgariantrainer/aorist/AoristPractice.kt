package com.danishbears.bulgariantrainer.aorist

import com.danishbears.bulgariantrainer.review.*
import kotlin.random.Random
import kotlinx.serialization.json.*

enum class AoristExercise { TYPED, CHOICE }
enum class AoristOutcome { CORRECT, INCORRECT, REVEALED }
enum class AoristSessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }
data class AoristSettings(val size:Int=20,val persons:List<String> = listOf("1sg"),
    val exercise:AoristExercise=AoristExercise.TYPED,val aspect:String?=null,val studyGroup:String?=null,
    val scope:StudyScope=StudyScope.MIXED,val datasetId:String?=null) {
    fun canonical():AoristSettings {
        if(size !in 1..100||persons.isEmpty()||persons.size>6||persons.distinct().size!=persons.size||persons.any{it !in AoristContent.persons}
            ||aspect!=null&&aspect !in setOf("imperfective","perfective")||studyGroup!=null&&(studyGroup.isBlank()||studyGroup.length>100))
            throw AoristException("Проверьте размер, лица, вид и учебную группу занятия.")
        studyGroup?.let(AoristContent::requireUnicode)
        datasetId?.let(AoristContent::identifier)
        return copy(persons=AoristContent.persons.filter{it in persons})
    }
    internal fun json():String=buildJsonObject {
        put("size",size);put("persons",JsonArray(persons.map(::JsonPrimitive)));put("exercise",exercise.name)
        put("aspect",aspect?.let(::JsonPrimitive)?:JsonNull);put("studyGroup",studyGroup?.let(::JsonPrimitive)?:JsonNull)
        put("scope",scope.name);put("datasetId",datasetId?.let(::JsonPrimitive)?:JsonNull)
    }.toString()
    companion object {
        internal fun read(text:String):AoristSettings=try {
            val e=AoristContent.strict(text).jsonObject
            require(e.keys==setOf("size","persons","exercise","aspect","studyGroup","scope","datasetId"))
            fun optional(k:String)=e.getValue(k).takeIf{it!=JsonNull}?.jsonPrimitive?.let{require(it.isString);it.content}
            val size=e.getValue("size").jsonPrimitive;require(!size.isString&&size.content.matches(Regex("[0-9]+")))
            AoristSettings(size.int,e.getValue("persons").jsonArray.map{require(it.jsonPrimitive.isString);it.jsonPrimitive.content},
                AoristExercise.valueOf(AoristContent.text(e,"exercise")),optional("aspect"),optional("studyGroup"),
                StudyScope.valueOf(AoristContent.text(e,"scope")),optional("datasetId")).canonical()
        }catch(e:AoristException){throw e}catch(_:Exception){throw AoristException("Некорректные параметры занятия аориста.")}
    }
}
data class AoristAvailability(val eligible:Int,val offered:Int,val shorterRequired:Boolean,val fewerOptions:Int,val unavailableChoices:Int)
data class AoristOption(val id:String,val text:String)
data class AoristPrompt(val lemma:String,val russian:String,val person:String)
data class AoristFeedback(val outcome:AoristOutcome,val submitted:String?,val optionId:String?,val accepted:List<String>,val content:JsonObject)
data class AoristQuestion(val id:Long,val verbId:Long,val formId:Long,val position:Int,val exercise:AoristExercise,
    val prompt:AoristPrompt,val options:List<AoristOption>,val draft:String,val selectedDraft:String?,val draftVersion:Long,val feedback:AoristFeedback?)
data class AoristReview(val position:Int,val exercise:AoristExercise,val prompt:AoristPrompt,val feedback:AoristFeedback?)
data class AoristPracticeState(val id:Long,val settings:AoristSettings,val status:AoristSessionStatus,val total:Int,val position:Int,
    val revision:Long,val answered:Int,val correct:Int,val incorrect:Int,val revealed:Int,val question:AoristQuestion?,val review:List<AoristReview>) {
    val active get()=status==AoristSessionStatus.IN_PROGRESS
    val skipped get()=if(active)0 else total-answered-revealed
    val accuracy:Double? get()=if(answered==0)null else Math.round(correct*1000.0/answered)/10.0
}
data class AoristProgress(val verbId:Long,val person:String,val exercise:AoristExercise,val attempts:Int,val correct:Int,
    val incorrect:Int,val revealed:Int,val category:LearningCategory,val schedule:ReviewSchedule)

internal data class AoristStoredTarget(val formId:Long,val verbId:Long,val person:String,val content:JsonObject) {
    val answers get()=AoristContent.answers(content,person)
}
internal data class AoristCandidate(val target:AoristStoredTarget,val priority:Int,val optionCount:Int)

/** Stored surface forms only. Alternatives/homographs cannot become false distractors. */
internal class AoristChoicePlanner(private val random:Random) {
    private fun wrong(target:AoristStoredTarget,stored:List<AoristStoredTarget>,shuffle:Boolean):List<String> {
        val accepted=target.answers.map(AoristContent::key).toSet()
        val same=stored.filter{it.verbId==target.verbId};val others=stored.filter{it.verbId!=target.verbId}
        val pool=if(shuffle)same.shuffled(random)+others.shuffled(random)else same+others
        val unique=linkedMapOf<String,String>()
        for(item in pool)for(text in item.answers) {
            val key=AoristContent.key(text)
            if(key !in accepted)unique.putIfAbsent(key,text)
            if(unique.size==3)return unique.values.toList()
        }
        return unique.values.toList()
    }
    fun count(target:AoristStoredTarget,stored:List<AoristStoredTarget>)=1+wrong(target,stored,false).size
    fun options(target:AoristStoredTarget,stored:List<AoristStoredTarget>):List<AoristOption> {
        val wrong=wrong(target,stored,true)
        if(wrong.isEmpty())return emptyList()
        return (listOf(target.answers.first())+wrong).shuffled(random).mapIndexed{i,text->AoristOption("o${i+1}",text)}
    }
}
internal class AoristPlanner(private val random:Random) {
    fun plan(pool:List<AoristCandidate>,size:Int):List<AoristCandidate> {
        require(size in 1..100&&pool.map{it.target.formId}.distinct().size==pool.size)
        return pool.shuffled(random).sortedBy{it.priority}.take(size).shuffled(random)
    }
}
internal fun optionsJson(options:List<AoristOption>):String=JsonArray(options.map{buildJsonObject{put("id",it.id);put("text",it.text)}}).toString()
internal fun readOptions(text:String):List<AoristOption> = AoristContent.strict(text).jsonArray.map{node->
    val e=node.jsonObject;require(e.keys==setOf("id","text"));AoristOption(AoristContent.text(e,"id"),AoristContent.text(e,"text"))
}
