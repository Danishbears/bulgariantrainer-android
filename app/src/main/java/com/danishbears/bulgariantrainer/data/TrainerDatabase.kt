package com.danishbears.bulgariantrainer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WordEntity::class, TranslationEntity::class, SentenceEntity::class, SentenceAlternativeEntity::class, ImportReceiptEntity::class], version = 2, exportSchema = true)
internal abstract class TrainerDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun contentDao(): ContentDao

    companion object {
        fun open(context: Context): TrainerDatabase = Room.databaseBuilder(
            context.applicationContext,
            TrainerDatabase::class.java,
            "bulgariantrainer.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
