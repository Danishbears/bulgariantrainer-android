package com.danishbears.bulgariantrainer.sentences

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.danishbears.bulgariantrainer.data.withContentTransaction as withTransaction
import com.danishbears.bulgariantrainer.data.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SentenceRepository internal constructor(private val database: TrainerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.sentenceDao()
    fun pages(archived: Boolean): Flow<PagingData<SentenceRecord>> = Pager(PagingConfig(pageSize = 30,
        initialLoadSize = 30, prefetchDistance = 8, maxSize = 150, enablePlaceholders = false)) { dao.paging(archived) }
        .flow.map { it.map { row -> row.toRecord() } }
    fun observeAvailable(): Flow<Long> = dao.observeAvailable()
    suspend fun get(id: Long): SentenceRecord = (dao.get(id) ?: fail(SentenceError.SENTENCE_NOT_FOUND)).toRecord()
    suspend fun create(input: SentenceInput): SentenceRecord {
        val valid = SentenceValidation.normalize(input)
        return database.withTransaction {
            if (dao.forKey(VocabularyText.key(valid.russian)) != null) fail(SentenceError.DUPLICATE_SENTENCE)
            val now = clock.millis()
            val id = dao.insert(SentenceEntity(sourceId = "manual-${UUID.randomUUID()}", sourceFilename = "manual", sourceRow = 0,
                russian = valid.russian, normalizedRussian = VocabularyText.key(valid.russian), bulgarian = valid.bulgarian,
                explanation = valid.explanation, difficulty = valid.difficulty, topic = valid.topic, createdAt = now, updatedAt = now))
            replaceAlternatives(id, valid.alternatives, emptyList())
            get(id)
        }
    }
    suspend fun update(id: Long, input: SentenceInput): SentenceRecord {
        val valid = SentenceValidation.normalize(input)
        return database.withTransaction {
            val previous = dao.get(id) ?: fail(SentenceError.SENTENCE_NOT_FOUND)
            val key = VocabularyText.key(valid.russian)
            if (dao.forKey(key)?.let { it != id } == true) fail(SentenceError.DUPLICATE_SENTENCE)
            dao.update(previous.sentence.copy(russian = valid.russian, normalizedRussian = key, bulgarian = valid.bulgarian,
                explanation = valid.explanation, difficulty = valid.difficulty, topic = valid.topic, approved = true, updatedAt = clock.millis()))
            replaceAlternatives(id, valid.alternatives, previous.alternatives)
            get(id)
        }
    }
    suspend fun setArchived(id: Long, archived: Boolean): SentenceRecord = database.withTransaction {
        val previous = dao.get(id) ?: fail(SentenceError.SENTENCE_NOT_FOUND)
        dao.update(previous.sentence.copy(archived = archived, updatedAt = clock.millis())); get(id)
    }
    private suspend fun replaceAlternatives(id: Long, values: List<String>, previous: List<SentenceAlternativeEntity>) {
        val keys = values.map(VocabularyText::key)
        dao.removeAlternativesExcept(id, keys)
        val old = previous.associateBy { it.key }
        values.forEachIndexed { index, text ->
            val existing = old[keys[index]]
            if (existing == null) dao.insertAlternative(SentenceAlternativeEntity(sentenceId = id, position = index, text = text, key = keys[index]))
            else dao.updateAlternative(existing.copy(position = index, text = text))
        }
    }
    private fun fail(code: SentenceError): Nothing = throw SentenceException(code)
}
internal fun SentenceWithAlternatives.toRecord() = SentenceRecord(sentence.id,
    SentenceInput(sentence.russian, sentence.bulgarian, alternatives.sortedBy { it.position }.map { it.text },
        sentence.explanation, sentence.difficulty, sentence.topic), sentence.archived, sentence.approved,
    sentence.sourceId, sentence.sourceFilename, sentence.sourceRow)
