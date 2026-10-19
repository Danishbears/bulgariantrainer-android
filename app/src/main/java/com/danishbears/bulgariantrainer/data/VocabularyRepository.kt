package com.danishbears.bulgariantrainer.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** All vocabulary writes are atomic and all database access is asynchronous. */
class VocabularyRepository internal constructor(
    private val database: TrainerDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.vocabularyDao()

    suspend fun create(input: WordInput): VocabularyWord {
        val valid = validate(input)
        return database.withTransaction {
            ensureUnique(valid.key)
            val now = clock.millis()
            val id = dao.insertWord(WordEntity(
                bulgarianText = valid.text,
                normalizedText = valid.key,
                partOfSpeech = valid.part,
                normalizedPartOfSpeech = valid.part?.let(VocabularyText::key),
                grammaticalGender = valid.gender,
                notes = valid.notes,
                createdAt = now,
                updatedAt = now,
            ))
            valid.translations.forEach { value ->
                dao.insertTranslation(TranslationEntity(
                    wordId = id, russianText = value.text,
                    normalizedText = value.key, isPrimary = value.primary,
                ))
            }
            get(id)
        }
    }

    suspend fun update(id: Long, input: WordInput): VocabularyWord {
        val valid = validate(input)
        return database.withTransaction {
            val previous = dao.get(id) ?: throw WordNotFoundException(id)
            ensureUnique(valid.key, id)
            dao.updateWord(previous.word.copy(
                bulgarianText = valid.text,
                normalizedText = valid.key,
                partOfSpeech = valid.part,
                normalizedPartOfSpeech = valid.part?.let(VocabularyText::key),
                grammaticalGender = valid.gender,
                notes = valid.notes,
                updatedAt = clock.millis(),
            ))
            dao.removeTranslationsExcept(id, valid.translations.map { it.key })
            val existing = previous.translations.associateBy { it.normalizedText }
            valid.translations.forEach { value ->
                val old = existing[value.key]
                if (old == null) {
                    dao.insertTranslation(TranslationEntity(
                        wordId = id, russianText = value.text,
                        normalizedText = value.key, isPrimary = value.primary,
                    ))
                } else {
                    dao.updateTranslation(old.copy(russianText = value.text, isPrimary = value.primary))
                }
            }
            get(id)
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean): VocabularyWord = database.withTransaction {
        val previous = dao.get(id) ?: throw WordNotFoundException(id)
        dao.updateWord(previous.word.copy(archived = archived, updatedAt = clock.millis()))
        get(id)
    }

    suspend fun get(id: Long): VocabularyWord = (dao.get(id) ?: throw WordNotFoundException(id)).toWord()

    suspend fun list(filter: VocabularyFilter = VocabularyFilter()): List<VocabularyWord> {
        val params = parameters(filter)
        return dao.list(filter.archived, params.part, params.pattern, filter.size, params.offset).map { it.toWord() }
    }

    fun observe(filter: VocabularyFilter = VocabularyFilter()): Flow<List<VocabularyWord>> {
        val params = parameters(filter)
        return dao.observe(filter.archived, params.part, params.pattern, filter.size, params.offset)
            .map { rows -> rows.map { it.toWord() } }
    }

    /** Only translations currently depend on words. Future history tables must use restrictive FKs. */
    suspend fun delete(id: Long) = database.withTransaction {
        if (dao.deleteWord(id) == 0) throw WordNotFoundException(id)
    }

    private suspend fun ensureUnique(key: String, exceptId: Long? = null) {
        val existing = dao.idForKey(key)
        if (existing != null && existing != exceptId) throw DuplicateWordException()
    }

    private fun validate(input: WordInput): ValidatedWord {
        val text = requiredText(input.bulgarianText, 255)
        require(input.translations.size in 1..50) { "A word needs between 1 and 50 translations" }
        val primaryCount = input.translations.count { it.isPrimary }
        require(primaryCount <= 1) { "Only one translation may be primary" }
        val keys = HashSet<String>()
        val translations = input.translations.mapIndexed { index, value ->
            val translation = requiredText(value.russianText, 255)
            val key = VocabularyText.key(translation)
            require(keys.add(key)) { "Duplicate Russian translations are not allowed" }
            ValidatedTranslation(translation, key, value.isPrimary || (primaryCount == 0 && index == 0))
        }
        return ValidatedWord(
            text, VocabularyText.key(text), optionalText(input.partOfSpeech, 100),
            optionalText(input.grammaticalGender, 100), optionalText(input.notes, 4000), translations,
        )
    }

    private fun requiredText(value: String, max: Int): String {
        require(value.length <= max) { "Vocabulary text is too long" }
        return VocabularyText.clean(value).also { require(it.isNotEmpty()) { "Vocabulary text must not be blank" } }
    }

    private fun optionalText(value: String?, max: Int): String? {
        require(value == null || value.length <= max) { "Optional text is too long" }
        return VocabularyText.optional(value)
    }

    private fun parameters(filter: VocabularyFilter): FilterParameters {
        val offset = filter.page.toLong() * filter.size
        require(filter.page >= 0 && filter.size in 1..100 && offset <= Int.MAX_VALUE) { "Invalid page or size" }
        require(filter.query == null || filter.query.length <= 255) { "Search query is too long" }
        require(filter.query == null || VocabularyText.clean(filter.query).isNotEmpty()) { "Search query must not be blank" }
        return FilterParameters(
            optionalText(filter.partOfSpeech, 100)?.let(VocabularyText::key),
            filter.query?.let(VocabularyText::searchPattern), offset.toInt(),
        )
    }

    private fun WordWithTranslations.toWord() = VocabularyWord(
        word.id, word.bulgarianText,
        translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id })
            .map { VocabularyTranslation(it.id, it.russianText, it.isPrimary) },
        word.partOfSpeech, word.grammaticalGender, word.notes, word.archived,
        Instant.ofEpochMilli(word.createdAt), Instant.ofEpochMilli(word.updatedAt),
    )

    private data class ValidatedTranslation(val text: String, val key: String, val primary: Boolean)
    private data class ValidatedWord(
        val text: String, val key: String, val part: String?, val gender: String?, val notes: String?,
        val translations: List<ValidatedTranslation>,
    )
    private data class FilterParameters(val part: String?, val pattern: String?, val offset: Int)
}
