package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Query

internal data class AoristOutcomeCount(val person:String,val exercise:String,val outcome:String,val count:Long)
internal data class AoristDueCount(val person:String,val exercise:String,val count:Long)

@Dao internal interface AoristStatisticsDao {
    // Historical attempts remain counted after editing/archiving content and in unfinished sessions.
    @Query("""SELECT q.person,q.exercise,a.outcome,COUNT(*) AS count FROM aorist_attempts a
        JOIN aorist_questions q ON q.id=a.questionId JOIN aorist_verbs v ON v.id=q.verbId
        JOIN aorist_datasets d ON d.id=v.datasetId WHERE (:dataset IS NULL OR d.externalId=:dataset)
        GROUP BY q.person,q.exercise,a.outcome""")
    suspend fun outcomes(dataset:String?):List<AoristOutcomeCount>

    // One due target is (stable form ID, exercise), not a distinct verb or a historical attempt.
    @Query("""SELECT f.person,s.exercise,COUNT(*) AS count FROM aorist_review_schedules s
        JOIN aorist_forms f ON f.id=s.formId JOIN aorist_verbs v ON v.id=f.verbId
        JOIN aorist_datasets d ON d.id=v.datasetId WHERE v.archived=0 AND s.nextReview<=:now
        AND (:dataset IS NULL OR d.externalId=:dataset) GROUP BY f.person,s.exercise""")
    suspend fun due(dataset:String?,now:Long):List<AoristDueCount>
}
