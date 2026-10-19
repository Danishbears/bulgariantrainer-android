package com.danishbears.bulgariantrainer.review

import androidx.room.withTransaction
import com.danishbears.bulgariantrainer.data.*
import java.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ReviewRepository internal constructor(private val database: TrainerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.reviewDao()
    private val scheduler = ReviewScheduler(clock)
    fun observeAvailable(mode: ReviewMode, scope: StudyScope): Flow<Long> = combine(dao.observeChanges(), flow {
        while (true) { emit(clock.millis()); delay(60_000) }
    }) { _, _ -> available(mode, scope) }
    suspend fun available(mode: ReviewMode, scope: StudyScope): Long = database.withTransaction {
        val at = clock.instant()
        if (mode == ReviewMode.SENTENCE) {
            val rows = dao.sentences().associateBy { it.sentenceId }
            dao.sentenceIds().count { scheduler.eligible(scheduler.category(rows[it]?.schedule(), at), scope) }.toLong()
        } else {
            val rows = dao.words().filter { it.mode == mode.name }.associateBy { it.wordId }
            dao.wordIds().count { scheduler.eligible(scheduler.category(rows[it]?.schedule(), at), scope) }.toLong()
        }
    }
    fun observeTypingAvailable(scope: StudyScope): Flow<Long> = combine(dao.observeChanges(), flow {
        while (true) { emit(clock.millis()); delay(60_000) }
    }) { _, _ -> database.withTransaction {
        val at = clock.instant(); val rows = dao.words().associateBy { it.wordId to it.mode }
        dao.wordIds().count { id -> listOf(ReviewMode.TYPING_BG_TO_RU, ReviewMode.TYPING_RU_TO_BG).any { mode ->
            scheduler.eligible(scheduler.category(rows[id to mode.name]?.schedule(), at), scope)
        } }.toLong()
    } }
}
