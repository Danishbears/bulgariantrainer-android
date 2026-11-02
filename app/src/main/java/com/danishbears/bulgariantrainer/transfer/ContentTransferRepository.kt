package com.danishbears.bulgariantrainer.transfer

import com.danishbears.bulgariantrainer.data.withContentTransaction as withTransaction
import com.danishbears.bulgariantrainer.data.ImportReceiptEntity
import com.danishbears.bulgariantrainer.data.SentenceAlternativeEntity
import com.danishbears.bulgariantrainer.data.SentenceEntity
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.TranslationEntity
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.data.VocabularyText
import com.danishbears.bulgariantrainer.data.VocabularyValidator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.Writer
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class ContentTransferRepository internal constructor(private val database: TrainerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val words = database.vocabularyDao()
    private val content = database.contentDao()
    private val vocabulary = VocabularyRepository(database, clock)
    fun observeSentenceCount(): Flow<Long> = content.observeSentenceCount()

    suspend fun preview(kind: ContentKind, filename: String, bytes: ByteArray): ImportPreview = withContext(Dispatchers.Default) {
        refresh(ContentParser.parse(kind, filename, bytes), initial = true)
    }

    /** Recheck all corrected keys, including archive entries. IN batches avoid SQLite parameter limits. */
    suspend fun refresh(preview: ImportPreview, initial: Boolean = false): ImportPreview = database.withTransaction {
        val keys = preview.rows.mapNotNull { it.key() }.distinct()
        val wordMatches = if (preview.kind == ContentKind.WORDS) keys.chunked(900).flatMap { words.forKeys(it) }
            .associateBy { it.word.normalizedText } else emptyMap()
        val sentenceMatches = if (preview.kind == ContentKind.SENTENCES) keys.chunked(900).flatMap { content.sentenceKeys(it) }
            .associateBy { it.key } else emptyMap()
        val first = mutableMapOf<String, Int>()
        preview.copy(rows = preview.rows.map { row ->
            val key = row.key()
            val duplicate = key?.let { first.putIfAbsent(it, row.rowId) }
            val match = wordMatches[key]
            val existing = match?.word?.id ?: sentenceMatches[key]?.id
            val warnings = row.warnings.filter { it != ImportIssue.DUPLICATE_IN_FILE && it != ImportIssue.DUPLICATE_IN_DATABASE }.toMutableList()
            if (duplicate != null) warnings.add(ImportIssue.DUPLICATE_IN_FILE)
            if (existing != null) warnings.add(ImportIssue.DUPLICATE_IN_DATABASE)
            val hasNewWarning = duplicate != null || existing != null
            row.copy(existingId = existing, existingTranslations = match?.translations?.map { it.russianText }.orEmpty(),
                duplicateOf = duplicate, warnings = warnings,
                selected = row.selected && (!initial || !hasNewWarning),
                status = if (row.status == RowStatus.READY && hasNewWarning) RowStatus.WARNING else row.status)
        })
    }

    suspend fun confirm(preview: ImportPreview): ImportSummary {
        val expectedEpoch = database.contentEpoch.get()
        return withContext(Dispatchers.Default) {
        if (preview.rows.size > ContentParser.MAX_ENTRIES || preview.rows.map { it.rowId }.toSet().size != preview.rows.size ||
            preview.rows.none { it.selected && it.action != ImportAction.SKIP }) throw TransferException(TransferError.INVALID_SELECTION)
        for (row in preview.rows.filter { it.selected && it.action != ImportAction.SKIP }) {
            if (!row.selectable || (row.status == RowStatus.INVALID && !row.edited) || row.rowId < 1 || row.issues().isNotEmpty() ||
                (preview.kind == ContentKind.WORDS) != (row.word != null) ||
                (preview.kind == ContentKind.SENTENCES && row.action == ImportAction.APPEND))
                throw TransferException(TransferError.INVALID_SELECTION, row.rowId)
        }
        val fingerprint = fingerprint(preview)
        database.withTransaction {
            check(expectedEpoch == database.contentEpoch.get()) { "Local data was replaced; reopen import" }
            val previous = content.receipt(preview.id)
            if (previous != null) {
                if (previous.fingerprint != fingerprint) throw TransferException(TransferError.ALREADY_CONFIRMED)
                return@withTransaction ImportSummary(previous.created, previous.translationsAdded, previous.skipped)
            }
            var created = 0
            var added = 0
            var skipped = 0
            for (row in preview.rows) {
                if (!row.selected || row.action == ImportAction.SKIP) { skipped++; continue }
                if (preview.kind == ContentKind.WORDS) {
                    val input = requireNotNull(row.word)
                    val valid = VocabularyValidator.normalized(input)
                    if (row.action == ImportAction.APPEND) {
                        val id = row.existingId ?: words.idForKey(valid.key) ?: throw TransferException(TransferError.TARGET_CHANGED, row.rowId)
                        val target = words.get(id) ?: throw TransferException(TransferError.TARGET_CHANGED, row.rowId)
                        if (target.word.normalizedText != valid.key) throw TransferException(TransferError.TARGET_CHANGED, row.rowId)
                        val keys = target.translations.map { it.normalizedText }.toSet()
                        val missing = valid.translations.filter { it.key !in keys }
                        if (target.translations.size + missing.size > 50) throw TransferException(TransferError.INVALID_SELECTION, row.rowId)
                        for (translation in missing) words.insertTranslation(TranslationEntity(wordId = id,
                            russianText = translation.text, normalizedText = translation.key, isPrimary = false))
                        if (missing.isNotEmpty()) words.updateWord(target.word.copy(updatedAt = clock.millis()))
                        added += missing.size
                        if (missing.isEmpty()) skipped++
                    } else if (words.idForKey(valid.key) != null) skipped++
                    else { vocabulary.create(input); created++ }
                } else {
                    val draft = requireNotNull(row.sentence)
                    val key = VocabularyText.key(draft.russian)
                    if (content.sentenceId(key) != null) { skipped++; continue }
                    val now = clock.millis()
                    val id = content.insertSentence(SentenceEntity(sourceId = draft.sourceId, sourceFilename = preview.filename,
                        sourceRow = row.firstLine, russian = VocabularyText.clean(draft.russian), normalizedRussian = key,
                        bulgarian = VocabularyText.clean(draft.bulgarian), explanation = VocabularyText.optional(draft.explanation),
                        difficulty = requireNotNull(draft.difficulty.toIntOrNull()), topic = VocabularyText.optional(draft.topic),
                        createdAt = now, updatedAt = now))
                    draft.alternatives.forEachIndexed { index, text -> content.insertAlternative(SentenceAlternativeEntity(
                        sentenceId = id, position = index, text = VocabularyText.clean(text), key = VocabularyText.key(text))) }
                    created++
                }
            }
            content.insertReceipt(ImportReceiptEntity(preview.id, fingerprint, created, added, skipped, clock.millis()))
            ImportSummary(created, added, skipped)
        }
    }

    }

    /** A consistent, bounded-memory snapshot. Caller owns and closes the Writer. No progress data exported. */
    suspend fun export(kind: ContentKind, includeArchived: Boolean, writer: Writer): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            // The web CSV importer requires difficulty 1–5 even though manual creation permits null.
            // Refuse before writing rather than inventing difficulty or exporting non-importable records.
            if (kind == ContentKind.SENTENCES && content.missingDifficulty(includeArchived) > 0)
                throw TransferException(TransferError.SENTENCE_DIFFICULTY_REQUIRED)
            CsvCodec.write(writer, if (kind == ContentKind.WORDS) ContentParser.wordHeader else ContentParser.sentenceHeader)
            var last = 0L
            var count = 0L
            while (true) {
                if (kind == ContentKind.WORDS) {
                    val batch = words.exportBatch(last, includeArchived)
                    if (batch.isEmpty()) break
                    for (row in batch) for (translation in row.translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id })) {
                        CsvCodec.write(writer, listOf(row.word.bulgarianText, translation.russianText, row.word.partOfSpeech.orEmpty())); count++
                    }
                    last = batch.last().word.id
                } else {
                    val batch = content.sentenceBatch(last, includeArchived)
                    if (batch.isEmpty()) break
                    for (row in batch) {
                        val s = row.sentence
                        CsvCodec.write(writer, listOf(s.sourceId, s.russian, s.bulgarian,
                            JsonArray(row.alternatives.sortedBy { it.position }.map { JsonPrimitive(it.text) }).toString(),
                            s.explanation.orEmpty(), s.difficulty.toString(), s.topic.orEmpty())); count++
                    }
                    last = batch.last().sentence.id
                }
            }
            writer.flush()
            count
        }
    }

    private fun fingerprint(preview: ImportPreview): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun text(value: String) { val encoded = value.toByteArray(Charsets.UTF_8); out.writeInt(encoded.size); out.write(encoded) }
            text(preview.kind.name); text(preview.filename)
            for (row in preview.rows) {
                out.writeInt(row.rowId); out.writeInt(row.firstLine); out.writeBoolean(row.selected); text(row.action.name)
                out.writeLong(row.existingId ?: -1)
                row.word?.let { w ->
                    text(w.bulgarianText); text(w.partOfSpeech.orEmpty()); text(w.grammaticalGender.orEmpty()); text(w.notes.orEmpty())
                    out.writeInt(w.translations.size)
                    w.translations.forEach { text(it.russianText); out.writeBoolean(it.isPrimary) }
                }
                row.sentence?.let { s ->
                    text(s.sourceId); text(s.russian); text(s.bulgarian); text(s.explanation); text(s.difficulty); text(s.topic)
                    out.writeInt(s.alternatives.size); s.alternatives.forEach(::text); text(s.unresolvedAlternatives.orEmpty())
                }
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
