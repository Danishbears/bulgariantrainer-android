package com.danishbears.bulgariantrainer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WordEntity::class, TranslationEntity::class, SentenceEntity::class, SentenceAlternativeEntity::class, ImportReceiptEntity::class, FlashcardSessionEntity::class, FlashcardCardEntity::class, FlashcardAttemptEntity::class, RecognitionProgressEntity::class, TypingSessionEntity::class, TypingQuestionEntity::class, TypingAttemptEntity::class, TypingProgressEntity::class, SentenceSessionEntity::class, SentenceQuestionEntity::class, SentenceAttemptEntity::class, SentenceProgressEntity::class, WordReviewScheduleEntity::class, SentenceReviewScheduleEntity::class, TrainerPreferencesEntity::class, AspectDatasetEntity::class, AspectPairEntity::class, AspectAnswerEntity::class, AspectReceiptEntity::class, AspectSessionEntity::class, AspectQuestionEntity::class, AspectAttemptEntity::class, AspectProgressEntity::class, AspectScheduleEntity::class, AoristDatasetEntity::class, AoristVerbEntity::class, AoristFormEntity::class, AoristAnswerEntity::class, AoristReceiptEntity::class, AoristSessionEntity::class, AoristQuestionEntity::class, AoristAttemptEntity::class, AoristProgressEntity::class, AoristScheduleEntity::class, AoristPreferencesEntity::class], version = 11, exportSchema = true)
internal abstract class TrainerDatabase : RoomDatabase() {
    internal val contentEpoch = java.util.concurrent.atomic.AtomicLong()
    abstract fun aoristPracticeDao(): AoristPracticeDao
    abstract fun aoristDao(): AoristDao
    abstract fun aspectPracticeDao(): AspectPracticeDao
    abstract fun aspectStatisticsDao(): AspectStatisticsDao
    abstract fun aspectDao(): AspectDao
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
    }
}
