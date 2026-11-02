package com.danishbears.bulgariantrainer.statistics

import androidx.room.withTransaction
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.review.*
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StatisticsRepository internal constructor(private val database: TrainerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.statisticsDao()
    fun observeChanges(): Flow<Long> = dao.observeChanges()
    suspend fun overview(): StatisticsOverview = withContext(Dispatchers.Default) {
        database.withTransaction {
            val at = clock.instant()
            val words = dao.words().map { row -> StatisticsWord(row.word.id, row.word.bulgarianText, row.word.archived,
                row.translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id }).map { it.russianText }) }
            StatisticsCalculator.calculate(StatisticsInput(words, dao.trainableSentences().toSet(), dao.studiedWords().toSet(),
                database.reviewDao().words().associate { (it.wordId to ReviewMode.valueOf(it.mode)) to it.schedule() },
                database.reviewDao().sentences().associate { it.sentenceId to it.schedule() }, dao.attempts(), dao.sessions()), at)
        }
    }
    suspend fun wordMistakes(id: Long): WordMistakes = withContext(Dispatchers.Default) {
        database.withTransaction {
            val row = database.vocabularyDao().get(id) ?: throw WordNotFoundException(id)
            val rows = dao.attempts().filter { it.wordId == id && it.result == "INCORRECT" }
            WordMistakes(id, row.word.bulgarianText, row.word.archived,
                row.translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id }).map { it.russianText },
                rows.size.toLong(), rows.sortedWith(compareByDescending<StatisticsAttempt> { it.createdAt }.thenByDescending { it.id }).take(20).map {
                    WordMistake(it.id, PracticeMode.valueOf(it.mode), it.direction, it.submitted, Instant.ofEpochMilli(it.createdAt), GradingType.valueOf(it.grading))
                })
        }
    }
}
