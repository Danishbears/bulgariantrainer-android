package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val FILTER_WORDS = """
    SELECT * FROM words w
    WHERE w.archived = :archived
      AND (:part IS NULL OR w.normalized_part_of_speech = :part)
      AND (:pattern IS NULL OR w.normalized_text LIKE :pattern ESCAPE '!'
        OR EXISTS (SELECT 1 FROM translations t WHERE t.word_id = w.id
                   AND t.normalized_text LIKE :pattern ESCAPE '!'))
    ORDER BY w.id ASC LIMIT :limit OFFSET :offset
"""

@Dao
internal interface VocabularyDao {
    @Transaction
    @Query(FILTER_WORDS)
    suspend fun list(archived: Boolean, part: String?, pattern: String?, limit: Int, offset: Int): List<WordWithTranslations>

    @Transaction
    @Query(FILTER_WORDS)
    fun observe(archived: Boolean, part: String?, pattern: String?, limit: Int, offset: Int): Flow<List<WordWithTranslations>>

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun get(id: Long): WordWithTranslations?

    @Query("SELECT id FROM words WHERE normalized_text = :key")
    suspend fun idForKey(key: String): Long?

    @Insert
    suspend fun insertWord(word: WordEntity): Long

    @Update
    suspend fun updateWord(word: WordEntity)

    @Insert
    suspend fun insertTranslation(translation: TranslationEntity): Long

    @Update
    suspend fun updateTranslation(translation: TranslationEntity)

    @Query("DELETE FROM translations WHERE word_id = :wordId AND normalized_text NOT IN (:keys)")
    suspend fun removeTranslationsExcept(wordId: Long, keys: List<String>)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteWord(id: Long): Int
}
