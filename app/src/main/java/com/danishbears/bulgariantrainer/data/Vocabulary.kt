package com.danishbears.bulgariantrainer.data

import java.time.Instant

data class TranslationInput(val russianText: String, val isPrimary: Boolean = false)

data class WordInput(
    val bulgarianText: String,
    val translations: List<TranslationInput>,
    val partOfSpeech: String? = null,
    val grammaticalGender: String? = null,
    val notes: String? = null,
)

data class VocabularyTranslation(val id: Long, val russianText: String, val isPrimary: Boolean)

data class VocabularyWord(
    val id: Long,
    val bulgarianText: String,
    val translations: List<VocabularyTranslation>,
    val partOfSpeech: String?,
    val grammaticalGender: String?,
    val notes: String?,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class VocabularyFilter(
    val query: String? = null,
    val partOfSpeech: String? = null,
    val archived: Boolean = false,
    val page: Int = 0,
    val size: Int = 20,
)

class DuplicateWordException : IllegalArgumentException("A word with this Bulgarian text already exists")

class WordNotFoundException(val wordId: Long) : NoSuchElementException("Word $wordId does not exist")
