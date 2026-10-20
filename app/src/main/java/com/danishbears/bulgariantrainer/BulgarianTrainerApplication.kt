package com.danishbears.bulgariantrainer

import android.app.Application
import java.time.Clock
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
import com.danishbears.bulgariantrainer.sentences.SentenceRepository
import com.danishbears.bulgariantrainer.sentences.SentencePracticeRepository
import com.danishbears.bulgariantrainer.typing.TypingRepository
import com.danishbears.bulgariantrainer.flashcards.FlashcardRepository
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.transfer.ContentTransferRepository
import com.danishbears.bulgariantrainer.data.VocabularyRepository

class BulgarianTrainerApplication : Application() {
    private val studyClock: Clock = Clock.systemUTC()
    private val database by lazy { TrainerDatabase.open(this) }

    val backup by lazy { com.danishbears.bulgariantrainer.backup.BackupRepository(this,database,studyClock) }
    val preferences by lazy { com.danishbears.bulgariantrainer.data.TrainerPreferencesRepository(database) }

    val statistics: StatisticsRepository by lazy { StatisticsRepository(database, studyClock) }

    val sentences: SentenceRepository by lazy { SentenceRepository(database) }
    val sentencePractice: SentencePracticeRepository by lazy { SentencePracticeRepository(database, studyClock) }

    val typing: TypingRepository by lazy { TypingRepository(database, studyClock) }

    val flashcards: FlashcardRepository by lazy { FlashcardRepository(database, studyClock) }

    val transfer: ContentTransferRepository by lazy { ContentTransferRepository(database) }

    val vocabulary: VocabularyRepository by lazy { VocabularyRepository(database) }
}
