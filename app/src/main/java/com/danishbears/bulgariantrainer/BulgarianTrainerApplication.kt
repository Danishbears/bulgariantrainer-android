package com.danishbears.bulgariantrainer

import android.app.Application
import com.danishbears.bulgariantrainer.typing.TypingRepository
import com.danishbears.bulgariantrainer.flashcards.FlashcardRepository
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.transfer.ContentTransferRepository
import com.danishbears.bulgariantrainer.data.VocabularyRepository

class BulgarianTrainerApplication : Application() {
    private val database by lazy { TrainerDatabase.open(this) }

    val typing: TypingRepository by lazy { TypingRepository(database) }

    val flashcards: FlashcardRepository by lazy { FlashcardRepository(database) }

    val transfer: ContentTransferRepository by lazy { ContentTransferRepository(database) }

    val vocabulary: VocabularyRepository by lazy { VocabularyRepository(database) }
}
