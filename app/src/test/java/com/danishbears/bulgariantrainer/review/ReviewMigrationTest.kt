package com.danishbears.bulgariantrainer.review

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.statistics.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.*
import java.time.*
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
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ReviewMigrationTest {
    @Test fun v5UpgradePreservesRawHistoryAndCountersAndCopiesOnlyKnownSchedulingFacts()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="schedule-migration-${UUID.randomUUID()}.db"
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val schema=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/5.json")!!.bufferedReader().use { it.readText() }).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path,null).use { s ->
            s.setForeignKeyConstraintsEnabled(true)
            for(element in schema.getValue("entities").jsonArray) {
                val e=element.jsonObject;val table=e.getValue("tableName").jsonPrimitive.content
                s.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                e["indices"]?.jsonArray.orEmpty().forEach { s.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { s.execSQL(it.jsonPrimitive.content) }
            s.execSQL("INSERT INTO words VALUES(7,'Къща','къща',NULL,NULL,NULL,'История',0,100,100)")
            s.execSQL("INSERT INTO translations VALUES(13,7,'Дом','дом',1)")
            s.execSQL("INSERT INTO sentences VALUES(9,'old-9','old.csv',2,'Я здесь.','я здесь.','Тук съм.',NULL,NULL,NULL,0,1,100,100)")
            s.execSQL("INSERT INTO sentence_alternatives VALUES(11,9,0,'Аз съм тук.','аз съм тук.')")
            s.execSQL("INSERT INTO import_receipts VALUES('old-receipt','fingerprint',1,2,3,100)")
            s.execSQL("INSERT INTO recognition_progress VALUES(7,12,9,4,1000)")
            s.execSQL("INSERT INTO typing_progress VALUES(7,'BG_TO_RU',3,2,1,1000)")
            s.execSQL("INSERT INTO typing_progress VALUES(7,'RU_TO_BG',4,1,0,1000)")
            s.execSQL("INSERT INTO typing_progress VALUES(7,'UNKNOWN',2,1,0,1000)")
            s.execSQL("INSERT INTO sentence_progress VALUES(9,3,1,1,1,0,1000)")
            s.execSQL("INSERT INTO flashcard_sessions VALUES(1,'old-flash',1,'BG_TO_RU','COMPLETED',1,2,100,1000)")
            s.execSQL("""INSERT INTO flashcard_cards VALUES(21,1,7,1,'BG_TO_RU','Къща','["Дом"]',900)""")
            s.execSQL("INSERT INTO flashcard_attempts VALUES(31,21,1,1000)")
            s.execSQL("INSERT INTO typing_sessions VALUES(1,'old-typing',1,'IN_PROGRESS',1,1,0,100,NULL)")
            s.execSQL("""INSERT INTO typing_questions VALUES(22,1,7,1,'BG_TO_RU','Къща','["Дом"]',0)""")
            s.execSQL("INSERT INTO sentence_sessions VALUES(1,'old-sentence',1,'IN_PROGRESS',1,1,1,100,NULL)")
            s.execSQL("""INSERT INTO sentence_questions VALUES(23,1,9,1,'Я здесь.','["Тук съм.","Аз съм тук."]',NULL,NULL,NULL,0)""")
            s.execSQL("INSERT INTO sentence_attempts VALUES(32,23,'Вариант.','вариант.',0,NULL,1001,NULL)")
            s.version=5
        }
        val db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6, MIGRATION_6_7).build()
        val at=Instant.parse("2026-10-10T12:30:00Z");val clock=Clock.fixed(at,ZoneOffset.UTC)
        try {
            val review=db.reviewDao();val flash=review.word(7,"FLASHCARD")!!.schedule()
            assertEquals(12,flash.attempts);assertEquals(9,flash.successes);assertEquals(4,flash.streak);assertEquals(0,flash.intervalDays)
            assertEquals(Instant.ofEpochMilli(1000),flash.nextReview);assertTrue(flash.inherited)
            assertEquals(RecognitionProgressEntity(7,12,9,4,1000),db.flashcardDao().progress(7))
            assertEquals(TypingProgressEntity(7,"BG_TO_RU",3,2,1,1000),db.typingDao().progress(7,"BG_TO_RU"))
            assertNull(review.word(7,"UNKNOWN"));assertEquals(2,db.typingDao().progress(7,"UNKNOWN")!!.attempts)
            assertEquals(0,review.word(7,"TYPING_BG_TO_RU")!!.streak);assertEquals(0,review.word(7,"TYPING_RU_TO_BG")!!.streak)
            assertEquals(3,review.sentence(9)!!.attempts);assertEquals(0,review.sentence(9)!!.streak)
            val stats=StatisticsRepository(db,clock).overview();assertEquals(1L,stats.reviewAttempts);assertEquals(4,stats.inheritedSchedules)
            assertEquals(1L,stats.vocabulary.dueItems);assertEquals(1L,stats.completedSessions)
            assertEquals(ImportReceiptEntity("old-receipt","fingerprint",1,2,3,100),db.contentDao().receipt("old-receipt"))
            val typing=TypingRepository(db,clock);val resumed=typing.restore()!!
            assertEquals(22L,resumed.question!!.id);assertEquals(StudyScope.MIXED,resumed.scope)
            typing.submit(resumed.id,resumed.question!!.id,"Дом")
            assertEquals(4,review.word(7,"TYPING_BG_TO_RU")!!.attempts);assertEquals(1,review.word(7,"TYPING_BG_TO_RU")!!.streak)
            assertEquals(2,db.typingDao().progress(7,"BG_TO_RU")!!.consecutiveCorrect)
            val sentences=SentencePracticeRepository(db,clock);val pending=sentences.restore()!!
            assertEquals(1,pending.pending);assertEquals("Вариант.",pending.question!!.feedback!!.submitted)
            assertEquals(3,review.sentence(9)!!.attempts)
            sentences.selfRate(pending.id,pending.question!!.id,SentenceRating.PARTIAL)
            assertEquals(4,review.sentence(9)!!.attempts);assertEquals(1,review.sentence(9)!!.intervalDays)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
            assertEquals(7,db.openHelper.readableDatabase.version)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
