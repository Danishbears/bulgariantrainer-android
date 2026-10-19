package com.danishbears.bulgariantrainer.flashcards

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FlashcardMigrationTest {
    @Test fun populatedExportedV2MigratesToV3PreservingWordsSentencesAlternativesAndReceipts() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "flashcard-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val raw = javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/2.json")!!.bufferedReader().use { it.readText() }
        val schema = Json.parseToJsonElement(raw).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            schema.getValue("entities").jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray.orEmpty().forEach { index -> sqlite.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.execSQL("INSERT INTO words VALUES(7,'Къща','къща','съществително','съществително','женский','Заметка',0,1234,5678)")
            sqlite.execSQL("INSERT INTO translations VALUES(13,7,'Дом','дом',1)")
            sqlite.execSQL("""INSERT INTO sentences(id,source_id,source_filename,source_row,russian_text,normalized_russian,bulgarian_text,
                explanation,difficulty,topic,archived,approved,created_at,updated_at)
                VALUES(9,'source-9','old.csv',2,'Я здесь.','я здесь.','Тук съм.','Пояснение',2,'Тема',1,1,5,9)""")
            sqlite.execSQL("INSERT INTO sentence_alternatives VALUES(11,9,0,'Аз съм тук.','аз съм тук.')")
            sqlite.execSQL("INSERT INTO import_receipts VALUES('existing-receipt','fingerprint',2,3,4,77)")
            sqlite.version = 2
        }
        val db = Room.databaseBuilder(context, TrainerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        try {
            val word = VocabularyRepository(db).get(7) // Opening validates the full migrated Room schema.
            assertEquals("Заметка", word.notes)
            assertEquals("женский", word.grammaticalGender)
            assertEquals(13L, word.translations.single().id)
            assertEquals(1234L, word.createdAt.toEpochMilli())
            val sentence = db.contentDao().sentenceBatch(0, true).single()
            assertEquals(9L, sentence.sentence.id)
            assertTrue(sentence.sentence.archived)
            assertTrue(sentence.sentence.approved)
            assertEquals("Пояснение", sentence.sentence.explanation)
            assertEquals(11L, sentence.alternatives.single().id)
            assertEquals(ImportReceiptEntity("existing-receipt", "fingerprint", 2, 3, 4, 77), db.contentDao().receipt("existing-receipt"))
            val flashcards = FlashcardRepository(db)
            assertNull(flashcards.restore())
            assertNull(flashcards.progress(7))
            assertEquals(1L, flashcards.observeAvailable().first())
            val session = flashcards.start(UUID.randomUUID().toString())
            assertEquals(listOf("Къща"), session.question!!.prompt)
            assertEquals(4, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
