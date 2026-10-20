package com.danishbears.bulgariantrainer.transfer

import com.danishbears.bulgariantrainer.data.TranslationInput
import com.danishbears.bulgariantrainer.data.VocabularyText
import com.danishbears.bulgariantrainer.data.WordInput
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object ContentParser {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_ENTRIES = 2000
    val sentenceHeader = listOf("id", "russian", "bulgarian", "alternativeTranslations", "grammaticalExplanation", "difficulty", "topic")
    val wordHeader = listOf("bulgarian", "russian", "partOfSpeech")
    private val pair = Regex("=|(?<=[\\p{Z}\\t\\n\\r\\u000B\\u000C\\u0085])[-–—]|[-–—](?=[\\p{Z}\\t\\n\\r\\u000B\\u000C\\u0085])")
    private val tabs = Regex("\\h*\\t\\h*")
    private val cyrillic = Regex("\\p{IsCyrillic}")
    private val latin = Regex("\\p{IsLatin}")

    fun parse(kind: ContentKind, filename: String, bytes: ByteArray): ImportPreview {
        if (bytes.size > MAX_BYTES) throw TransferException(TransferError.FILE_TOO_LARGE)
        val name = filename.replace('\\', '/').substringAfterLast('/')
        val lower = name.lowercase(Locale.ROOT)
        if (!(lower.endsWith(".csv") || (kind == ContentKind.WORDS && lower.endsWith(".txt"))))
            throw TransferException(TransferError.UNSUPPORTED_FILE)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) { throw TransferException(TransferError.INVALID_UTF8) }
        val rows = if (kind == ContentKind.SENTENCES) sentences(text) else if (lower.endsWith(".csv")) wordsCsv(text) else wordsTxt(text)
        return ImportPreview(kind, name, rows)
    }

    private fun wordsTxt(text: String): List<ImportRow> {
        val lines = text.split(Regex("\r\n|\n|\r")).toMutableList()
        if (lines.size > 1 && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        if (lines.size > MAX_ENTRIES) throw TransferException(TransferError.TOO_MANY_ENTRIES)
        return lines.mapIndexed { index, source ->
            val record = CsvCodec.Record(index + 1, index + 1, source, emptyList(), false)
            if (source.isBlank()) empty(index + 1, record)
            else {
                val pairs = pair.findAll(source).iterator()
                val matches = if (pairs.hasNext()) pairs else tabs.findAll(source).iterator()
                val match = if (matches.hasNext()) matches.next() else null
                if (match == null) invalidWord(index + 1, record, ImportIssue.NO_SEPARATOR)
                else word(index + 1, record, source.substring(0, match.range.first), source.substring(match.range.last + 1), null,
                    if (matches.hasNext()) listOf(ImportIssue.MULTIPLE_SEPARATORS) else emptyList())
            }
        }
    }
    private fun wordsCsv(text: String): List<ImportRow> {
        var columns: Map<String, Int>? = null
        var seenHeader = false
        return CsvCodec.read(text).mapIndexed { index, record ->
            val id = index + 1
            if (record.source.isBlank()) empty(id, record)
            else if (!seenHeader) {
                seenHeader = true
                val names = record.fields.map(VocabularyText::key)
                if (!record.invalid && names.size in 2..3 && names.toSet().size == names.size &&
                    names.containsAll(listOf("bulgarian", "russian")) && names.all { it in setOf("bulgarian", "russian", "partofspeech") }) {
                    columns = names.withIndex().associate { it.value to it.index }
                    ImportRow(id, record.first, record.last, record.source, RowStatus.HEADER, listOf(ImportIssue.CSV_HEADER))
                } else invalidWord(id, record, ImportIssue.UNSUPPORTED_CSV_HEADER)
            } else {
                val cols = columns
                when {
                    cols == null -> invalidWord(id, record, ImportIssue.UNSUPPORTED_CSV_HEADER)
                    record.invalid -> invalidWord(id, record, ImportIssue.CSV_SYNTAX)
                    record.fields.size != cols.size -> invalidWord(id, record, ImportIssue.CSV_COLUMN_COUNT)
                    else -> word(id, record, record.fields[cols.getValue("bulgarian")], record.fields[cols.getValue("russian")],
                        cols["partofspeech"]?.let { record.fields[it] }, if (record.first != record.last) listOf(ImportIssue.MULTILINE_VALUE) else emptyList())
                }
            }
        }
    }
    private fun word(id: Int, record: CsvCodec.Record, bg: String, ru: String, part: String?, initial: List<ImportIssue>): ImportRow {
        val value = WordInput(VocabularyText.clean(bg), listOf(TranslationInput(VocabularyText.clean(ru), true)), VocabularyText.optional(part))
        val warnings = initial.toMutableList()
        var invalid = false
        if (value.bulgarianText.isEmpty() || value.translations.single().russianText.isEmpty()) { warnings.add(ImportIssue.MISSING_VALUE); invalid = true }
        if (value.bulgarianText.length > 255 || value.translations.single().russianText.length > 255 || (value.partOfSpeech?.length ?: 0) > 100) {
            warnings.add(ImportIssue.VALUE_TOO_LONG); invalid = true
        }
        if (containsControl(record.source)) { warnings.add(ImportIssue.CONTROL_CHARACTER); invalid = true }
        val texts = listOf(value.bulgarianText, value.translations.single().russianText)
        if (texts.all { it.isNotEmpty() }) {
            if (texts.any { !cyrillic.containsMatchIn(it) || latin.containsMatchIn(it) }) warnings.add(ImportIssue.LANGUAGE_REVIEW)
            if (texts.any { '/' in it || '\\' in it }) warnings.add(ImportIssue.ALTERNATIVES)
            if (texts.any { ',' in it } || texts.last().endsWith(';')) warnings.add(ImportIssue.COMMA_OR_LIST)
            if (record.source.contains('(') || record.source.contains(')') || record.source.contains("!!")) warnings.add(ImportIssue.ANNOTATION)
        }
        val status = if (invalid) RowStatus.INVALID else if (warnings.isEmpty()) RowStatus.READY else RowStatus.WARNING
        return ImportRow(id, record.first, record.last, record.source, status, warnings, word = value, selected = status == RowStatus.READY)
    }
    private fun invalidWord(id: Int, record: CsvCodec.Record, issue: ImportIssue) = ImportRow(id, record.first, record.last,
        record.source, RowStatus.INVALID, listOf(issue), word = WordInput("", listOf(TranslationInput("", true))))
    private fun empty(id: Int, record: CsvCodec.Record) = ImportRow(id, record.first, record.last, record.source,
        RowStatus.EMPTY, listOf(ImportIssue.EMPTY_RECORD))

    private fun sentences(text: String): List<ImportRow> {
        val records = CsvCodec.read(text)
        if (records.first().invalid || records.first().fields != sentenceHeader) throw TransferException(TransferError.INVALID_HEADER)
        return records.mapIndexed { index, record ->
            val id = index + 1
            if (index == 0) ImportRow(id, record.first, record.last, record.source, RowStatus.HEADER, listOf(ImportIssue.CSV_HEADER))
            else if (record.source.isBlank()) empty(id, record)
            else {
                val fields = record.fields
                fun field(i: Int) = fields.getOrNull(i).orEmpty()
                var unresolved: String? = null
                val alternatives = try { parseAlternatives(field(3)) } catch (_: IllegalArgumentException) { unresolved = field(3); emptyList() }
                val draft = SentenceDraft(field(0), field(1), field(2), alternatives, field(4), field(5), field(6), unresolved)
                val warnings = buildList {
                    if (record.invalid) add(ImportIssue.CSV_SYNTAX)
                    if (fields.size != 7) add(ImportIssue.CSV_COLUMN_COUNT)
                    addAll(draft.issues())
                }.distinct()
                ImportRow(id, record.first, record.last, record.source, if (warnings.isEmpty()) RowStatus.READY else RowStatus.INVALID,
                    warnings, sentence = draft, selected = warnings.isEmpty())
            }
        }
    }
    internal fun parseAlternatives(text: String): List<String> {
        val array = Json.parseToJsonElement(text) as? JsonArray ?: throw IllegalArgumentException("Expected JSON array")
        return array.map { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: throw IllegalArgumentException("Expected string") }
    }
}
