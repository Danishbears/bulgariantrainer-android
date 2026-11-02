package com.danishbears.bulgariantrainer.transfer

import com.danishbears.bulgariantrainer.data.VocabularyText
import com.danishbears.bulgariantrainer.data.VocabularyValidator
import com.danishbears.bulgariantrainer.data.WordInput
import java.util.UUID

enum class ContentKind { WORDS, SENTENCES }
enum class RowStatus { HEADER, EMPTY, READY, WARNING, INVALID }
enum class ImportAction { CREATE, APPEND, SKIP }
enum class ImportIssue {
    EMPTY_RECORD, NO_SEPARATOR, MISSING_VALUE, VALUE_TOO_LONG, MULTIPLE_SEPARATORS,
    LANGUAGE_REVIEW, ALTERNATIVES, COMMA_OR_LIST, ANNOTATION, MULTILINE_VALUE,
    CONTROL_CHARACTER, CSV_HEADER, UNSUPPORTED_CSV_HEADER, CSV_SYNTAX, CSV_COLUMN_COUNT,
    INVALID_ALTERNATIVES_JSON, INVALID_DIFFICULTY, MISSING_SOURCE_ID, DUPLICATE_ALTERNATIVE,
    DUPLICATE_IN_FILE, DUPLICATE_IN_DATABASE, INVALID_WORD, TARGET_CHANGED, TOO_MANY_TRANSLATIONS,
}
enum class TransferError { INVALID_UTF8, FILE_TOO_LARGE, TOO_MANY_ENTRIES, UNSUPPORTED_FILE, INVALID_HEADER,
    READ_FAILED, WRITE_FAILED, INVALID_SELECTION, TARGET_CHANGED, ALREADY_CONFIRMED, STORAGE_FAILED, SENTENCE_DIFFICULTY_REQUIRED }
class TransferException(val code: TransferError, val rowId: Int? = null) : IllegalArgumentException(code.name)

data class SentenceDraft(
    val sourceId: String = "", val russian: String = "", val bulgarian: String = "",
    val alternatives: List<String> = emptyList(), val explanation: String = "",
    val difficulty: String = "", val topic: String = "", val unresolvedAlternatives: String? = null,
) {
    fun issues(): List<ImportIssue> = buildList {
        if (sourceId.isBlank()) add(ImportIssue.MISSING_SOURCE_ID)
        if (VocabularyText.clean(russian).isEmpty() || VocabularyText.clean(bulgarian).isEmpty()) add(ImportIssue.MISSING_VALUE)
        if (russian.length > 4000 || bulgarian.length > 4000 || explanation.length > 4000 || topic.length > 255 ||
            alternatives.size > 50 || alternatives.any { it.length > 4000 || VocabularyText.clean(it).isEmpty() }) add(ImportIssue.VALUE_TOO_LONG)
        if (difficulty.toIntOrNull() !in 1..5) add(ImportIssue.INVALID_DIFFICULTY)
        if (unresolvedAlternatives != null) add(ImportIssue.INVALID_ALTERNATIVES_JSON)
        val keys = hashSetOf(VocabularyText.key(bulgarian))
        if (alternatives.any { !keys.add(VocabularyText.key(it)) }) add(ImportIssue.DUPLICATE_ALTERNATIVE)
        if ((listOf(russian, bulgarian, explanation, topic, sourceId) + alternatives).any(::containsControl)) add(ImportIssue.CONTROL_CHARACTER)
    }
}

data class ImportRow(
    val rowId: Int, val firstLine: Int, val lastLine: Int, val source: String,
    val status: RowStatus, val warnings: List<ImportIssue>,
    val word: WordInput? = null, val sentence: SentenceDraft? = null,
    val selected: Boolean = false, val action: ImportAction = ImportAction.CREATE,
    val existingId: Long? = null, val existingTranslations: List<String> = emptyList(),
    val duplicateOf: Int? = null, val edited: Boolean = false,
) {
    val selectable get() = status != RowStatus.HEADER && status != RowStatus.EMPTY
    fun key(): String? = (word?.bulgarianText ?: sentence?.russian)?.let(VocabularyText::key)?.takeIf { it.isNotEmpty() }
    fun issues(): List<ImportIssue> = word?.let {
        buildList {
            if (!VocabularyValidator.errors(it).isEmpty) add(ImportIssue.INVALID_WORD)
            if ((listOf(it.bulgarianText, it.partOfSpeech.orEmpty(), it.grammaticalGender.orEmpty(), it.notes.orEmpty()) +
                it.translations.map { t -> t.russianText }).any(::containsControl)) add(ImportIssue.CONTROL_CHARACTER)
        }
    } ?: sentence?.issues() ?: listOf(ImportIssue.MISSING_VALUE)
}

data class ImportPreview(
    val kind: ContentKind, val filename: String, val rows: List<ImportRow>, val id: String = UUID.randomUUID().toString(),
)
data class ImportSummary(val created: Int, val translationsAdded: Int, val skipped: Int)
internal fun containsControl(text: String): Boolean = text.any { it.isISOControl() && it != '\t' && it != '\n' && it != '\r' }
