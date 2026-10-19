package com.danishbears.bulgariantrainer.sentences

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
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
class SentenceMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun fixture(name: String, populate: (SQLiteDatabase) -> Unit) {
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val raw = javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/4.json")!!.bufferedReader().use { it.readText() }
        val schema = Json.parseToJsonElement(raw).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            sqlite.setForeignKeyConstraintsEnabled(true)
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject; val table = e.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray.orEmpty().forEach { index -> sqlite.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            populate(sqlite); sqlite.version = 4
        }
    }
    private fun open(name: String) = Room.databaseBuilder(context, TrainerDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    private fun sequence(db: TrainerDatabase, name: String) = db.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(name)).use { it.moveToFirst(); it.getLong(0) }
    @Test fun populatedV4MigratesWithoutLosingAlternativeIdsProvenanceCountersOrUnfinishedOtherModes() = runBlocking<Unit> {
        val name = "sentence-migration-${UUID.randomUUID()}.db"
        fixture(name) { s ->
            s.execSQL("INSERT INTO words VALUES(7,'Къща','къща','съществително','съществително','женский','Заметка',0,1234,5678)")
            s.execSQL("INSERT INTO translations VALUES(13,7,'Дом','дом',1)")
            s.execSQL("INSERT INTO sentences VALUES(9,'old-9','old.csv',2,'Я здесь.','я здесь.','Тук съм.','Пояснение',2,'Тема',0,1,5,9)")
            s.execSQL("INSERT INTO sentence_alternatives VALUES(11,9,0,'Аз съм тук.','аз съм тук.')")
            s.execSQL("INSERT INTO import_receipts VALUES('old-import','fingerprint',2,3,4,77)")
            s.execSQL("INSERT INTO flashcard_sessions VALUES(5,'previous-flash',1,'BG_TO_RU','IN_PROGRESS',1,1,100,NULL)")
            s.execSQL("""INSERT INTO flashcard_cards VALUES(21,5,7,1,'BG_TO_RU','Къща','["Дом"]',101)""")
            s.execSQL("INSERT INTO recognition_progress VALUES(7,1,1,1,102)")
            s.execSQL("INSERT INTO typing_sessions VALUES(6,'previous-typing',1,'COMPLETED',1,1,1,100,103)")
            s.execSQL("""INSERT INTO typing_questions VALUES(22,6,7,1,'RU_TO_BG','Къща','["Дом"]',0)""")
            s.execSQL("INSERT INTO typing_attempts VALUES(31,22,'КЪЩА','къща',1,103)")
            s.execSQL("INSERT INTO typing_progress VALUES(7,'RU_TO_BG',1,1,1,103)")
            s.execSQL("UPDATE sqlite_sequence SET seq = 900 WHERE name = 'sentences'")
            s.execSQL("UPDATE sqlite_sequence SET seq = 1100 WHERE name = 'sentence_alternatives'")
        }
        val db = open(name)
        try {
            val content = SentenceRepository(db); val sentence = content.get(9)
            assertEquals("old-9", sentence.sourceId); assertEquals("old.csv", sentence.sourceFilename); assertEquals(2, sentence.sourceRow)
            assertEquals(2, sentence.input.difficulty); assertEquals("Пояснение", sentence.input.explanation)
            assertEquals(11L, db.sentenceDao().get(9)!!.alternatives.single().id)
            assertEquals(900L, sequence(db, "sentences")); assertEquals(1100L, sequence(db, "sentence_alternatives"))
            assertEquals(ImportReceiptEntity("old-import", "fingerprint", 2, 3, 4, 77), db.contentDao().receipt("old-import"))
            assertEquals("Заметка", VocabularyRepository(db).get(7).notes)
            val flash = FlashcardRepository(db); assertTrue(flash.restore()!!.question!!.revealed)
            val typing = TypingRepository(db); assertEquals("КЪЩА", typing.restore()!!.question!!.feedback!!.submitted)
            assertEquals(1, typing.progress(7, TypingDirection.RU_TO_BG)!!.attempts)
            val oldFlash = flash.restore(); val oldTyping = typing.restore()
            content.update(9, sentence.input.copy(difficulty = null))
            assertNull(content.get(9).input.difficulty)
            val practice = SentencePracticeRepository(db); val session = practice.start(UUID.randomUUID().toString())
            assertTrue(practice.submit(session.id, session.question!!.id, "Аз съм тук.").question!!.feedback!!.automaticMatch)
            assertEquals(oldFlash, flash.restore()); assertEquals(oldTyping, typing.restore())
            assertEquals(1, flash.progress(7)!!.attempts)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            assertEquals(5, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun emptyChildTableRetainsDeletedHighWaterIdsAndNewOptionalDifficultyIsValid() = runBlocking<Unit> {
        val name = "sentence-empty-migration-${UUID.randomUUID()}.db"
        fixture(name) { s ->
            s.execSQL("INSERT INTO sentences VALUES(90,'source','source.csv',2,'Дом.','дом.','Къща.',NULL,2,NULL,0,1,1,1)")
            s.execSQL("INSERT INTO sentence_alternatives VALUES(110,90,0,'Дом.','дом.')")
            s.execSQL("DELETE FROM sentence_alternatives"); s.execSQL("DELETE FROM sentences")
        }
        val db = open(name)
        try {
            val content = SentenceRepository(db)
            val created = content.create(SentenceInput("Дом.", "Къща.", listOf("Дом.")))
            assertTrue(created.id > 90L); assertNull(created.input.difficulty)
            assertTrue(db.sentenceDao().get(created.id)!!.alternatives.single().id > 110L)
            assertEquals(0, db.sentenceDao().graph(1)?.questions?.size ?: 0)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
