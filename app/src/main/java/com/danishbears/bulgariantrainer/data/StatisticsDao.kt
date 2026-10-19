package com.danishbears.bulgariantrainer.data

import androidx.room.*
import com.danishbears.bulgariantrainer.statistics.*
import kotlinx.coroutines.flow.Flow

@Dao
internal interface StatisticsDao {
    @Transaction @Query("SELECT * FROM words ORDER BY id") suspend fun words(): List<WordWithTranslations>
    @Query("SELECT id FROM sentences WHERE approved = 1 AND archived = 0") suspend fun trainableSentences(): List<Long>
    @Query("SELECT word_id FROM recognition_progress WHERE attempts > 0 UNION SELECT word_id FROM typing_progress WHERE attempts > 0")
    suspend fun studiedWords(): List<Long>
    @Query("""SELECT a.id, 'FLASHCARD' AS mode, q.session_id AS sessionId, q.word_id AS wordId,
        q.direction, NULL AS submitted, CASE a.known WHEN 1 THEN 'CORRECT' ELSE 'INCORRECT' END AS result,
        'SELF_ASSESSED' AS grading, a.rated_at AS createdAt FROM flashcard_attempts a JOIN flashcard_cards q ON q.id = a.card_id
        UNION ALL SELECT a.id, 'TYPING', q.session_id, q.word_id, q.direction, a.submitted,
        CASE a.correct WHEN 1 THEN 'CORRECT' ELSE 'INCORRECT' END, 'AUTOMATIC', a.submitted_at
        FROM typing_attempts a JOIN typing_questions q ON q.id = a.question_id
        UNION ALL SELECT a.id, 'SENTENCE', q.session_id, NULL, 'RU_TO_BG', a.submitted,
        CASE a.automatic_match WHEN 1 THEN 'CORRECT' ELSE COALESCE(a.self_rating, 'PENDING') END,
        CASE WHEN a.automatic_match = 1 THEN 'AUTOMATIC' WHEN a.self_rating IS NOT NULL THEN 'SELF_ASSESSED' ELSE 'UNCLASSIFIED' END,
        a.submitted_at FROM sentence_attempts a JOIN sentence_questions q ON q.id = a.question_id""")
    suspend fun attempts(): List<StatisticsAttempt>
    @Query("""SELECT id, 'FLASHCARD' AS mode, status, started_at AS startedAt, completed_at AS completedAt, total FROM flashcard_sessions
        UNION ALL SELECT id, 'TYPING', status, started_at, completed_at, total FROM typing_sessions
        UNION ALL SELECT id, 'SENTENCE', status, started_at, completed_at, total FROM sentence_sessions""")
    suspend fun sessions(): List<StatisticsSession>
    @Query("""SELECT (SELECT COUNT(*) FROM words) + (SELECT COUNT(*) FROM translations) + (SELECT COUNT(*) FROM sentences) +
        (SELECT COUNT(*) FROM recognition_progress) + (SELECT COUNT(*) FROM typing_progress) + (SELECT COUNT(*) FROM sentence_progress) +
        (SELECT COUNT(*) FROM word_review_schedules) + (SELECT COUNT(*) FROM sentence_review_schedules) +
        (SELECT COUNT(*) FROM flashcard_sessions) + (SELECT COUNT(*) FROM typing_sessions) + (SELECT COUNT(*) FROM sentence_sessions) +
        (SELECT COUNT(*) FROM flashcard_attempts) + (SELECT COUNT(*) FROM typing_attempts) + (SELECT COUNT(*) FROM sentence_attempts) +
        (SELECT COUNT(*) FROM flashcard_cards) + (SELECT COUNT(*) FROM typing_questions) + (SELECT COUNT(*) FROM sentence_questions)""")
    fun observeChanges(): Flow<Long>
}
