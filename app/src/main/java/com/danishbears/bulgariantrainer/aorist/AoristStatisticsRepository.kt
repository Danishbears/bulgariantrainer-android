package com.danishbears.bulgariantrainer.aorist

import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.withContentTransaction
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.map

data class AoristStatisticsCounts(val answered:Long=0,val correct:Long=0,val incorrect:Long=0,
    val revealed:Long=0,val dueForReview:Long=0) {
    internal operator fun plus(other:AoristStatisticsCounts)=AoristStatisticsCounts(answered+other.answered,
        correct+other.correct,incorrect+other.incorrect,revealed+other.revealed,dueForReview+other.dueForReview)
}
data class AoristStatisticsCell(val person:String,val exercise:AoristExercise,val counts:AoristStatisticsCounts)
data class AoristStatistics(val datasetId:String?,val asOf:Instant,val counts:AoristStatisticsCounts,
    val persons:Map<String,AoristStatisticsCounts>,val exercises:Map<AoristExercise,AoristStatisticsCounts>,val cells:List<AoristStatisticsCell>)

/** Read-only aggregates of committed attempts/schedules; never replay or regrade learning history. */
class AoristStatisticsRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC()) {
    private val dao=db.aoristStatisticsDao()
    fun observeChanges()=db.invalidationTracker.createFlow("aorist_attempts","aorist_questions","aorist_forms",
        "aorist_verbs","aorist_datasets","aorist_review_schedules").map{Unit}
    suspend fun overview(datasetId:String?=null):AoristStatistics {
        datasetId?.let(AoristContent::identifier)
        return db.withContentTransaction {
            val at=clock.instant()
            val outcomes=dao.outcomes(datasetId).groupBy{it.person to it.exercise}
            val due=dao.due(datasetId,at.toEpochMilli()).associate{(it.person to it.exercise) to it.count}
            val cells=AoristContent.persons.flatMap{person->AoristExercise.entries.map{exercise->
                val key=person to exercise.name
                val counts=outcomes[key].orEmpty().associate{it.outcome to it.count}
                val correct=counts[AoristOutcome.CORRECT.name]?:0L
                val incorrect=counts[AoristOutcome.INCORRECT.name]?:0L
                AoristStatisticsCell(person,exercise,AoristStatisticsCounts(correct+incorrect,correct,incorrect,
                    counts[AoristOutcome.REVEALED.name]?:0L,due[key]?:0L))
            }}
            fun sum(rows:List<AoristStatisticsCell>)=rows.fold(AoristStatisticsCounts()){total,row->total+row.counts}
            AoristStatistics(datasetId,at,sum(cells),AoristContent.persons.associateWith{p->sum(cells.filter{it.person==p})},
                AoristExercise.entries.associateWith{e->sum(cells.filter{it.exercise==e})},cells)
        }
    }
}
