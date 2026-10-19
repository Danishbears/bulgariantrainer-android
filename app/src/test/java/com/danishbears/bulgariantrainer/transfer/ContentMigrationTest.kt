package com.danishbears.bulgariantrainer.transfer

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.MIGRATION_1_2
import com.danishbears.bulgariantrainer.data.MIGRATION_2_3
import com.danishbears.bulgariantrainer.data.MIGRATION_3_4
import com.danishbears.bulgariantrainer.data.MIGRATION_4_5
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.VocabularyRepository
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
class ContentMigrationTest {
    @Test fun populatedExportedVersionOneMigratesWithoutChangingVocabularyIdentitiesOrMetadata() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val json = javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/1.json")!!.bufferedReader().use { it.readText() }
        val schema = Json.parseToJsonElement(json).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e.getValue("indices").jsonArray.forEach { index -> sqlite.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.execSQL("INSERT INTO words VALUES(7,'Къща','къща','съществително','съществително','женский','Сохранить заметку',1,1234,5678)")
            sqlite.execSQL("INSERT INTO translations VALUES(13,7,'Дом','дом',1)")
            sqlite.version = 1
        }
        val db = Room.databaseBuilder(context, TrainerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        try {
            val word = VocabularyRepository(db).get(7) // Room also validates migrated columns, indices and FKs on open.
            assertEquals("Къща", word.bulgarianText)
            assertEquals("Сохранить заметку", word.notes)
            assertEquals("женский", word.grammaticalGender)
            assertTrue(word.archived)
            assertEquals(1234L, word.createdAt.toEpochMilli())
            assertEquals(5678L, word.updatedAt.toEpochMilli())
            assertEquals(13L, word.translations.single().id)
            assertTrue(word.translations.single().isPrimary)
            assertEquals(0L, ContentTransferRepository(db).observeSentenceCount().first())
            assertEquals(5, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
