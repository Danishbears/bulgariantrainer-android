package com.danishbears.bulgariantrainer.transfer

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.MIGRATION_1_2
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.TranslationInput
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.data.WordInput
import java.io.StringWriter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ContentTransferRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: TrainerDatabase
    private lateinit var transfer: ContentTransferRepository
    private lateinit var vocabulary: VocabularyRepository
    private val filename = "transfer-${UUID.randomUUID()}.db"
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        reopen()
    }
    private fun reopen() {
        db = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).addMigrations(MIGRATION_1_2).build()
        transfer = ContentTransferRepository(db)
        vocabulary = VocabularyRepository(db)
    }
    @After fun teardown() { db.close(); context.deleteDatabase(filename) }
    private suspend fun preview(text: String, kind: ContentKind = ContentKind.WORDS, name: String = "words.txt") = transfer.preview(kind, name, text.toByteArray())
    private fun chooseAll(preview: ImportPreview) = preview.copy(rows = preview.rows.map { row -> row.copy(selected = row.selectable) })
    private fun combine(preview: ImportPreview) = preview.copy(rows = preview.rows.map { row -> row.copy(selected = row.selectable,
        action = if (row.existingId != null || row.duplicateOf != null) ImportAction.APPEND else ImportAction.CREATE) })
    private suspend fun expect(code: TransferError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: TransferException) { assertEquals(code, e.code) }
    }
    private suspend fun export(kind: ContentKind, archived: Boolean = true): String = StringWriter().also { transfer.export(kind, archived, it) }.toString()
    private suspend fun word() = vocabulary.create(WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("жилище")),
        partOfSpeech = "съществително", grammaticalGender = "женский", notes = "Не заменять"))

    @Test fun previewWritesNothingAndDetectsUnicodeDuplicatesInFileAndArchive() = runBlocking<Unit> {
        val stored = vocabulary.setArchived(word().id, true)
        val p = preview(" КЪЩА - здание\nкъща - дом\nкуче - собака")
        assertEquals(stored.id, p.rows[0].existingId)
        assertTrue(ImportIssue.DUPLICATE_IN_DATABASE in p.rows[0].warnings)
        assertEquals(1, p.rows[1].duplicateOf)
        assertFalse(p.rows[0].selected)
        assertFalse(p.rows[1].selected)
        assertTrue(p.rows[2].selected)
        assertEquals(stored, vocabulary.get(stored.id))
        assertEquals(1L, vocabulary.observeCounts().first().total)
        assertEquals(0L, transfer.observeSentenceCount().first())
    }
    @Test fun createSkipsDuplicatesRatherThanOverwritingExistingMetadataOrTranslations() = runBlocking<Unit> {
        val before = word()
        val result = transfer.confirm(chooseAll(preview("КЪЩА - здание\nкуче - собака\nКУЧЕ - пёс")))
        assertEquals(ImportSummary(1, 0, 2), result)
        assertEquals(before, vocabulary.get(before.id))
        assertEquals(listOf("собака"), vocabulary.list().last().translations.map { it.russianText })
    }
    @Test fun appendIsExplicitAndPreservesArchivePrimaryIdsMetadataAndIndependentProgress() = runBlocking<Unit> {
        val before = vocabulary.setArchived(word().id, true)
        db.openHelper.writableDatabase.execSQL("CREATE TABLE future_progress(word_id INTEGER PRIMARY KEY REFERENCES words(id) ON DELETE RESTRICT, attempts INTEGER NOT NULL)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO future_progress VALUES(${before.id}, 17)")
        val p = preview("къща - здание")
        assertFalse(p.rows.single().selected)
        val append = p.copy(rows = p.rows.map { it.copy(selected = true, action = ImportAction.APPEND) })
        assertEquals(ImportSummary(0, 1, 0), transfer.confirm(append))
        val after = vocabulary.get(before.id)
        assertTrue(after.archived)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.notes, after.notes)
        assertEquals(before.grammaticalGender, after.grammaticalGender)
        assertEquals(before.partOfSpeech, after.partOfSpeech)
        assertEquals(before.translations, after.translations.take(2))
        assertFalse(after.translations.last().isPrimary)
        db.openHelper.readableDatabase.query("SELECT attempts FROM future_progress").use { it.moveToFirst(); assertEquals(17, it.getInt(0)) }
    }
    @Test fun explicitSameFileAppendCombinesAllAcceptedTranslationsWithoutSplittingCommaValues() = runBlocking<Unit> {
        val p = preview("bulgarian,russian\nотивам,\"ехать, идти\"\nотивам,отправляться\nотивам,идти\n", name = "words.csv")
        assertEquals(ImportSummary(1, 2, 1), transfer.confirm(combine(p)))
        val saved = vocabulary.list().single()
        assertEquals(listOf("ехать, идти", "отправляться", "идти"), saved.translations.map { it.russianText })
        assertTrue(saved.translations.first().isPrimary)
    }
    @Test fun changedAppendTargetAbortsTheWholeImport() = runBlocking<Unit> {
        val before = word()
        val p = preview("куче - собака\nкъща - здание")
        val request = p.copy(rows = p.rows.map { it.copy(selected = true, action = if (it.existingId != null) ImportAction.APPEND else ImportAction.CREATE) })
        vocabulary.update(before.id, WordInput("дом", listOf(TranslationInput("дом"))))
        expect(TransferError.TARGET_CHANGED) { transfer.confirm(request) }
        assertEquals(listOf("дом"), vocabulary.list().map { it.bulgarianText })
        assertNull(db.contentDao().receipt(p.id))
    }
    @Test fun newlyCreatedDuplicatesAreRecheckedAtConfirmation() = runBlocking<Unit> {
        val p = preview("куче - собака")
        val concurrent = vocabulary.create(WordInput("КУЧЕ", listOf(TranslationInput("пёс"))))
        assertEquals(ImportSummary(0, 0, 1), transfer.confirm(p))
        assertEquals(concurrent, vocabulary.get(concurrent.id))
    }
    @Test fun invalidSelectedRowOrFiftyTranslationLimitCannotCommitPartialContent() = runBlocking<Unit> {
        val p = chooseAll(preview("куче - собака\nбез разделителя"))
        expect(TransferError.INVALID_SELECTION) { transfer.confirm(p) }
        assertTrue(vocabulary.list().isEmpty())
        val stored = vocabulary.create(WordInput("къща", List(50) { TranslationInput("перевод$it") }))
        val append = combine(preview("куче - собака\nкъща - новый перевод"))
        expect(TransferError.INVALID_SELECTION) { transfer.confirm(append) }
        assertEquals(listOf(stored), vocabulary.list())
        assertNull(db.contentDao().receipt(append.id))
    }
    @Test fun sqliteFailureAfterEarlierWritesRollsBackContentAndReceiptTogether() = runBlocking<Unit> {
        vocabulary.observeCounts().first() // Open the real database before installing a fixture-only fault.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_transfer BEFORE INSERT ON translations WHEN NEW.russian_text='ошибка' BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        val p = preview("къща - дом\nкуче - ошибка")
        try { transfer.confirm(p); fail("Expected SQLite failure") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(vocabulary.list().isEmpty())
        assertEquals(0L, vocabulary.observeCounts().first().translations)
        assertNull(db.contentDao().receipt(p.id))
    }
    @Test fun confirmationReceiptsSurviveReopenAndRejectChangedRetries() = runBlocking<Unit> {
        val p = preview("куче - собака")
        val original = transfer.confirm(p)
        db.close(); reopen()
        assertEquals(original, transfer.confirm(p))
        assertEquals(1, vocabulary.list().size)
        val changed = p.copy(rows = p.rows.map { it.copy(word = it.word!!.copy(notes = "новое")) })
        expect(TransferError.ALREADY_CONFIRMED) { transfer.confirm(changed) }
    }
    @Test fun concurrentConfirmationsDoNotDoubleCountOrDuplicateWrites() = runBlocking<Unit> {
        val p = preview("куче - собака")
        val results = List(2) { async(Dispatchers.Default) { transfer.confirm(p) } }.awaitAll()
        assertEquals(listOf(ImportSummary(1, 0, 0), ImportSummary(1, 0, 0)), results)
        assertEquals(1, vocabulary.list().size)
    }
    @Test fun refreshAfterEditingFindsNewDuplicatesWithoutLosingExplicitSelections() = runBlocking<Unit> {
        word()
        val p = combine(preview("къща - здание\nкуче - собака"))
        val edited = p.copy(rows = p.rows.map { if (it.rowId == 2) it.copy(word = it.word!!.copy(bulgarianText = " КЪЩА "), edited = true) else it })
        val refreshed = transfer.refresh(edited)
        assertTrue(refreshed.rows.all { it.selected })
        assertEquals(1, refreshed.rows[1].duplicateOf)
        assertEquals(refreshed.rows[0].existingId, refreshed.rows[1].existingId)
    }
    @Test fun vocabularyExportRoundTripsPrimaryOrderCyrillicCommasQuotesAndEveryTranslation() = runBlocking<Unit> {
        val stored = vocabulary.create(WordInput("отивам", listOf(TranslationInput("ехать, идти"), TranslationInput("говорить \"да\"", true), TranslationInput("отправляться")), partOfSpeech = "глагол"))
        val csv = export(ContentKind.WORDS)
        assertTrue(csv.startsWith("bulgarian,russian,partOfSpeech\r\n"))
        assertTrue(csv.contains("\"говорить \"\"да\"\"\""))
        // Reimport into a second actual Room database, not back into the populated source.
        val second = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java).build()
        try {
            val target = ContentTransferRepository(second)
            target.confirm(combine(target.preview(ContentKind.WORDS, "words.csv", csv.toByteArray())))
            val restored = VocabularyRepository(second).list().single()
            assertEquals(stored.bulgarianText, restored.bulgarianText)
            assertEquals(stored.partOfSpeech, restored.partOfSpeech)
            assertEquals(stored.translations.map { it.russianText }, restored.translations.map { it.russianText })
            assertTrue(restored.translations.first().isPrimary)
        } finally { second.close() }
    }
    @Test fun sentenceDatasetImportsAllFieldsAndRoundTripsThroughStrictWebCsv() = runBlocking<Unit> {
        val bytes = javaClass.classLoader!!.getResourceAsStream("bulgarian_sentences_100.csv")!!.use { it.readBytes() }
        val p = transfer.preview(ContentKind.SENTENCES, "bulgarian_sentences_100.csv", bytes)
        assertEquals(0L, transfer.observeSentenceCount().first())
        assertEquals(ImportSummary(100, 0, 1), transfer.confirm(p))
        val stored = db.contentDao().sentenceBatch(0, true)
        assertEquals(100, stored.size)
        assertTrue(stored.all { it.sentence.approved && !it.sentence.archived })
        assertEquals(20, stored.sumOf { it.alternatives.size })
        assertEquals("bulgarian_sentences_100.csv", stored.first().sentence.sourceFilename)
        assertEquals(2, stored.first().sentence.sourceRow)
        val csv = export(ContentKind.SENTENCES)
        assertTrue(csv.startsWith(ContentParser.sentenceHeader.joinToString(",") + "\r\n"))
        val roundTrip = ContentParser.parse(ContentKind.SENTENCES, "export.csv", csv.toByteArray())
        assertEquals(p.rows.drop(1).map { it.sentence }, roundTrip.rows.drop(1).map { it.sentence })
        val duplicates = transfer.preview(ContentKind.SENTENCES, "export.csv", csv.toByteArray())
        assertTrue(duplicates.rows.drop(1).all { !it.selected && ImportIssue.DUPLICATE_IN_DATABASE in it.warnings })
        assertEquals(ImportSummary(0, 0, 101), transfer.confirm(chooseAll(duplicates)))
        assertEquals(stored, db.contentDao().sentenceBatch(0, true))
    }
    @Test fun invalidSentenceAlternativesBlockAllWritesAndCorrectedSourceIsRetained() = runBlocking<Unit> {
        val header = ContentParser.sentenceHeader.joinToString(",")
        val p = chooseAll(preview("$header\n1,Я здесь.,Тук съм.,[],Пояснение,1,Тема\n2,Я там.,Там съм.,not-json,,2,\n", ContentKind.SENTENCES, "source.csv"))
        expect(TransferError.INVALID_SELECTION) { transfer.confirm(p) }
        assertEquals(0L, transfer.observeSentenceCount().first())
        val fixed = p.copy(rows = p.rows.map { if (it.rowId == 3) it.copy(edited = true, sentence = it.sentence!!.copy(unresolvedAlternatives = null, alternatives = listOf("Аз съм там."))) else it })
        assertEquals(ImportSummary(2, 0, 1), transfer.confirm(fixed))
        assertEquals("not-json", p.rows.last().sentence!!.unresolvedAlternatives)
        assertEquals(3, db.contentDao().sentenceBatch(0, true).last().sentence.sourceRow)
    }
    @Test fun sentenceExportEscapesCsvAndJsonIndependentlyWithoutLosingPunctuation() = runBlocking<Unit> {
        val draft = SentenceDraft("opaque-id", "Мне нужен \"чай\", пожалуйста.", "Искам чай, моля.",
            alternatives = listOf("Искам \"черен чай\", моля.", "Чай / кафе, моля."),
            explanation = "Правило \"цитаты\", пояснение", difficulty = "2", topic = "Кафе, чай")
        val p = ImportPreview(ContentKind.SENTENCES, "edited.csv", listOf(ImportRow(1, 2, 2, "original source",
            RowStatus.READY, emptyList(), sentence = draft, selected = true)))
        transfer.confirm(p)
        val csv = export(ContentKind.SENTENCES)
        val restored = ContentParser.parse(ContentKind.SENTENCES, "export.csv", csv.toByteArray()).rows.last().sentence!!
        assertEquals(draft, restored)
        assertTrue(csv.contains("\"\"чай\"\""))
        assertEquals(listOf("Искам \"черен чай\", моля.", "Чай / кафе, моля."), restored.alternatives)
    }
    @Test fun exportExcludesArchiveUnlessRequestedAndExportsOnlyContentNotProgress() = runBlocking<Unit> {
        vocabulary.setArchived(word().id, true)
        assertEquals("bulgarian,russian,partOfSpeech\r\n", export(ContentKind.WORDS, archived = false))
        val csv = export(ContentKind.WORDS, archived = true)
        assertEquals(3, CsvCodec.read(csv).size)
        assertFalse(csv.contains("Не заменять")) // Notes and archive metadata are outside the web CSV contract.
        assertFalse(csv.contains("progress"))
    }
}
