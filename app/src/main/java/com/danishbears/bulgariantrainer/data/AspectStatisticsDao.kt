package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Query

internal data class AspectOutcomeCount(val direction:String,val outcome:String,val count:Long)
internal data class AspectDueCount(val pairs:Long,val directions:Long)
internal data class AspectDirectionDue(val direction:String,val count:Long)

/** Read-only aggregates; no vocabulary/sentence tables and no materialized counters. */
@Dao internal interface AspectStatisticsDao {
    @Query("""SELECT q.direction, a.outcome, COUNT(*) AS count FROM aspect_attempts a
        JOIN aspect_questions q ON q.id=a.questionId JOIN aspect_pairs p ON p.id=q.pairId
        JOIN aspect_datasets d ON d.id=p.datasetId
        WHERE (:namespace IS NULL OR d.namespace=:namespace) GROUP BY q.direction,a.outcome""")
    suspend fun outcomes(namespace:String?):List<AspectOutcomeCount>

    @Query("""SELECT COUNT(DISTINCT s.pairId) AS pairs, COUNT(*) AS directions
        FROM aspect_review_schedules s JOIN aspect_progress g ON g.pairId=s.pairId AND g.direction=s.direction
        JOIN aspect_pairs p ON p.id=s.pairId JOIN aspect_datasets d ON d.id=p.datasetId
        WHERE p.archived=0 AND g.attempts>0 AND s.nextReview<=:now AND (:namespace IS NULL OR d.namespace=:namespace)""")
    suspend fun due(namespace:String?,now:Long):AspectDueCount

    @Query("""SELECT s.direction, COUNT(*) AS count FROM aspect_review_schedules s
        JOIN aspect_progress g ON g.pairId=s.pairId AND g.direction=s.direction
        JOIN aspect_pairs p ON p.id=s.pairId JOIN aspect_datasets d ON d.id=p.datasetId
        WHERE p.archived=0 AND g.attempts>0 AND s.nextReview<=:now AND (:namespace IS NULL OR d.namespace=:namespace)
        GROUP BY s.direction""")
    suspend fun dueDirections(namespace:String?,now:Long):List<AspectDirectionDue>
}
