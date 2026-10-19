package com.danishbears.bulgariantrainer.transfer

import java.io.Writer

/** Strict comma CSV state machine ported from the pinned web readCsv implementation.
 * Retains malformed records and exact physical source ranges rather than splitting on commas/newlines.
 */
internal object CsvCodec {
    data class Record(val first: Int, val last: Int, val source: String, val fields: List<String>, val invalid: Boolean)
    fun read(text: String): List<Record> {
        val records = mutableListOf<Record>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var state = 0 // field start, unquoted, quoted, after closing quote
        var start = 0
        var line = 1
        var first = 1
        var invalid = false
        var index = 0
        fun finish(end: Int, unclosed: Boolean = false) {
            fields.add(field.toString())
            records.add(Record(first, line, text.substring(start, end), fields.toList(), invalid || unclosed))
            if (records.size > ContentParser.MAX_ENTRIES) throw TransferException(TransferError.TOO_MANY_ENTRIES)
            fields.clear(); field.setLength(0)
        }
        while (index < text.length) {
            val c = text[index]
            val newline = c == '\r' || c == '\n'
            val length = if (c == '\r' && text.getOrNull(index + 1) == '\n') 2 else 1
            if (state == 2) {
                when {
                    c == '"' && text.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                    c == '"' -> state = 3
                    newline -> { field.append(text, index, index + length); line++; index += length - 1 }
                    else -> field.append(c)
                }
            } else when {
                newline -> {
                    finish(index)
                    index += length - 1; line++; first = line; start = index + 1; state = 0; invalid = false
                }
                c == ',' -> { fields.add(field.toString()); field.setLength(0); state = 0 }
                c == '"' && state == 0 -> state = 2
                state == 3 -> { if (c != ' ' && c != '\t') invalid = true }
                else -> { if (c == '"') invalid = true; field.append(c); state = 1 }
            }
            index++
        }
        if (start < text.length || records.isEmpty()) finish(text.length, state == 2)
        return records
    }
    fun write(writer: Writer, fields: List<String>) {
        writer.write(fields.joinToString(",") { value ->
            if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
        })
        writer.write("\r\n")
    }
}
