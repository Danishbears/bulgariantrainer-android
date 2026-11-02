package com.danishbears.bulgariantrainer.aspect

import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.withContentTransaction
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AspectDirectionStatistics(val direction:AspectDirection,val answered:Long,val correct:Long,val incorrect:Long,val revealed:Long,val dueForReview:Long)
data class AspectStatistics(val namespace:String?,val answered:Long,val correct:Long,val incorrect:Long,val revealed:Long,
    val dueForReview:Long,val dueDirections:Long,val asOf:Instant,val directions:List<AspectDirectionStatistics>)

/** Pinned web AspectStatisticsService semantics; intentionally independent from StatisticsRepository. */
class AspectStatisticsRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC()) {
    private val dao=db.aspectStatisticsDao()
    fun observeChanges()=db.invalidationTracker.createFlow("aspect_attempts","aspect_questions","aspect_pairs","aspect_datasets","aspect_progress","aspect_review_schedules").map{Unit}
    suspend fun overview(namespace:String?=null):AspectStatistics=withContext(Dispatchers.Default) {
        namespace?.let(AspectContent::namespace)
        db.withContentTransaction {
            val at=clock.instant();val outcomes=dao.outcomes(namespace);val due=dao.due(namespace,at.toEpochMilli())
            val directionsDue=dao.dueDirections(namespace,at.toEpochMilli()).associate{it.direction to it.count}
            val directions=listOf(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE).map{direction->
                fun count(outcome:AspectOutcome)=outcomes.firstOrNull{it.direction==direction.name&&it.outcome==outcome.name}?.count?:0L
                val correct=count(AspectOutcome.CORRECT);val incorrect=count(AspectOutcome.INCORRECT)
                AspectDirectionStatistics(direction,correct+incorrect,correct,incorrect,count(AspectOutcome.REVEALED),directionsDue[direction.name]?:0L)
            }
            AspectStatistics(namespace,directions.sumOf{it.answered},directions.sumOf{it.correct},directions.sumOf{it.incorrect},directions.sumOf{it.revealed},due.pairs,due.directions,at,directions)
        }
    }
}
