package com.danishbears.bulgariantrainer.data

enum class FieldIssue { REQUIRED, TOO_LONG, DUPLICATE, PRIMARY }

data class WordInputErrors(
    val bulgarian: FieldIssue? = null,
    val translations: Map<Int, FieldIssue> = emptyMap(),
    val translationGroup: FieldIssue? = null,
    val part: FieldIssue? = null,
    val gender: FieldIssue? = null,
    val notes: FieldIssue? = null,
) {
    val isEmpty: Boolean get() = bulgarian == null && translations.isEmpty() && translationGroup == null &&
        part == null && gender == null && notes == null
}

class InvalidVocabularyException(val errors: WordInputErrors) : IllegalArgumentException("Invalid vocabulary fields")

/** One validation contract shared by repository writes and the editor's field errors. */
object VocabularyValidator {
    fun errors(input: WordInput): WordInputErrors {
        fun required(value: String): FieldIssue? = when {
            value.length > 255 -> FieldIssue.TOO_LONG
            VocabularyText.clean(value).isEmpty() -> FieldIssue.REQUIRED
            else -> null
        }
        val keys = HashSet<String>()
        val translations = buildMap {
            input.translations.forEachIndexed { index, value ->
                val issue = required(value.russianText)
                if (issue != null) put(index, issue)
                else if (!keys.add(VocabularyText.key(value.russianText))) put(index, FieldIssue.DUPLICATE)
            }
        }
        return WordInputErrors(
            bulgarian = required(input.bulgarianText), translations = translations,
            translationGroup = when {
                input.translations.isEmpty() -> FieldIssue.REQUIRED
                input.translations.size > 50 -> FieldIssue.TOO_LONG
                input.translations.count { it.isPrimary } > 1 -> FieldIssue.PRIMARY
                else -> null
            },
            part = if ((input.partOfSpeech?.length ?: 0) > 100) FieldIssue.TOO_LONG else null,
            gender = if ((input.grammaticalGender?.length ?: 0) > 100) FieldIssue.TOO_LONG else null,
            notes = if ((input.notes?.length ?: 0) > 4000) FieldIssue.TOO_LONG else null,
        )
    }

    internal fun normalized(input: WordInput): ValidatedWord {
        val errors = errors(input)
        if (!errors.isEmpty) throw InvalidVocabularyException(errors)
        val primarySpecified = input.translations.any { it.isPrimary }
        val text = VocabularyText.clean(input.bulgarianText)
        return ValidatedWord(
            text, VocabularyText.key(text), VocabularyText.optional(input.partOfSpeech),
            VocabularyText.optional(input.grammaticalGender), VocabularyText.optional(input.notes),
            input.translations.mapIndexed { index, value ->
                val russian = VocabularyText.clean(value.russianText)
                ValidatedTranslation(russian, VocabularyText.key(russian), value.isPrimary || (!primarySpecified && index == 0))
            },
        )
    }
}

internal data class ValidatedTranslation(val text: String, val key: String, val primary: Boolean)
internal data class ValidatedWord(
    val text: String, val key: String, val part: String?, val gender: String?, val notes: String?,
    val translations: List<ValidatedTranslation>,
)
