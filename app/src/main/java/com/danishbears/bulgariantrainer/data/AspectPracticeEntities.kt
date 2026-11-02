package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Entity(tableName="aspect_sessions",indices=[Index(value=["requestId"],unique=true)])
internal data class AspectSessionEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val requestId:String,val settings:String,val allowShorter:Boolean,
    val total:Int,val position:Int=1,val revision:Long=0,val status:String="IN_PROGRESS",val startedAt:Long,val completedAt:Long?=null)
@Entity(tableName="aspect_questions",foreignKeys=[ForeignKey(entity=AspectSessionEntity::class,parentColumns=["id"],childColumns=["sessionId"]),
    ForeignKey(entity=AspectPairEntity::class,parentColumns=["id"],childColumns=["pairId"])],indices=[Index(value=["sessionId","position"],unique=true),Index(value=["sessionId","pairId"],unique=true),Index("pairId")])
internal data class AspectQuestionEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val sessionId:Long,val pairId:Long,val position:Int,val direction:String,
    val snapshot:String,val contentHash:String,val acknowledged:Boolean=false,val revealedAt:Long?=null)
@Entity(tableName="aspect_attempts",foreignKeys=[ForeignKey(entity=AspectQuestionEntity::class,parentColumns=["id"],childColumns=["questionId"])],indices=[Index(value=["questionId"],unique=true)])
internal data class AspectAttemptEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val questionId:Long,val submitted:String?,val normalized:String?,val outcome:String,val createdAt:Long)
@Entity(tableName="aspect_progress",primaryKeys=["pairId","direction"],foreignKeys=[ForeignKey(entity=AspectPairEntity::class,parentColumns=["id"],childColumns=["pairId"])])
internal data class AspectProgressEntity(val pairId:Long,val direction:String,val attempts:Int,val correct:Int,val revealed:Int,val lastReviewedAt:Long)
@Entity(tableName="aspect_review_schedules",primaryKeys=["pairId","direction"],foreignKeys=[ForeignKey(entity=AspectProgressEntity::class,parentColumns=["pairId","direction"],childColumns=["pairId","direction"])])
internal data class AspectScheduleEntity(val pairId:Long,val direction:String,val attempts:Int,val successes:Int,val streak:Int,val intervalDays:Int,val lastReview:Long,val nextReview:Long)
internal data class AspectQuestionGraph(@Embedded val question:AspectQuestionEntity,
    @Relation(parentColumn="id",entityColumn="questionId")val attempt:AspectAttemptEntity?)
internal data class AspectSessionGraph(@Embedded val session:AspectSessionEntity,
    @Relation(entity=AspectQuestionEntity::class,parentColumn="id",entityColumn="sessionId")val questions:List<AspectQuestionGraph>)
@Dao internal interface AspectPracticeDao {
    @Query("SELECT p.id FROM aspect_pairs p JOIN aspect_datasets d ON p.datasetId=d.id WHERE p.archived=0 AND p.studyStage IN (:stages) AND (:topic IS NULL OR p.topic=:topic) AND (:namespace IS NULL OR d.namespace=:namespace) ORDER BY p.id")
    suspend fun candidates(stages:List<Int>,topic:String?,namespace:String?):List<Long>
    @Query("SELECT * FROM aspect_review_schedules")suspend fun schedules():List<AspectScheduleEntity>
    @Query("SELECT * FROM aspect_review_schedules WHERE pairId=:id AND direction=:direction")suspend fun schedule(id:Long,direction:String):AspectScheduleEntity?
    @Query("SELECT * FROM aspect_progress WHERE pairId=:id AND direction=:direction")suspend fun progress(id:Long,direction:String):AspectProgressEntity?
    @Query("SELECT * FROM aspect_progress")suspend fun progressRows():List<AspectProgressEntity>
    @Upsert suspend fun saveProgress(value:AspectProgressEntity)
    @Upsert suspend fun saveSchedule(value:AspectScheduleEntity)
    @Query("SELECT id FROM aspect_sessions WHERE requestId=:request")suspend fun request(request:String):Long?
    @Query("SELECT * FROM aspect_sessions WHERE id=:id")suspend fun session(id:Long):AspectSessionEntity?
    @Transaction @Query("SELECT * FROM aspect_sessions WHERE id=:id")suspend fun graph(id:Long):AspectSessionGraph?
    @Transaction @Query("SELECT * FROM aspect_sessions WHERE id=:id")fun observe(id:Long):kotlinx.coroutines.flow.Flow<AspectSessionGraph?>
    @Query("SELECT id FROM aspect_sessions ORDER BY CASE WHEN status='IN_PROGRESS' THEN 0 ELSE 1 END,id DESC LIMIT 1")suspend fun latest():Long?
    @Query("SELECT id FROM aspect_sessions ORDER BY id")suspend fun allIds():List<Long>
    @Insert suspend fun insertSession(value:AspectSessionEntity):Long
    @Update suspend fun updateSession(value:AspectSessionEntity)
    @Insert suspend fun insertQuestion(value:AspectQuestionEntity):Long
    @Update suspend fun updateQuestion(value:AspectQuestionEntity)
    @Insert suspend fun insertAttempt(value:AspectAttemptEntity):Long
}
