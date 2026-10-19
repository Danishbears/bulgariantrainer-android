package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FlashcardDao {
    @Query("SELECT COUNT(*) FROM words w WHERE w.archived = 0 AND EXISTS(SELECT 1 FROM translations t WHERE t.word_id = w.id)")
    fun observeAvailable(): Flow<Long>
    @Transaction
    @Query("SELECT * FROM words w WHERE w.archived = 0 AND EXISTS(SELECT 1 FROM translations t WHERE t.word_id = w.id) ORDER BY RANDOM() LIMIT :size")
    suspend fun candidates(size: Int): List<WordWithTranslations>
    @Query("SELECT * FROM flashcard_sessions WHERE request_id = :requestId")
    suspend fun forRequest(requestId: String): FlashcardSessionEntity?
    @Query("SELECT id FROM flashcard_sessions ORDER BY CASE WHEN status = 'IN_PROGRESS' THEN 0 ELSE 1 END, id DESC LIMIT 1")
    suspend fun recoverableId(): Long?
    @Transaction
    @Query("SELECT * FROM flashcard_sessions WHERE id = :id")
    suspend fun graph(id: Long): FlashcardSessionGraph?
    @Transaction
    @Query("SELECT * FROM flashcard_sessions WHERE id = :id")
    fun observe(id: Long): Flow<FlashcardSessionGraph?>
    @Query("SELECT * FROM flashcard_sessions WHERE id = :id")
    suspend fun session(id: Long): FlashcardSessionEntity?
    @Query("SELECT * FROM flashcard_cards WHERE id = :cardId AND session_id = :sessionId")
    suspend fun card(sessionId: Long, cardId: Long): FlashcardCardEntity?
    @Query("SELECT * FROM flashcard_attempts WHERE card_id = :cardId")
    suspend fun attempt(cardId: Long): FlashcardAttemptEntity?
    @Query("SELECT * FROM recognition_progress WHERE word_id = :wordId")
    suspend fun progress(wordId: Long): RecognitionProgressEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM flashcard_cards WHERE word_id = :wordId) OR EXISTS(SELECT 1 FROM recognition_progress WHERE word_id = :wordId)")
    suspend fun wordInUse(wordId: Long): Boolean
    @Insert suspend fun insertSession(session: FlashcardSessionEntity): Long
    @Insert suspend fun insertCard(card: FlashcardCardEntity): Long
    @Insert suspend fun insertAttempt(attempt: FlashcardAttemptEntity): Long
    @Update suspend fun updateSession(session: FlashcardSessionEntity)
    @Update suspend fun updateCard(card: FlashcardCardEntity)
    @Upsert suspend fun saveProgress(progress: RecognitionProgressEntity)
}
