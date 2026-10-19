package com.danishbears.bulgariantrainer.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "typing_sessions", indices = [Index(value = ["request_id"], unique = true), Index(value = ["status", "id"])])
internal data class TypingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "allow_shorter") val allowShorter: Boolean,
    val status: String = "IN_PROGRESS", val total: Int,
    @ColumnInfo(name = "current_position") val currentPosition: Int = 1,
    val revision: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
@Entity(tableName = "typing_questions", foreignKeys = [
    ForeignKey(entity = TypingSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["session_id", "position"], unique = true), Index(value = ["session_id", "word_id"], unique = true), Index(value = ["word_id"])])
internal data class TypingQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val position: Int, val direction: String,
    @ColumnInfo(name = "bulgarian_text") val bulgarian: String,
    @ColumnInfo(name = "russian_json") val russianJson: String,
    val acknowledged: Boolean = false,
)
@Entity(tableName = "typing_attempts", foreignKeys = [
    ForeignKey(entity = TypingQuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["question_id"], unique = true)])
internal data class TypingAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    val submitted: String,
    @ColumnInfo(name = "normalized_answer") val normalizedAnswer: String,
    val correct: Boolean,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
)
@Entity(tableName = "typing_progress", primaryKeys = ["word_id", "direction"], foreignKeys = [
    ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT),
])
internal data class TypingProgressEntity(
    @ColumnInfo(name = "word_id") val wordId: Long, val direction: String,
    val attempts: Int, val correct: Int,
    @ColumnInfo(name = "consecutive_correct") val consecutiveCorrect: Int,
    @ColumnInfo(name = "last_submitted_at") val lastSubmittedAt: Long,
)
internal data class TypingQuestionWithAttempt(
    @Embedded val question: TypingQuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "question_id") val attempt: TypingAttemptEntity?,
)
internal data class TypingSessionGraph(
    @Embedded val session: TypingSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id", entity = TypingQuestionEntity::class)
    val questions: List<TypingQuestionWithAttempt>,
)
