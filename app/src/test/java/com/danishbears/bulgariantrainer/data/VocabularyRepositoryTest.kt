package com.danishbears.bulgariantrainer.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class VocabularyRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: TrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var filename: String
    private val now = Instant.parse("2026-10-19T12:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        filename = "vocabulary-test-${UUID.randomUUID()}.db"
        database = open()
        repository = VocabularyRepository(database, clock)
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(filename)
    }

    private fun open() = Room.databaseBuilder(context, TrainerDatabase::class.java, filename).build()

    private fun word(bg: String = "къща", ru: String = "дом") = WordInput(bg, listOf(TranslationInput(ru)))

    @Test fun newDatabaseIsEmptyAndCreateCleansTextAndChoosesPrimary() = runBlocking<Unit> {
        assertTrue(repository.list().isEmpty())
        val saved = repository.create(word("  КЪЩА\u00A0 ", "  Дом ").copy(
            translations = listOf(TranslationInput("  Дом "), TranslationInput("жилище")),
            partOfSpeech = "  Съществително ", grammaticalGender = " женский ", notes = "\nзаметка\tтест ",
        ))
        assertEquals("КЪЩА", saved.bulgarianText)
        assertEquals(listOf("Дом", "жилище"), saved.translations.map { it.russianText })
        assertEquals(1, saved.translations.count { it.isPrimary })
        assertTrue(saved.translations.first().isPrimary)
        assertEquals("Съществително", saved.partOfSpeech)
        assertEquals("женский", saved.grammaticalGender)
        assertEquals("заметка тест", saved.notes)
        assertEquals(now, saved.createdAt)
        assertEquals(now, saved.updatedAt)
        assertFalse(saved.archived)
        assertEquals(saved, repository.get(saved.id))
    }

    @Test fun explicitPrimaryComesFirstAndCommaTranslationStaysWhole() = runBlocking<Unit> {
        val saved = repository.create(word("отивам").copy(translations = listOf(
            TranslationInput("ехать, идти"), TranslationInput("отправляться", isPrimary = true),
        )))
        assertEquals(listOf("отправляться", "ехать, идти"), saved.translations.map { it.russianText })
        assertEquals(1, saved.translations.count { it.isPrimary })
    }

    @Test fun archivedWordsStillReserveNormalizedBulgarianKeys() = runBlocking<Unit> {
        val saved = repository.create(word("МОИ\u0306 ДОМ"))
        repository.setArchived(saved.id, true)
        expectFailure<DuplicateWordException> { repository.create(word(" мой\u00A0 дом ")) }
        assertTrue(repository.list().isEmpty())
        assertEquals(1, repository.list(VocabularyFilter(archived = true)).size)
        assertEquals(saved.translations, repository.setArchived(saved.id, false).translations)
    }

    @Test fun invalidWordAndTranslationRequestsNeverCreatePartialData() = runBlocking<Unit> {
        val invalid = listOf(
            word("\u00A0"), word("а".repeat(256)), word(ru = "\n"), word(ru = "а".repeat(256)),
            word().copy(translations = emptyList()),
            word().copy(translations = List(51) { TranslationInput("перевод$it") }),
            word().copy(translations = listOf(TranslationInput("ДОМ"), TranslationInput(" дом "))),
            word().copy(translations = listOf(TranslationInput("дом", true), TranslationInput("жилище", true))),
            word().copy(partOfSpeech = "а".repeat(101)), word().copy(grammaticalGender = "а".repeat(101)),
            word().copy(notes = "а".repeat(4001)),
        )
        invalid.forEach { input -> expectFailure<IllegalArgumentException> { repository.create(input) } }
        assertTrue(repository.list().isEmpty())
        assertEquals(0, translationCount())
    }

    @Test fun acceptsBoundaryLengthsAndFiftyDistinctTranslations() = runBlocking<Unit> {
        val saved = repository.create(WordInput(
            "а".repeat(255), List(50) { TranslationInput("а".repeat(250) + it) },
            partOfSpeech = "б".repeat(100), grammaticalGender = "в".repeat(100), notes = "г".repeat(4000),
        ))
        assertEquals(50, saved.translations.size)
        assertEquals(1, saved.translations.count { it.isPrimary })
    }

    @Test fun duplicateOrInvalidEditLeavesBothWordsUnchanged() = runBlocking<Unit> {
        val first = repository.create(word())
        val second = repository.create(word("куче", "собака"))
        expectFailure<DuplicateWordException> { repository.update(second.id, word(" КЪЩА ", "другой перевод")) }
        expectFailure<IllegalArgumentException> { repository.update(first.id, word().copy(translations = emptyList())) }
        assertEquals(first, repository.get(first.id))
        assertEquals(second, repository.get(second.id))
    }

    @Test fun editKeepsSurvivingTranslationIdsCreationTimeAndArchiveState() = runBlocking<Unit> {
        val saved = repository.create(word().copy(translations = listOf(TranslationInput("дом"), TranslationInput("жилище"))))
        repository.setArchived(saved.id, true)
        val later = VocabularyRepository(database, Clock.offset(clock, Duration.ofHours(1)))
        val edited = later.update(saved.id, word("Къща").copy(
            translations = listOf(TranslationInput("ЖИЛИЩЕ", true), TranslationInput("здание")),
        ))
        assertTrue(edited.archived)
        assertEquals(saved.createdAt, edited.createdAt)
        assertEquals(now.plusSeconds(3600), edited.updatedAt)
        assertEquals(saved.translations[1].id, edited.translations[0].id)
        assertEquals(listOf("ЖИЛИЩЕ", "здание"), edited.translations.map { it.russianText })
        assertEquals(2, translationCount())
    }

    @Test fun sqliteFailureRollsBackWordAndTranslationChangesTogether() = runBlocking<Unit> {
        val previous = repository.create(word())
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_test_translation BEFORE INSERT ON translations
            WHEN NEW.russian_text = 'ошибка'
            BEGIN SELECT RAISE(ABORT, 'intentional test constraint'); END
        """.trimIndent())
        expectFailure<SQLiteConstraintException> { repository.create(word("куче", "ошибка")) }
        expectFailure<SQLiteConstraintException> { repository.update(previous.id, word("новое слово", "ошибка")) }
        assertEquals(listOf(previous), repository.list())
        assertEquals(1, translationCount())
    }

    @Test fun bilingualSearchUsesUnicodePartKeysLiteralWildcardsAndPagination() = runBlocking<Unit> {
        val first = repository.create(word().copy(partOfSpeech = "СЪЩЕСТВИТЕЛНО"))
        val second = repository.create(word("куче", "собака").copy(partOfSpeech = "съществително"))
        val literal = repository.create(word("процент %_!", "100%_!"))
        assertEquals(listOf(first), repository.list(VocabularyFilter(query = "ДОМ", partOfSpeech = "съществително")))
        assertEquals(listOf(second), repository.list(VocabularyFilter(query = "КУЧ", partOfSpeech = " СЪЩЕСТВИТЕЛНО ")))
        assertEquals(listOf(literal), repository.list(VocabularyFilter(query = "%_!")))
        assertEquals(listOf(first.id, second.id, literal.id), repository.list().map { it.id })
        assertEquals(listOf(second), repository.list(VocabularyFilter(page = 1, size = 1)))
        repository.setArchived(first.id, true)
        assertEquals(2, repository.list().size)
        assertEquals(listOf(first.id), repository.list(VocabularyFilter(archived = true)).map { it.id })
    }

    @Test fun invalidSearchPaginationAndMissingIdsAreRejected() = runBlocking<Unit> {
        listOf(
            VocabularyFilter(query = "\u00A0"), VocabularyFilter(page = -1), VocabularyFilter(size = 0),
            VocabularyFilter(size = 101), VocabularyFilter(page = Int.MAX_VALUE, size = 100),
            VocabularyFilter(query = "а".repeat(256)), VocabularyFilter(partOfSpeech = "а".repeat(101)),
        ).forEach { filter -> expectFailure<IllegalArgumentException> { repository.list(filter) } }
        expectFailure<WordNotFoundException> { repository.get(999) }
        expectFailure<WordNotFoundException> { repository.update(999, word()) }
        expectFailure<WordNotFoundException> { repository.setArchived(999, true) }
        expectFailure<WordNotFoundException> { repository.delete(999) }
    }

    @Test fun foreignKeysRejectOrphansAndDeleteOnlyCascadesOwnedTranslations() = runBlocking<Unit> {
        val first = repository.create(word())
        val second = repository.create(word("куче", "собака"))
        val stored = database.vocabularyDao().get(first.id)!!
        expectFailure<SQLiteConstraintException> { database.vocabularyDao().insertWord(stored.word.copy(id = 0)) }
        expectFailure<SQLiteConstraintException> {
            database.vocabularyDao().insertTranslation(stored.translations.single().copy(id = 0))
        }
        expectFailure<SQLiteConstraintException> {
            database.vocabularyDao().insertTranslation(TranslationEntity(
                wordId = 999, russianText = "тест", normalizedText = "тест", isPrimary = true,
            ))
        }
        repository.delete(first.id)
        assertEquals(listOf(second), repository.list())
        assertEquals(1, translationCount())
        expectFailure<WordNotFoundException> { repository.get(first.id) }
    }

    @Test fun committedCyrillicDataSurvivesDatabaseCloseAndReopen() = runBlocking<Unit> {
        val saved = repository.create(word().copy(notes = "Данные на устройстве"))
        val archived = repository.setArchived(saved.id, true)
        database.close()
        database = open()
        repository = VocabularyRepository(database, clock)
        assertEquals(archived, repository.get(saved.id))
        assertEquals(listOf(archived), repository.list(VocabularyFilter(archived = true)))
        assertEquals(VocabularyCounts(1, 0, 1, 1), repository.observeCounts().first())
        assertEquals(listOf(archived), repository.pages(VocabularyFilter(archived = true)).asSnapshot())
    }

    @Test fun countsStartAtZeroAndTrackWordsNotTranslationJoinRows() = runBlocking<Unit> {
        assertEquals(VocabularyCounts(0, 0, 0, 0), repository.observeCounts().first())
        val first = repository.create(word().copy(translations = listOf(TranslationInput("дом"), TranslationInput("жилище"))))
        repository.create(word("куче", "собака"))
        assertEquals(VocabularyCounts(2, 2, 0, 3), repository.observeCounts().first())
        repository.setArchived(first.id, true)
        assertEquals(VocabularyCounts(2, 1, 1, 3), repository.observeCounts().first())
        repository.update(first.id, word())
        assertEquals(VocabularyCounts(2, 1, 1, 2), repository.observeCounts().first())
        repository.setArchived(first.id, false)
        assertEquals(VocabularyCounts(2, 2, 0, 2), repository.observeCounts().first())
    }

    @Test fun countsFlowInvalidatesForTranslationOnlyEdits() = runBlocking<Unit> {
        val saved = repository.create(word())
        val initial = CompletableDeferred<Unit>()
        val result = async {
            withTimeout(10_000) {
                repository.observeCounts().onEach { if (it.translations == 1L) initial.complete(Unit) }
                    .first { it.translations == 2L }
            }
        }
        withTimeout(10_000) { initial.await() }
        database.vocabularyDao().insertTranslation(TranslationEntity(
            wordId = saved.id, russianText = "жилище", normalizedText = "жилище", isPrimary = false))
        assertEquals(VocabularyCounts(1, 1, 0, 2), result.await())
    }

    @Test fun pagingLoadsBoundedPagesAndScrollsLargeDictionaryInStableOrder() = runBlocking<Unit> {
        val saved = (0 until 125).map { repository.create(word("дума$it", "перевод$it")) }
        val first = repository.pages().asSnapshot()
        assertTrue(first.size in 40..80) // Initial page plus bounded prefetch; never the whole dictionary.
        assertEquals(saved.take(first.size), first)
        val scrolled = repository.pages().asSnapshot { scrollTo(115) }
        assertEquals(saved, scrolled)
    }

    @Test fun pagingFindsSecondaryTranslationsLiteralWildcardsAndArchiveChanges() = runBlocking<Unit> {
        val saved = repository.create(word().copy(translations = listOf(TranslationInput("дом"), TranslationInput("100%_! ЖИЛИЩЕ"))))
        repository.create(word("куче", "собака"))
        assertEquals(listOf(saved), repository.pages(VocabularyFilter(query = "жилище")).asSnapshot())
        assertEquals(listOf(saved), repository.pages(VocabularyFilter(query = "%_!")).asSnapshot())
        val source = database.vocabularyDao().paging(false, null, VocabularyText.searchPattern("жилище"))
        val loaded = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 40, placeholdersEnabled = false))
        assertTrue(loaded is PagingSource.LoadResult.Page)
        val invalidated = CompletableDeferred<Unit>()
        source.registerInvalidatedCallback { invalidated.complete(Unit) }
        repository.setArchived(saved.id, true)
        withTimeout(10_000) { invalidated.await() }
        assertTrue(repository.pages(VocabularyFilter(query = "жилище")).asSnapshot().isEmpty())
        assertEquals(listOf(saved.id), repository.pages(VocabularyFilter(query = "жилище", archived = true))
            .asSnapshot().map { it.id })
    }

    @Test fun concurrentDuplicateCreationCommitsOnlyOneCompleteWord() = runBlocking<Unit> {
        val results = listOf(" КЪЩА ", "къща").map { bg ->
            async(Dispatchers.Default) { runCatching { repository.create(word(bg)) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is DuplicateWordException)
        assertEquals(1, repository.list().size)
        assertEquals(1, translationCount())
    }

    @Test fun observationRefreshesWhenOnlyARussianTranslationChanges() = runBlocking<Unit> {
        val saved = repository.create(word("котка", "кошка"))
        val initial = CompletableDeferred<Unit>()
        val result = async {
            withTimeout(10_000) {
                repository.observe(VocabularyFilter(query = "ЗВЕРЬ"))
                    .onEach { if (it.isEmpty()) initial.complete(Unit) }
                    .first { it.isNotEmpty() }
            }
        }
        withTimeout(10_000) { initial.await() }
        val translation = database.vocabularyDao().get(saved.id)!!.translations.single()
        database.vocabularyDao().updateTranslation(translation.copy(russianText = "зверь", normalizedText = "зверь"))
        assertEquals("зверь", result.await().single().translations.single().russianText)
    }

    @Test fun unsupportedSchemaVersionFailsWithoutDestructivelyDeletingData() = runBlocking<Unit> {
        val saved = repository.create(word())
        database.openHelper.writableDatabase.execSQL("PRAGMA user_version = 5")
        database.close()
        database = open()
        repository = VocabularyRepository(database, clock)
        expectFailure<IllegalStateException> { repository.get(saved.id) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(filename).path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT bulgarian_text FROM words WHERE id = ?", arrayOf(saved.id.toString())).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("къща", cursor.getString(0))
            }
        }
    }

    private fun translationCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM translations").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline action: suspend () -> Unit): T {
        try {
            action()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
