package com.danishbears.bulgariantrainer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WordEntity::class, TranslationEntity::class], version = 1, exportSchema = true)
internal abstract class TrainerDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao

    companion object {
        fun open(context: Context): TrainerDatabase = Room.databaseBuilder(
            context.applicationContext,
            TrainerDatabase::class.java,
            "bulgariantrainer.db",
        ).build()
    }
}
