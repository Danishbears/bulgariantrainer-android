package com.danishbears.bulgariantrainer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao internal interface AoristPracticeDao {
    @Query("""SELECT f.*,d.externalId AS namespace FROM aorist_forms f JOIN aorist_verbs v ON v.id=f.verbId
        JOIN aorist_datasets d ON d.id=v.datasetId WHERE v.archived=0
        AND (:namespace IS NULL OR d.externalId=:namespace) AND (:aspect IS NULL OR v.aspect=:aspect)
        AND (:studyGroup IS NULL OR v.studyGroup=:studyGroup) ORDER BY f.id""")
    suspend fun targets(namespace:String?,aspect:String?,studyGroup:String?):List<AoristTarget>
    @Query("SELECT * FROM aorist_forms WHERE id=:id") suspend fun form(id:Long):AoristFormEntity?
    @Query("SELECT * FROM aorist_review_schedules") suspend fun schedules():List<AoristScheduleEntity>
    @Query("SELECT * FROM aorist_review_schedules WHERE formId=:form AND exercise=:exercise") suspend fun schedule(form:Long,exercise:String):AoristScheduleEntity?
    @Query("SELECT * FROM aorist_progress WHERE formId=:form AND exercise=:exercise") suspend fun progress(form:Long,exercise:String):AoristProgressEntity?
    @Query("SELECT * FROM aorist_progress") suspend fun progressRows():List<AoristProgressEntity>
    @Upsert suspend fun saveProgress(value:AoristProgressEntity)
    @Upsert suspend fun saveSchedule(value:AoristScheduleEntity)
    @Query("SELECT * FROM aorist_preferences WHERE id=1") suspend fun preferences():AoristPreferencesEntity?
    @Upsert suspend fun savePreferences(value:AoristPreferencesEntity)
    @Query("SELECT id FROM aorist_sessions WHERE requestId=:request") suspend fun request(request:String):Long?
    @Query("SELECT id FROM aorist_sessions ORDER BY CASE WHEN status='IN_PROGRESS' THEN 0 ELSE 1 END,id DESC LIMIT 1") suspend fun latest():Long?
    @Query("SELECT id FROM aorist_sessions ORDER BY id") suspend fun allIds():List<Long>
    @Transaction @Query("SELECT * FROM aorist_sessions WHERE id=:id") suspend fun graph(id:Long):AoristSessionGraph?
    @Transaction @Query("SELECT * FROM aorist_sessions WHERE id=:id") fun observe(id:Long):Flow<AoristSessionGraph?>
    @Insert suspend fun insertSession(value:AoristSessionEntity):Long
    @Update suspend fun updateSession(value:AoristSessionEntity)
    @Insert suspend fun insertQuestion(value:AoristQuestionEntity):Long
    @Update suspend fun updateQuestion(value:AoristQuestionEntity)
    @Insert suspend fun insertAttempt(value:AoristAttemptEntity):Long
}
