package com.danishbears.bulgariantrainer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ReviewDao {
    @Query("SELECT * FROM word_review_schedules WHERE word_id = :id AND mode = :mode")
    suspend fun word(id: Long, mode: String): WordReviewScheduleEntity?
    @Query("SELECT * FROM word_review_schedules") suspend fun words(): List<WordReviewScheduleEntity>
    @Query("SELECT * FROM sentence_review_schedules WHERE sentence_id = :id")
    suspend fun sentence(id: Long): SentenceReviewScheduleEntity?
    @Query("SELECT * FROM sentence_review_schedules") suspend fun sentences(): List<SentenceReviewScheduleEntity>
    @Query("SELECT id FROM words w WHERE archived = 0 AND EXISTS (SELECT 1 FROM translations t WHERE t.word_id = w.id) ORDER BY id")
    suspend fun wordIds(): List<Long>
    @Query("SELECT id FROM sentences WHERE archived = 0 AND approved = 1 ORDER BY id")
    suspend fun sentenceIds(): List<Long>
    @Upsert suspend fun save(row: WordReviewScheduleEntity)
    @Upsert suspend fun save(row: SentenceReviewScheduleEntity)
    // A Room invalidation signal for eligibility and time-driven projections; callers also tick/reload time.
    @Query("""SELECT (SELECT COUNT(*) FROM words) + (SELECT COUNT(*) FROM translations) +
        (SELECT COUNT(*) FROM sentences) + (SELECT COUNT(*) FROM word_review_schedules) +
        (SELECT COUNT(*) FROM sentence_review_schedules)""")
    fun observeChanges(): Flow<Long>
}
