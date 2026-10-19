package com.danishbears.bulgariantrainer.review

import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class StudyScope { DUE, NEW, MIXED }
enum class LearningCategory { NEW, DUE, LEARNING, MASTERED }
enum class ReviewMode { FLASHCARD, TYPING_BG_TO_RU, TYPING_RU_TO_BG, SENTENCE }
data class ReviewSchedule(val attempts: Int = 0, val successes: Int = 0, val streak: Int = 0,
    val intervalDays: Int = 0, val lastReview: Instant? = null, val nextReview: Instant? = null,
    val inherited: Boolean = false)

/** Exact pinned web SpacedRepetitionScheduler policy, not SM-2. Duration days are 24 hours. */
class ReviewScheduler(private val clock: Clock = Clock.systemUTC(), private val masterySuccesses: Int = 5,
    private val masteryIntervalDays: Int = 28) {
    init { require(masterySuccesses > 0 && masteryIntervalDays > 0) }
    fun now(): Instant = clock.instant()
    fun intervalFor(correct: Boolean, consecutiveBefore: Int, previousInterval: Int): Int = when {
        !correct -> 1
        consecutiveBefore == 0 -> 1
        consecutiveBefore == 1 -> 3
        consecutiveBefore == 2 -> 7
        else -> minOf(365, maxOf(7, previousInterval) * 2)
    }
    fun record(previous: ReviewSchedule?, correct: Boolean, at: Instant = now()): ReviewSchedule {
        val old = previous ?: ReviewSchedule()
        val interval = intervalFor(correct, old.streak, old.intervalDays)
        return old.copy(attempts = old.attempts + 1, successes = old.successes + if (correct) 1 else 0,
            streak = if (correct) old.streak + 1 else 0, intervalDays = interval, lastReview = at,
            nextReview = at.plus(Duration.ofDays(interval.toLong())))
    }
    fun category(schedule: ReviewSchedule?, at: Instant = now()): LearningCategory = when {
        schedule == null || schedule.attempts == 0 -> LearningCategory.NEW
        schedule.nextReview == null || !schedule.nextReview.isAfter(at) -> LearningCategory.DUE
        schedule.streak >= masterySuccesses && schedule.intervalDays >= masteryIntervalDays -> LearningCategory.MASTERED
        else -> LearningCategory.LEARNING
    }
    fun eligible(category: LearningCategory, scope: StudyScope) = scope == StudyScope.MIXED ||
        scope == StudyScope.DUE && category == LearningCategory.DUE || scope == StudyScope.NEW && category == LearningCategory.NEW
    fun priority(category: LearningCategory): Int = when (category) {
        LearningCategory.DUE -> 0; LearningCategory.NEW -> 1; LearningCategory.LEARNING -> 2; LearningCategory.MASTERED -> 3
    }
}
