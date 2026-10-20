package com.danishbears.bulgariantrainer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WordEntity::class, TranslationEntity::class, SentenceEntity::class, SentenceAlternativeEntity::class, ImportReceiptEntity::class, FlashcardSessionEntity::class, FlashcardCardEntity::class, FlashcardAttemptEntity::class, RecognitionProgressEntity::class, TypingSessionEntity::class, TypingQuestionEntity::class, TypingAttemptEntity::class, TypingProgressEntity::class, SentenceSessionEntity::class, SentenceQuestionEntity::class, SentenceAttemptEntity::class, SentenceProgressEntity::class, WordReviewScheduleEntity::class, SentenceReviewScheduleEntity::class, TrainerPreferencesEntity::class], version = 7, exportSchema = true)
internal abstract class TrainerDatabase : RoomDatabase() {
    internal val contentEpoch = java.util.concurrent.atomic.AtomicLong()
    abstract fun preferencesDao(): TrainerPreferencesDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun contentDao(): ContentDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun typingDao(): TypingDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun reviewDao(): ReviewDao
    abstract fun statisticsDao(): StatisticsDao

    companion object {
        fun open(context: Context): TrainerDatabase = Room.databaseBuilder(
            context.applicationContext,
            TrainerDatabase::class.java,
            "bulgariantrainer.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
}
