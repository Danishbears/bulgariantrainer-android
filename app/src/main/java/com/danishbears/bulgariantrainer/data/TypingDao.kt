package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TypingDao {
    @Query("SELECT COUNT(*) FROM words w WHERE w.archived = 0 AND EXISTS(SELECT 1 FROM translations t WHERE t.word_id = w.id)")
    fun observeAvailable(): Flow<Long>
    @Query("SELECT id FROM words w WHERE w.archived = 0 AND EXISTS(SELECT 1 FROM translations t WHERE t.word_id = w.id) ORDER BY id")
    suspend fun candidateIds(): List<Long>
    @Transaction
    @Query("SELECT * FROM words WHERE id IN (:ids) ORDER BY id")
    suspend fun candidates(ids: List<Long>): List<WordWithTranslations>
    @Query("SELECT * FROM typing_sessions WHERE request_id = :requestId")
    suspend fun forRequest(requestId: String): TypingSessionEntity?
    @Query("SELECT id FROM typing_sessions ORDER BY CASE WHEN status = 'IN_PROGRESS' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun recoverableId(): Long?
    @Transaction
    @Query("SELECT * FROM typing_sessions WHERE id = :id")
    suspend fun graph(id: Long): TypingSessionGraph?
    @Transaction
    @Query("SELECT * FROM typing_sessions WHERE id = :id")
    fun observe(id: Long): Flow<TypingSessionGraph?>
    @Query("SELECT * FROM typing_sessions WHERE id = :id")
    suspend fun session(id: Long): TypingSessionEntity?
    @Query("SELECT * FROM typing_questions WHERE id = :questionId AND session_id = :sessionId")
    suspend fun question(sessionId: Long, questionId: Long): TypingQuestionEntity?
    @Query("SELECT * FROM typing_attempts WHERE question_id = :questionId")
    suspend fun attempt(questionId: Long): TypingAttemptEntity?
    @Query("SELECT * FROM typing_progress WHERE word_id = :wordId AND direction = :direction")
    suspend fun progress(wordId: Long, direction: String): TypingProgressEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM typing_questions WHERE word_id = :wordId) OR EXISTS(SELECT 1 FROM typing_progress WHERE word_id = :wordId)")
    suspend fun wordInUse(wordId: Long): Boolean
    @Insert suspend fun insertSession(session: TypingSessionEntity): Long
    @Insert suspend fun insertQuestion(question: TypingQuestionEntity): Long
    @Insert suspend fun insertAttempt(attempt: TypingAttemptEntity): Long
    @Update suspend fun updateSession(session: TypingSessionEntity)
    @Update suspend fun updateQuestion(question: TypingQuestionEntity)
    @Query("UPDATE typing_questions SET acknowledged = 1 WHERE session_id = :sessionId AND id IN (SELECT question_id FROM typing_attempts)")
    suspend fun acknowledgeAnswered(sessionId: Long)
    @Upsert suspend fun saveProgress(progress: TypingProgressEntity)
}
