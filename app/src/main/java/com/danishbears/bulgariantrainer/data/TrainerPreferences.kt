package com.danishbears.bulgariantrainer.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danishbears.bulgariantrainer.review.StudyScope
import com.danishbears.bulgariantrainer.flashcards.FlashcardDirection

@Entity(tableName = "trainer_preferences")
internal data class TrainerPreferencesEntity(@PrimaryKey val id: Int = 1,
    @ColumnInfo(name="flashcard_size") val flashcardSize: Int = 20,
    @ColumnInfo(name="flashcard_direction") val flashcardDirection: String = "BG_TO_RU",
    @ColumnInfo(name="flashcard_scope") val flashcardScope: String = "MIXED",
    @ColumnInfo(name="typing_scope") val typingScope: String = "MIXED",
    @ColumnInfo(name="sentence_size") val sentenceSize: Int = 10,
    @ColumnInfo(name="sentence_scope") val sentenceScope: String = "MIXED")
@Dao internal interface TrainerPreferencesDao {
    @Query("SELECT * FROM trainer_preferences WHERE id=1") suspend fun get(): TrainerPreferencesEntity?
    @Upsert suspend fun put(value: TrainerPreferencesEntity)
}
data class TrainerPreferences(val flashcardSize: Int = 20, val flashcardDirection: String = "BG_TO_RU",
    val flashcardScope: String = "MIXED", val typingScope: String = "MIXED", val sentenceSize: Int = 10, val sentenceScope: String = "MIXED")
class TrainerPreferencesRepository internal constructor(private val db: TrainerDatabase) {
    suspend fun get(): TrainerPreferences = db.withContentTransaction { row().let { TrainerPreferences(it.flashcardSize,it.flashcardDirection,it.flashcardScope,it.typingScope,it.sentenceSize,it.sentenceScope) } }
    private suspend fun row() = db.preferencesDao().get() ?: TrainerPreferencesEntity()
    suspend fun flashcardSize(value: Int) { require(value in 1..100); change { copy(flashcardSize=value) } }
    suspend fun flashcardDirection(value: FlashcardDirection) = change { copy(flashcardDirection=value.name) }
    suspend fun flashcardScope(value: StudyScope) = change { copy(flashcardScope=value.name) }
    suspend fun typingScope(value: StudyScope) = change { copy(typingScope=value.name) }
    suspend fun sentenceSize(value: Int) { require(value in 1..100); change { copy(sentenceSize=value) } }
    suspend fun sentenceScope(value: StudyScope) = change { copy(sentenceScope=value.name) }
    private suspend fun change(update: TrainerPreferencesEntity.() -> TrainerPreferencesEntity) = db.withContentTransaction { db.preferencesDao().put(row().update()) }
}
internal val MIGRATION_6_7 = object : Migration(6,7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `trainer_preferences` (`id` INTEGER NOT NULL, `flashcard_size` INTEGER NOT NULL, `flashcard_direction` TEXT NOT NULL, `flashcard_scope` TEXT NOT NULL, `typing_scope` TEXT NOT NULL, `sentence_size` INTEGER NOT NULL, `sentence_scope` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}
