package com.danishbears.bulgariantrainer.data

import androidx.room.*
import com.danishbears.bulgariantrainer.review.ReviewSchedule
import java.time.Instant

@Entity(tableName = "word_review_schedules", primaryKeys = ["word_id", "mode"], foreignKeys = [
    ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["mode", "next_review_at"])])
internal data class WordReviewScheduleEntity(
    @ColumnInfo(name = "word_id") val wordId: Long, val mode: String,
    val attempts: Int, val successes: Int, val streak: Int,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "last_review_at") val lastReview: Long?,
    @ColumnInfo(name = "next_review_at") val nextReview: Long?,
    val inherited: Boolean,
)
@Entity(tableName = "sentence_review_schedules", foreignKeys = [
    ForeignKey(entity = SentenceEntity::class, parentColumns = ["id"], childColumns = ["sentence_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["next_review_at"])])
internal data class SentenceReviewScheduleEntity(
    @PrimaryKey @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    val attempts: Int, val successes: Int, val streak: Int,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "last_review_at") val lastReview: Long?,
    @ColumnInfo(name = "next_review_at") val nextReview: Long?,
    val inherited: Boolean,
)
internal fun WordReviewScheduleEntity.schedule() = ReviewSchedule(attempts, successes, streak, intervalDays,
    lastReview?.let(Instant::ofEpochMilli), nextReview?.let(Instant::ofEpochMilli), inherited)
internal fun SentenceReviewScheduleEntity.schedule() = ReviewSchedule(attempts, successes, streak, intervalDays,
    lastReview?.let(Instant::ofEpochMilli), nextReview?.let(Instant::ofEpochMilli), inherited)
internal fun ReviewSchedule.wordEntity(id: Long, mode: String) = WordReviewScheduleEntity(id, mode, attempts, successes, streak,
    intervalDays, lastReview?.toEpochMilli(), nextReview?.toEpochMilli(), inherited)
internal fun ReviewSchedule.sentenceEntity(id: Long) = SentenceReviewScheduleEntity(id, attempts, successes, streak,
    intervalDays, lastReview?.toEpochMilli(), nextReview?.toEpochMilli(), inherited)
