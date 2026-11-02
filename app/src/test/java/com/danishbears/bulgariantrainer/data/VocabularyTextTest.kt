package com.danishbears.bulgariantrainer.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabularyTextTest {
    @Test fun canonicalCyrillicAndUnicodeWhitespaceMatchTheReference() {
        assertEquals("мой дом", VocabularyText.key(" \t МОИ\u0306\u00A0  ДОМ \n"))
        assertEquals("ёлка", VocabularyText.key("Е\u0308ЛКА"))
        assertEquals("ѝ", VocabularyText.key("И\u0300"))
        val separators = "\t\n\u000B\u000C\r \u0085\u00A0\u1680\u2000\u2001" +
            "\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000"
        assertEquals("мой дом", VocabularyText.key("$separators МОЙ${separators}ДОМ$separators"))
    }

    @Test fun spellingPunctuationAndAlphabetsStayDistinct() {
        listOf(
            "къща" to "каща", "мой" to "мои", "ѝ" to "и", "ёлка" to "елка",
            "дом" to "дoм", "дом." to "дом", "за́вчера" to "завчера",
        ).forEach { (first, second) -> assertNotEquals(VocabularyText.key(first), VocabularyText.key(second)) }
        assertEquals("ехать, идти/ходить", VocabularyText.key("Ехать, идти/ходить"))
    }

    @Test fun keysAreIndependentOfTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("i", VocabularyText.key("I"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun displayTextKeepsCaseAndOptionalBlanksBecomeNull() {
        assertEquals("Мой дом", VocabularyText.clean("  Мой\u00A0дом  "))
        assertNull(VocabularyText.optional(null))
        assertNull(VocabularyText.optional("\u2007 \n"))
        assertEquals("%!!!%!_%", VocabularyText.searchPattern("!%_"))
    }

    @Test fun zeroWidthAndCombiningCharactersAreNotDiscarded() {
        assertEquals("до\u200Bм", VocabularyText.key("до\u200Bм"))
        assertEquals("\uFEFFдом", VocabularyText.key("\uFEFFдом"))
        assertEquals("\u0000дом\u0000", VocabularyText.key("\u0000ДОМ\u0000"))
        assertEquals("дом", VocabularyText.key("\u001CДОМ\u001F"))
    }
}
