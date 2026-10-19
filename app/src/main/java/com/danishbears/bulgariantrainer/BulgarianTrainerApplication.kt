package com.danishbears.bulgariantrainer

import android.app.Application
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.VocabularyRepository

class BulgarianTrainerApplication : Application() {
    private val database by lazy { TrainerDatabase.open(this) }

    val vocabulary: VocabularyRepository by lazy { VocabularyRepository(database) }
}
