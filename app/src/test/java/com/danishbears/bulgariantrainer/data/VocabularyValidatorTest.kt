package com.danishbears.bulgariantrainer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyValidatorTest {
    private fun input(bg: String = "къща", vararg ru: String = arrayOf("дом")) =
        WordInput(bg, ru.map { TranslationInput(it) })

    @Test fun fieldErrorsIdentifyRawLimitsBeforeWhitespaceCleaning() {
        val errors = VocabularyValidator.errors(input(" ".repeat(255) + "а", " ").copy(
            partOfSpeech = " ".repeat(101), grammaticalGender = "а".repeat(101), notes = "а".repeat(4001)))
        assertEquals(FieldIssue.TOO_LONG, errors.bulgarian)
        assertEquals(FieldIssue.REQUIRED, errors.translations[0])
        assertEquals(FieldIssue.TOO_LONG, errors.part)
        assertEquals(FieldIssue.TOO_LONG, errors.gender)
        assertEquals(FieldIssue.TOO_LONG, errors.notes)
    }

    @Test fun alternativeErrorsUseNfcWhitespaceAndCaseWithoutFuzzyMatching() {
        val errors = VocabularyValidator.errors(input("къща", "МОИ\u0306 ДОМ", " мой\u00A0  дом ", "дoм", "дом"))
        assertEquals(mapOf(1 to FieldIssue.DUPLICATE), errors.translations)
        assertNull(errors.bulgarian)
        assertFalse(errors.isEmpty)
    }

    @Test fun noExplicitPrimarySelectsFirstAndOptionalBlankBecomesNull() {
        val normalized = VocabularyValidator.normalized(input(" къща ", "дом", "жилище").copy(partOfSpeech = "\u00A0"))
        assertTrue(normalized.translations.first().primary)
        assertFalse(normalized.translations.last().primary)
        assertNull(normalized.part)
        assertEquals("къща", normalized.text)
    }

    @Test fun translationGroupExplainsEmptyOverflowAndMultiplePrimaries() {
        assertEquals(FieldIssue.REQUIRED, VocabularyValidator.errors(WordInput("а", emptyList())).translationGroup)
        assertEquals(FieldIssue.TOO_LONG, VocabularyValidator.errors(WordInput("а", List(51) { TranslationInput("$it") })).translationGroup)
        assertEquals(FieldIssue.PRIMARY, VocabularyValidator.errors(WordInput("а",
            listOf(TranslationInput("один", true), TranslationInput("два", true)))).translationGroup)
    }
}
