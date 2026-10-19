package com.danishbears.bulgariantrainer.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SentenceDao {
    @Transaction @Query("SELECT * FROM sentences WHERE archived = :archived ORDER BY id")
    fun paging(archived: Boolean): PagingSource<Int, SentenceWithAlternatives>
    @Transaction @Query("SELECT * FROM sentences WHERE id = :id")
    suspend fun get(id: Long): SentenceWithAlternatives?
    @Query("SELECT COUNT(*) FROM sentences WHERE archived = 0 AND approved = 1")
    fun observeAvailable(): Flow<Long>
    @Query("SELECT id FROM sentences WHERE archived = 0 AND approved = 1 ORDER BY id")
    suspend fun candidateIds(): List<Long>
    @Transaction @Query("SELECT * FROM sentences WHERE id IN (:ids) ORDER BY id")
    suspend fun candidates(ids: List<Long>): List<SentenceWithAlternatives>
    @Query("SELECT id FROM sentences WHERE normalized_russian = :key")
    suspend fun forKey(key: String): Long?
    @Insert suspend fun insert(sentence: SentenceEntity): Long
    @Update suspend fun update(sentence: SentenceEntity)
    @Query("DELETE FROM sentence_alternatives WHERE sentence_id = :id AND normalized_text NOT IN (:keys)")
    suspend fun removeAlternativesExcept(id: Long, keys: List<String>)
    @Insert suspend fun insertAlternative(alternative: SentenceAlternativeEntity): Long
    @Update suspend fun updateAlternative(alternative: SentenceAlternativeEntity)
    @Query("SELECT * FROM sentence_sessions WHERE request_id = :requestId")
    suspend fun forRequest(requestId: String): SentenceSessionEntity?
    @Query("SELECT id FROM sentence_sessions ORDER BY CASE WHEN status = 'IN_PROGRESS' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun recoverableId(): Long?
    @Transaction @Query("SELECT * FROM sentence_sessions WHERE id = :id")
    suspend fun graph(id: Long): SentenceSessionGraph?
    @Transaction @Query("SELECT * FROM sentence_sessions WHERE id = :id")
    fun observe(id: Long): Flow<SentenceSessionGraph?>
    @Query("SELECT * FROM sentence_sessions WHERE id = :id")
    suspend fun session(id: Long): SentenceSessionEntity?
    @Query("SELECT * FROM sentence_questions WHERE id = :questionId AND session_id = :sessionId")
    suspend fun question(sessionId: Long, questionId: Long): SentenceQuestionEntity?
    @Query("SELECT * FROM sentence_attempts WHERE question_id = :questionId")
    suspend fun attempt(questionId: Long): SentenceAttemptEntity?
    @Query("SELECT * FROM sentence_progress WHERE sentence_id = :sentenceId")
    suspend fun progress(sentenceId: Long): SentenceProgressEntity?
    @Insert suspend fun insertSession(session: SentenceSessionEntity): Long
    @Insert suspend fun insertQuestion(question: SentenceQuestionEntity): Long
    @Insert suspend fun insertAttempt(attempt: SentenceAttemptEntity): Long
    @Update suspend fun updateSession(session: SentenceSessionEntity)
    @Update suspend fun updateQuestion(question: SentenceQuestionEntity)
    @Update suspend fun updateAttempt(attempt: SentenceAttemptEntity)
    @Query("UPDATE sentence_questions SET acknowledged = 1 WHERE session_id = :id AND id IN (SELECT question_id FROM sentence_attempts)")
    suspend fun acknowledgeSubmitted(id: Long)
    @Upsert suspend fun saveProgress(progress: SentenceProgressEntity)
}
