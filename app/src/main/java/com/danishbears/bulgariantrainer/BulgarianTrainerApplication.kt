package com.danishbears.bulgariantrainer

import android.app.Application
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.transfer.ContentTransferRepository
import com.danishbears.bulgariantrainer.data.VocabularyRepository

class BulgarianTrainerApplication : Application() {
    private val database by lazy { TrainerDatabase.open(this) }

    val transfer: ContentTransferRepository by lazy { ContentTransferRepository(database) }

    val vocabulary: VocabularyRepository by lazy { VocabularyRepository(database) }
}
