package com.danishbears.bulgariantrainer.transfer

import org.junit.Assert.*
import org.junit.Test

class ContentParserTest {
    private fun words(text: String, filename: String = "words.txt") = ContentParser.parse(ContentKind.WORDS, filename, text.toByteArray())
    private fun sentences(text: String) = ContentParser.parse(ContentKind.SENTENCES, "sentences.csv", text.toByteArray())
    private val header = ContentParser.sentenceHeader.joinToString(",")
    private fun expect(code: TransferError, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (error: TransferException) { assertEquals(code, error.code) }
    }
    @Test fun acceptsUtf8WithAndWithoutBomAndRejectsInvalidBytes() {
        assertEquals(words("куче - собака").rows, words("\uFEFFкуче - собака", "WORDS.TXT").rows)
        expect(TransferError.INVALID_UTF8) { ContentParser.parse(ContentKind.WORDS, "a.txt", byteArrayOf(0xd0.toByte(), 0x20)) }
        expect(TransferError.INVALID_UTF8) { ContentParser.parse(ContentKind.SENTENCES, "a.csv", byteArrayOf(0xff.toByte())) }
    }
    @Test fun txtSeparatorsPreserveHyphensPhrasesAndLiteralCommaOrSlashTranslations() {
        listOf(" - ", "=", " – ", " — ", "\t\t", "- ").forEach { separator ->
            val row = words("куче${separator}собака").rows.single()
            assertEquals("куче", row.word!!.bulgarianText)
            assertEquals("собака", row.word.translations.single().russianText)
        }
        val rows = words("горе-долу - более-менее\nна колко години си - сколько тебе лет\nотивам - ехать, идти\nяке/якета - куртка/куртки").rows
        assertEquals("горе-долу", rows[0].word!!.bulgarianText)
        assertEquals("на колко години си", rows[1].word!!.bulgarianText)
        assertEquals("ехать, идти", rows[2].word!!.translations.single().russianText)
        assertTrue(ImportIssue.COMMA_OR_LIST in rows[2].warnings)
        assertTrue(ImportIssue.ALTERNATIVES in rows[3].warnings)
        val original = words("храна - продукты\nимам/няма - иметь(глагол)/неиметь\nмомиче - девочка (едно момиче средний род) ").rows
        assertEquals("продукты", original[0].word!!.translations.single().russianText)
        assertEquals("имам/няма", original[1].word!!.bulgarianText)
        assertEquals("иметь(глагол)/неиметь", original[1].word!!.translations.single().russianText)
        assertTrue(ImportIssue.ALTERNATIVES in original[1].warnings)
        assertTrue(ImportIssue.ANNOTATION in original[2].warnings)
    }
    @Test fun retainsInvalidBlankAndControlRowsWithoutAutoReversingLanguages() {
        val rows = words("hi - здрасти\r\n\r\nзаголовок\r\nкуче - соб\u0000ака\r\n").rows
        assertEquals(4, rows.size)
        assertEquals("hi", rows.first().word!!.bulgarianText)
        assertTrue(ImportIssue.LANGUAGE_REVIEW in rows.first().warnings)
        assertFalse(rows.first().selected)
        assertEquals(RowStatus.EMPTY, rows[1].status)
        assertEquals("заголовок", rows[2].source)
        assertEquals(RowStatus.INVALID, rows[2].status)
        assertTrue(ImportIssue.CONTROL_CHARACTER in rows[3].warnings)
        assertEquals(4, rows[3].firstLine)
        assertEquals(1, words("").rows.size)
    }
    @Test fun csvQuotesCommasAndEscapedQuotesAreParsedAsFields() {
        val rows = words("\uFEFFrussian,bulgarian,partOfSpeech\r\n\"ехать, идти\",отивам,verb\r\n\"слово \"\"цитата\"\"\",дума,noun\r\n", "words.csv").rows
        assertEquals(3, rows.size)
        assertEquals(RowStatus.HEADER, rows[0].status)
        assertEquals("ехать, идти", rows[1].word!!.translations.single().russianText)
        assertEquals("слово \"цитата\"", rows[2].word!!.translations.single().russianText)
        assertEquals("noun", rows[2].word!!.partOfSpeech)
    }
    @Test fun multilineCsvKeepsExactSourceRangeAndRecoversAfterBrokenRecord() {
        val rows = words("bulgarian,russian\n\"добро\r\nутро\",\"доброе утро\"\nкуче,\"собака\"broken\nкотка,кошка\n", "words.csv").rows
        assertEquals(4, rows.size)
        assertEquals(2, rows[1].firstLine)
        assertEquals(3, rows[1].lastLine)
        assertEquals("\"добро\r\nутро\",\"доброе утро\"", rows[1].source)
        assertEquals("добро утро", rows[1].word!!.bulgarianText)
        assertEquals(RowStatus.INVALID, rows[2].status)
        assertEquals(5, rows[3].firstLine)
        assertEquals("котка", rows[3].word!!.bulgarianText)
    }
    @Test fun unclosedCsvRecordIsNotDiscardedAndHeadersAreNeverGuessed() {
        val rows = words("bulgarian,russian\nкуче,\"собака\nкотка,кошка", "words.csv").rows
        assertEquals(2, rows.size)
        assertTrue(ImportIssue.CSV_SYNTAX in rows.last().warnings)
        assertEquals("куче,\"собака\nкотка,кошка", rows.last().source)
        assertTrue(words("куче,собака\nкотка,кошка", "words.csv").rows.all { it.status == RowStatus.INVALID })
        assertTrue(words("bulgarian,russian,notes\nкуче,собака,x", "words.csv").rows.all { it.status == RowStatus.INVALID })
        assertTrue(ImportIssue.CSV_COLUMN_COUNT in words("bulgarian,russian\nкуче,собака,пёс", "words.csv").rows.last().warnings)
    }
    @Test fun sentenceCsvUsesStrictJsonArrayAndRetainsMalformedFieldForManualCorrection() {
        val rows = sentences("$header\n1,Я здесь.,Тук съм.,\"[\"\"Аз съм тук.\"\"]\",Объяснение,1,Тема\n2,Я там.,Там съм.,\"[1]\",,2,\n").rows
        assertEquals(listOf("Аз съм тук."), rows[1].sentence!!.alternatives)
        assertEquals(RowStatus.READY, rows[1].status)
        assertEquals("[1]", rows[2].sentence!!.unresolvedAlternatives)
        assertTrue(ImportIssue.INVALID_ALTERNATIVES_JSON in rows[2].issues())
        listOf("['не JSON']", "{}", "[\"a\",]", "[null]", "[true]").forEach { invalid ->
            try { ContentParser.parseAlternatives(invalid); fail("Accepted $invalid") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun sentencePreviewCatchesDuplicateAlternativesAndDifficultyBeforeConfirmation() {
        val row = sentences("$header\n1,Я здесь.,Тук съм.,\"[\"\" ТУК СЪМ. \"\"]\",,6,\n").rows.last()
        assertEquals(RowStatus.INVALID, row.status)
        assertTrue(ImportIssue.DUPLICATE_ALTERNATIVE in row.warnings)
        assertTrue(ImportIssue.INVALID_DIFFICULTY in row.warnings)
        expect(TransferError.INVALID_HEADER) { sentences(header.replace("russian", "Russian") + "\n") }
    }
    @Test fun enforcesByteAndRecordLimitsIncludingHeadersAndDoesNotAddTerminalEmptyRecord() {
        expect(TransferError.FILE_TOO_LARGE) { ContentParser.parse(ContentKind.WORDS, "a.txt", ByteArray(ContentParser.MAX_BYTES + 1)) }
        expect(TransferError.UNSUPPORTED_FILE) { words("куче - собака", "a.pdf") }
        expect(TransferError.TOO_MANY_ENTRIES) { words("куче - собака\n".repeat(2001)) }
        assertEquals(2000, words("куче - собака\n".repeat(2000)).rows.size)
        assertEquals(2, words("bulgarian,russian\nкуче,собака\n", "words.csv").rows.size)
    }
    @Test fun actualDatasetHasExactSevenColumnHeaderOneHundredRowsAndTwentyAlternatives() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("bulgarian_sentences_100.csv")!!.use { it.readBytes() }
        val rows = ContentParser.parse(ContentKind.SENTENCES, "bulgarian_sentences_100.csv", bytes).rows
        assertEquals(101, rows.size)
        assertEquals(header, rows.first().source)
        assertTrue(rows.drop(1).all { it.status == RowStatus.READY })
        assertEquals(20, rows.sumOf { it.sentence?.alternatives?.size ?: 0 })
        assertEquals(setOf("1", "2"), rows.drop(1).map { it.sentence!!.difficulty }.toSet())
    }
}
