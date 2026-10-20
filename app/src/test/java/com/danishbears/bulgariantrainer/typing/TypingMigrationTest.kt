package com.danishbears.bulgariantrainer.typing

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import java.util.UUID
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
class TypingMigrationTest {
    @Test fun populatedV3PreservesContentFlashcardQueueAttemptsAndRecognitionWhenAddingTyping() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "typing-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val raw = javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/3.json")!!.bufferedReader().use { it.readText() }
        val schema = Json.parseToJsonElement(raw).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject; val table = e.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray.orEmpty().forEach { index -> sqlite.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.execSQL("INSERT INTO words VALUES(7,'Къща','къща','съществително','съществително','женский','Заметка',0,1234,5678)")
            sqlite.execSQL("INSERT INTO translations VALUES(13,7,'Дом','дом',1)")
            sqlite.execSQL("INSERT INTO words VALUES(8,'Куче','куче',NULL,NULL,NULL,NULL,0,12,56)")
            sqlite.execSQL("INSERT INTO translations VALUES(14,8,'Собака','собака',1)")
            sqlite.execSQL("""INSERT INTO sentences(id,source_id,source_filename,source_row,russian_text,normalized_russian,bulgarian_text,difficulty,archived,approved,created_at,updated_at)
                VALUES(9,'old-9','old.csv',2,'Я здесь.','я здесь.','Тук съм.',2,0,1,5,9)""")
            sqlite.execSQL("INSERT INTO sentence_alternatives VALUES(11,9,0,'Аз съм тук.','аз съм тук.')")
            sqlite.execSQL("INSERT INTO import_receipts VALUES('old-import','fingerprint',2,3,4,77)")
            sqlite.execSQL("INSERT INTO flashcard_sessions VALUES(5,'previous-request',2,'MIXED','IN_PROGRESS',2,2,100,NULL)")
            sqlite.execSQL("""INSERT INTO flashcard_cards VALUES(21,5,7,1,'BG_TO_RU','Къща','["Дом"]',101)""")
            sqlite.execSQL("""INSERT INTO flashcard_cards VALUES(22,5,8,2,'RU_TO_BG','Куче','["Собака"]',NULL)""")
            sqlite.execSQL("INSERT INTO flashcard_attempts VALUES(30,21,1,102)")
            sqlite.execSQL("INSERT INTO recognition_progress VALUES(7,1,1,1,102)")
            sqlite.version = 3
        }
        val db = Room.databaseBuilder(context, TrainerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        try {
            val vocabulary = VocabularyRepository(db).get(7)
            assertEquals("Заметка", vocabulary.notes); assertEquals(13L, vocabulary.translations.single().id)
            assertEquals(1234L, vocabulary.createdAt.toEpochMilli())
            assertEquals(11L, db.contentDao().sentenceBatch(0, true).single().alternatives.single().id)
            assertEquals(ImportReceiptEntity("old-import", "fingerprint", 2, 3, 4, 77), db.contentDao().receipt("old-import"))
            val flash = FlashcardRepository(db); val before = flash.restore()!!
            assertEquals(5L, before.id); assertEquals(2, before.currentPosition); assertEquals(1, before.known)
            assertEquals(22L, before.question!!.id); assertEquals(listOf("Собака"), before.question!!.prompt)
            assertFalse(before.question!!.revealed); assertEquals(1, flash.progress(7)!!.attempts)
            val typing = TypingRepository(db); assertNull(typing.restore()); assertNull(typing.progress(7, TypingDirection.BG_TO_RU))
            typing.start(UUID.randomUUID().toString(), true)
            assertEquals(before, flash.restore()); assertEquals(1, flash.progress(7)!!.attempts)
            flash.reveal(before.id, before.question!!.id)
            assertEquals(FlashcardStatus.COMPLETED, flash.rate(before.id, before.question!!.id, false).status)
            assertEquals(7, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
