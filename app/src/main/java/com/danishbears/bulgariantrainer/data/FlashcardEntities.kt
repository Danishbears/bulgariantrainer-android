package com.danishbears.bulgariantrainer.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "flashcard_sessions", indices = [Index(value = ["request_id"], unique = true), Index(value = ["status", "id"])])
internal data class FlashcardSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "requested_size") val requestedSize: Int,
    val direction: String,
    val status: String = "IN_PROGRESS",
    val total: Int,
    @ColumnInfo(name = "current_position") val currentPosition: Int = 1,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
@Entity(tableName = "flashcard_cards", foreignKeys = [
    ForeignKey(entity = FlashcardSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["session_id", "position"], unique = true), Index(value = ["session_id", "word_id"], unique = true), Index(value = ["word_id"])])
internal data class FlashcardCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val position: Int, val direction: String,
    @ColumnInfo(name = "bulgarian_text") val bulgarian: String,
    @ColumnInfo(name = "russian_json") val russianJson: String,
    @ColumnInfo(name = "revealed_at") val revealedAt: Long? = null,
)
@Entity(tableName = "flashcard_attempts", foreignKeys = [
    ForeignKey(entity = FlashcardCardEntity::class, parentColumns = ["id"], childColumns = ["card_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["card_id"], unique = true)])
internal data class FlashcardAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val known: Boolean,
    @ColumnInfo(name = "rated_at") val ratedAt: Long,
)
@Entity(tableName = "recognition_progress", foreignKeys = [
    ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT),
])
internal data class RecognitionProgressEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: Long,
    val attempts: Int, val known: Int,
    @ColumnInfo(name = "consecutive_known") val consecutiveKnown: Int,
    @ColumnInfo(name = "last_rated_at") val lastRatedAt: Long,
)
internal data class FlashcardCardWithAttempt(
    @Embedded val card: FlashcardCardEntity,
    @Relation(parentColumn = "id", entityColumn = "card_id") val attempt: FlashcardAttemptEntity?,
)
internal data class FlashcardSessionGraph(
    @Embedded val session: FlashcardSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id", entity = FlashcardCardEntity::class)
    val cards: List<FlashcardCardWithAttempt>,
)
