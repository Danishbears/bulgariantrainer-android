package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Entity(tableName = "sentence_sessions", indices = [Index(value = ["request_id"], unique = true), Index(value = ["status", "id"])])
internal data class SentenceSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "requested_size") val requestedSize: Int,
    val status: String = "IN_PROGRESS", val total: Int,
    @ColumnInfo(name = "current_position") val currentPosition: Int = 1,
    val revision: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "study_scope", defaultValue = "'MIXED'") val scope: String = "MIXED",
)
@Entity(tableName = "sentence_questions", foreignKeys = [
    ForeignKey(entity = SentenceSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = SentenceEntity::class, parentColumns = ["id"], childColumns = ["sentence_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["session_id", "position"], unique = true), Index(value = ["session_id", "sentence_id"], unique = true), Index(value = ["sentence_id"])])
internal data class SentenceQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    val position: Int,
    @ColumnInfo(name = "russian_text") val russian: String,
    @ColumnInfo(name = "accepted_json") val acceptedJson: String,
    val explanation: String?, val difficulty: Int?, val topic: String?,
    val acknowledged: Boolean = false,
)
@Entity(tableName = "sentence_attempts", foreignKeys = [
    ForeignKey(entity = SentenceQuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["question_id"], unique = true)])
internal data class SentenceAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    val submitted: String,
    @ColumnInfo(name = "normalized_answer") val normalizedAnswer: String,
    @ColumnInfo(name = "automatic_match") val automaticMatch: Boolean,
    @ColumnInfo(name = "self_rating") val selfRating: String? = null,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
    @ColumnInfo(name = "evaluated_at") val evaluatedAt: Long? = null,
)
@Entity(tableName = "sentence_progress", foreignKeys = [
    ForeignKey(entity = SentenceEntity::class, parentColumns = ["id"], childColumns = ["sentence_id"], onDelete = ForeignKey.RESTRICT),
])
internal data class SentenceProgressEntity(
    @PrimaryKey @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    val attempts: Int, val correct: Int, val partial: Int, val incorrect: Int,
    @ColumnInfo(name = "consecutive_correct") val consecutiveCorrect: Int,
    @ColumnInfo(name = "last_evaluated_at") val lastEvaluatedAt: Long,
)
internal data class SentenceQuestionWithAttempt(
    @Embedded val question: SentenceQuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "question_id") val attempt: SentenceAttemptEntity?,
)
internal data class SentenceSessionGraph(
    @Embedded val session: SentenceSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id", entity = SentenceQuestionEntity::class)
    val questions: List<SentenceQuestionWithAttempt>,
)
