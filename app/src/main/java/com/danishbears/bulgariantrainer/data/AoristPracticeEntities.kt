package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Entity(tableName="aorist_sessions",indices=[Index(value=["requestId"],unique=true)])
internal data class AoristSessionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val requestId:String,
    val settings:String,val allowShorter:Boolean,val allowFewerOptions:Boolean,val policyVersion:Int=1,
    val total:Int,val position:Int=1,val revision:Long=0,val status:String="IN_PROGRESS",val startedAt:Long,val completedAt:Long?=null)
@Entity(tableName="aorist_questions",foreignKeys=[ForeignKey(entity=AoristSessionEntity::class,parentColumns=["id"],childColumns=["sessionId"]),
    ForeignKey(entity=AoristFormEntity::class,parentColumns=["id"],childColumns=["formId"]),
    ForeignKey(entity=AoristVerbEntity::class,parentColumns=["id"],childColumns=["verbId"])],
    indices=[Index(value=["sessionId","position"],unique=true),Index(value=["sessionId","formId"],unique=true),Index("formId"),Index("verbId")])
internal data class AoristQuestionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val sessionId:Long,val formId:Long,
    val verbId:Long,val person:String,val exercise:String,val position:Int,val snapshot:String,val contentHash:String,
    val options:String,val acknowledged:Boolean=false,val revealedAt:Long?=null,val draft:String="",val selectedDraft:String?=null,val draftVersion:Long=0)
@Entity(tableName="aorist_attempts",foreignKeys=[ForeignKey(entity=AoristQuestionEntity::class,parentColumns=["id"],childColumns=["questionId"])],
    indices=[Index(value=["questionId"],unique=true)])
internal data class AoristAttemptEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val questionId:Long,
    val submitted:String?,val optionId:String?,val normalized:String?,val outcome:String,val createdAt:Long)
@Entity(tableName="aorist_progress",primaryKeys=["formId","exercise"],
    foreignKeys=[ForeignKey(entity=AoristFormEntity::class,parentColumns=["id"],childColumns=["formId"])])
internal data class AoristProgressEntity(val formId:Long,val exercise:String,val attempts:Int,val correct:Int,
    val incorrect:Int,val revealed:Int,val lastReviewedAt:Long)
@Entity(tableName="aorist_review_schedules",primaryKeys=["formId","exercise"],
    foreignKeys=[ForeignKey(entity=AoristProgressEntity::class,parentColumns=["formId","exercise"],childColumns=["formId","exercise"])])
internal data class AoristScheduleEntity(val formId:Long,val exercise:String,val attempts:Int,val successes:Int,
    val streak:Int,val intervalDays:Int,val lastReview:Long,val nextReview:Long)
@Entity(tableName="aorist_preferences")
internal data class AoristPreferencesEntity(@PrimaryKey val id:Int=1,val settings:String)

internal data class AoristQuestionGraph(@Embedded val question:AoristQuestionEntity,
    @Relation(parentColumn="id",entityColumn="questionId") val attempt:AoristAttemptEntity?)
internal data class AoristSessionGraph(@Embedded val session:AoristSessionEntity,
    @Relation(entity=AoristQuestionEntity::class,parentColumn="id",entityColumn="sessionId") val questions:List<AoristQuestionGraph>)
internal data class AoristTarget(@Embedded val form:AoristFormEntity,val namespace:String)
