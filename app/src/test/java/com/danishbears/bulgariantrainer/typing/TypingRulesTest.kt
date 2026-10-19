package com.danishbears.bulgariantrainer.typing

import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TypingRulesTest {
    @Test fun fullPlanHasThirtyDistinctWordsAndExactlyFifteenOfEachShuffledDirection() {
        val input = (1L..50L).toList()
        val plan = TypingPlanner(Random(7)).plan(input)
        assertEquals(30, plan.size)
        assertEquals(30, plan.map { it.wordId }.toSet().size)
        assertEquals(15, plan.count { it.direction == TypingDirection.BG_TO_RU })
        assertEquals(15, plan.count { it.direction == TypingDirection.RU_TO_BG })
        assertTrue(plan.take(15).map { it.direction }.toSet().size == 2)
        assertNotEquals(input.take(30), plan.map { it.wordId })
        assertEquals(plan, TypingPlanner(Random(7)).plan(input))
        assertNotEquals(plan, TypingPlanner(Random(8)).plan(input))
    }
    @Test fun everyShortSizeIsBalancedWithoutPaddingOrDuplicateInputIds() {
        for (size in 0..29) {
            val ids = (1L..size.toLong()).toList()
            val plan = TypingPlanner(Random(size)).plan(ids + ids)
            assertEquals(size, plan.size)
            assertEquals(size, plan.map { it.wordId }.toSet().size)
            assertEquals((size + 1) / 2, plan.count { it.direction == TypingDirection.BG_TO_RU })
            assertEquals(size / 2, plan.count { it.direction == TypingDirection.RU_TO_BG })
        }
    }
    @Test fun canonicalUnicodeWhitespaceAndRootCaseAreEquivalent() {
        assertTrue(TypingEvaluator.accepts(" \t МОИ\u0306\u00a0  ДОМ \n", listOf("мой дом")))
        assertTrue(TypingEvaluator.accepts("Е\u0308ЛКА", listOf("ёлка")))
        assertTrue(TypingEvaluator.accepts("И\u0300", listOf("ѝ")))
        for (space in listOf('\u0085', '\u1680', '\u2007', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000')) {
            assertTrue(TypingEvaluator.accepts("${space}МОЙ${space}ДОМ${space}", listOf("мой дом")))
        }
        val original = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals("i", TypingEvaluator.key("I")) }
        finally { Locale.setDefault(original) }
    }
    @Test fun meaningfulSpellingPunctuationAndConfusablesAreNeverFuzzyMatched() {
        for ((wrong, accepted) in listOf("каща" to "къща", "мои" to "мой", "и" to "ѝ", "дoм" to "дом",
            "елка" to "ёлка", "дом!" to "дом", "кьща" to "къща", "д\u200Bом" to "дом")) {
            assertFalse("$wrong / $accepted", TypingEvaluator.accepts(wrong, listOf(accepted)))
        }
        assertFalse(TypingEvaluator.accepts("\u00a0", listOf("")))
    }
    @Test fun acceptsOneWholeAlternativeOnly() {
        assertTrue(TypingEvaluator.accepts("МОЁ ЖИЛИЩЕ", listOf("дом", "моё жилище")))
        assertFalse(TypingEvaluator.accepts("дом; моё жилище", listOf("дом", "моё жилище")))
        assertFalse(TypingEvaluator.accepts("жилище", listOf("моё жилище")))
    }
}
