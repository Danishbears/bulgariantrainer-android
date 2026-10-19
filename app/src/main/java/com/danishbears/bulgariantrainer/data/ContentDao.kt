package com.danishbears.bulgariantrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal data class SentenceKey(val id: Long, @androidx.room.ColumnInfo(name = "normalized_russian") val key: String)

@Dao
internal interface ContentDao {
    @Query("SELECT id, normalized_russian FROM sentences WHERE normalized_russian IN (:keys)")
    suspend fun sentenceKeys(keys: List<String>): List<SentenceKey>
    @Query("SELECT id FROM sentences WHERE normalized_russian = :key")
    suspend fun sentenceId(key: String): Long?
    @Query("SELECT COUNT(*) FROM sentences")
    fun observeSentenceCount(): Flow<Long>
    @Insert suspend fun insertSentence(sentence: SentenceEntity): Long
    @Insert suspend fun insertAlternative(alternative: SentenceAlternativeEntity): Long
    @Transaction
    @Query("SELECT * FROM sentences WHERE (:includeArchived OR archived = 0) AND id > :after ORDER BY id LIMIT 100")
    suspend fun sentenceBatch(after: Long, includeArchived: Boolean): List<SentenceWithAlternatives>
    @Query("SELECT * FROM import_receipts WHERE id = :id")
    suspend fun receipt(id: String): ImportReceiptEntity?
    @Insert suspend fun insertReceipt(receipt: ImportReceiptEntity)
}
