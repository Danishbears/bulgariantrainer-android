package com.danishbears.bulgariantrainer.aspect

import com.danishbears.bulgariantrainer.review.StudyScope
import kotlinx.serialization.json.*
import kotlin.random.Random

enum class AspectDirection { IMPERFECTIVE_TO_PERFECTIVE, PERFECTIVE_TO_IMPERFECTIVE, MIXED }
enum class AspectOutcome { CORRECT, INCORRECT, REVEALED }
enum class AspectSessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }
data class AspectSettings(val size:Int=20,val stages:List<Int> = listOf(1),val topic:String?=null,
    val direction:AspectDirection=AspectDirection.MIXED,val scope:StudyScope=StudyScope.MIXED,val namespace:String?=null) {
    fun canonical():AspectSettings {
        if(size !in 1..100||stages.isEmpty()||stages.size>3||stages.distinct().size!=stages.size||stages.any{it !in 1..3}
            ||topic!=null&&(topic.isBlank()||topic.length>255))throw AspectException("Проверьте размер, стадии и тему занятия.")
        namespace?.let(AspectContent::namespace)
        return copy(stages=stages.sorted())
    }
    internal fun json():String=buildJsonObject {
        put("size",size);put("stages",JsonArray(stages.map(::JsonPrimitive)));put("topic",topic?.let(::JsonPrimitive)?:JsonNull)
        put("direction",direction.name);put("scope",scope.name);put("namespace",namespace?.let(::JsonPrimitive)?:JsonNull)
    }.toString()
    companion object {
        internal fun read(text:String):AspectSettings {
            val e=AspectContent.strict(text).jsonObject
            require(e.keys==setOf("size","stages","topic","direction","scope","namespace"))
            fun optional(k:String)=e.getValue(k).takeIf{it!=JsonNull}?.jsonPrimitive?.content
            return AspectSettings(e.getValue("size").jsonPrimitive.int,e.getValue("stages").jsonArray.map{it.jsonPrimitive.int},optional("topic"),
                AspectDirection.valueOf(e.getValue("direction").jsonPrimitive.content),StudyScope.valueOf(e.getValue("scope").jsonPrimitive.content),optional("namespace")).canonical()
        }
    }
}
data class AspectAvailability(val eligible:Int,val forward:Int,val reverse:Int,val offered:Int,val shorterRequired:Boolean)
data class AspectPrompt(val bulgarian:String,val russian:String,val targetRussian:String)
data class AspectFeedback(val outcome:AspectOutcome,val submitted:String?,val accepted:List<String>,val content:JsonObject)
data class AspectQuestion(val id:Long,val position:Int,val direction:AspectDirection,val prompt:AspectPrompt,val feedback:AspectFeedback?)
data class AspectReview(val position:Int,val direction:AspectDirection,val prompt:AspectPrompt,val feedback:AspectFeedback?)
data class AspectPracticeState(val id:Long,val settings:AspectSettings,val status:AspectSessionStatus,val total:Int,val position:Int,val revision:Long,
    val answered:Int,val correct:Int,val incorrect:Int,val revealed:Int,val question:AspectQuestion?,val review:List<AspectReview>) {
    val active get()=status==AspectSessionStatus.IN_PROGRESS
    val remaining get()=total-answered-revealed
    val accuracy:Double? get()=if(answered==0)null else Math.round(correct*1000.0/answered)/10.0
}
internal data class AspectCandidate(val id:Long,val forward:Int?,val reverse:Int?)
internal data class AspectPlanned(val id:Long,val direction:AspectDirection)
/** Literal bounded assignment in the pinned web AspectQuestionPlanner, not legacy typing's greedy planner. */
internal class AspectPlanner(private val random:Random) {
    private data class Path(val previous:Path?,val item:AspectPlanned)
    fun plan(candidates:List<AspectCandidate>,requested:Int,direction:AspectDirection):List<AspectPlanned> {
        require(requested in 1..100 && candidates.map{it.id}.distinct().size==candidates.size && candidates.all{it.forward!=null||it.reverse!=null})
        val pool=candidates.shuffled(random);val size=minOf(requested,pool.size)
        if(size==0)return emptyList()
        if(direction!=AspectDirection.MIXED) {
            val forward=direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE
            return pool.filter{if(forward)it.forward!=null else it.reverse!=null}.sortedBy{if(forward)it.forward else it.reverse}
                .take(size).map{AspectPlanned(it.id,direction)}.shuffled(random)
        }
        val low=maxOf(0,size-pool.count{it.reverse!=null});val high=minOf(size,pool.count{it.forward!=null})
        val floor=(size/2).coerceIn(low,high);val ceil=((size+1)/2).coerceIn(low,high)
        val maxForward=maxOf(floor,ceil);val maxReverse=size-minOf(floor,ceil)
        val cost=Array(maxForward+1){IntArray(maxReverse+1){Int.MAX_VALUE/2}}
        val paths=Array(maxForward+1){arrayOfNulls<Path>(maxReverse+1)};cost[0][0]=0
        for(c in pool)for(f in maxForward downTo 0)for(r in maxReverse downTo 0) {
            if(f>0&&c.forward!=null&&cost[f-1][r]+c.forward<cost[f][r]) {
                cost[f][r]=cost[f-1][r]+c.forward;paths[f][r]=Path(paths[f-1][r],AspectPlanned(c.id,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE))
            }
            if(r>0&&c.reverse!=null&&cost[f][r-1]+c.reverse<cost[f][r]) {
                cost[f][r]=cost[f][r-1]+c.reverse;paths[f][r]=Path(paths[f][r-1],AspectPlanned(c.id,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE))
            }
        }
        val f=when {cost[floor][size-floor]<cost[ceil][size-ceil]->floor;cost[ceil][size-ceil]<cost[floor][size-floor]->ceil;random.nextBoolean()->floor;else->ceil}
        val result=mutableListOf<AspectPlanned>();var path=paths[f][size-f]
        while(path!=null){result+=path.item;path=path.previous}
        check(result.size==size)
        return result.shuffled(random)
    }
}
