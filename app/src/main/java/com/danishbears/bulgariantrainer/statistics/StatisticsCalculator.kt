package com.danishbears.bulgariantrainer.statistics

import com.danishbears.bulgariantrainer.review.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Port of pinned StatisticsService definitions. No synthetic attempts or persisted dashboard totals. */
internal object StatisticsCalculator {
    private val wordModes = listOf(ReviewMode.FLASHCARD, ReviewMode.TYPING_BG_TO_RU, ReviewMode.TYPING_RU_TO_BG)
    fun finalized(row: StatisticsAttempt) = row.result in setOf("CORRECT", "PARTIAL", "INCORRECT")
    fun rate(rows: List<StatisticsAttempt>): Rate {
        val attempts = rows.size.toLong(); val correct = rows.count { it.result == "CORRECT" }.toLong()
        return Rate(attempts, correct, rows.count { it.result == "PARTIAL" }.toLong(), rows.count { it.result == "INCORRECT" }.toLong(),
            if (attempts == 0L) null else Math.round(correct * 1000.0 / attempts) / 10.0)
    }
    private fun performance(rows: List<StatisticsAttempt>) = Performance(rate(rows), rate(rows.filter { it.grading == "AUTOMATIC" }),
        rate(rows.filter { it.grading == "SELF_ASSESSED" }), rate(rows.filter { it.grading == "UNCLASSIFIED" }))
    fun utcDate(at: Instant): LocalDate = at.atZone(ZoneOffset.UTC).toLocalDate()
    fun calculate(input: StatisticsInput, at: Instant): StatisticsOverview {
        val scheduler = ReviewScheduler(); val today = utcDate(at)
        val history = input.attempts.filter(::finalized)
        val wordById = input.words.associateBy { it.id }
        val trainable = input.words.filter { !it.archived && it.translations.isNotEmpty() }
        val studied = input.studiedWords + history.mapNotNull { it.wordId }
        val categories = mutableMapOf<ReviewMode, CategoryCounts>()
        for (mode in wordModes) {
            val values = trainable.map { scheduler.category(input.wordSchedules[it.id to mode], at) }
            categories[mode] = categoryCounts(values)
        }
        categories[ReviewMode.SENTENCE] = categoryCounts(input.trainableSentences.map { scheduler.category(input.sentenceSchedules[it], at) })
        val vocabulary = VocabularyStatistics(input.words.size.toLong(), input.words.count { !it.archived }.toLong(), input.words.count { it.archived }.toLong(),
            trainable.count { it.id !in studied }.toLong(), trainable.count { word -> wordModes.any { scheduler.category(input.wordSchedules[word.id to it], at) == LearningCategory.DUE } }.toLong(),
            trainable.count { word -> wordModes.all { scheduler.category(input.wordSchedules[word.id to it], at) == LearningCategory.MASTERED } }.toLong())
        val scheduledToday = trainable.sumOf { word -> wordModes.count { mode -> input.wordSchedules[word.id to mode]?.nextReview?.let(::utcDate) == today }.toLong() } +
            input.trainableSentences.count { input.sentenceSchedules[it]?.nextReview?.let(::utcDate) == today }
        val completed = input.sessions.filter { it.status == "COMPLETED" }
        val days = history.groupBy { utcDate(Instant.ofEpochMilli(it.createdAt)) }
        val completedDays = completed.filter { it.completedAt != null }.groupBy { utcDate(Instant.ofEpochMilli(it.completedAt!!)) }
        fun daily(size: Int) = (size - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong()); val reviews = days[date].orEmpty()
            DailyPoint(date, reviews.size.toLong(), reviews.count { it.result == "CORRECT" }.toLong(), completedDays[date].orEmpty().size.toLong())
        }
        var day = if (days.containsKey(today)) today else today.minusDays(1)
        var streak = 0L
        while (days.containsKey(day)) { streak++; day = day.minusDays(1) }
        val wrong = history.filter { it.wordId != null && it.result == "INCORRECT" }.groupBy { it.wordId!! }
        val difficult = wrong.entries.filter { it.key in wordById }.sortedWith(compareByDescending<Map.Entry<Long, List<StatisticsAttempt>>> { it.value.size }
            .thenByDescending { it.value.maxOf { row -> row.createdAt } }.thenBy { it.key }).take(20).map {
            val word = wordById.getValue(it.key)
            DifficultWord(word.id, word.bulgarian, word.archived, word.translations, it.value.size.toLong(), Instant.ofEpochMilli(it.value.maxOf { row -> row.createdAt }))
        }
        val bySession = history.groupBy { it.mode to it.sessionId }
        val recent = input.sessions.sortedWith(compareByDescending<StatisticsSession> { it.startedAt }.thenByDescending { it.id }.thenBy { it.mode })
            .take(10).map {
                val rate = rate(bySession[it.mode to it.id].orEmpty())
                RecentSession(PracticeMode.valueOf(it.mode), it.id, it.status, Instant.ofEpochMilli(it.startedAt), it.completedAt?.let(Instant::ofEpochMilli),
                    it.total, rate.attempts, rate.correct, rate.accuracyPercent)
            }
        return StatisticsOverview(at, vocabulary, completed.size.toLong(), history.size.toLong(), performance(history),
            performance(history.filter { it.mode == "FLASHCARD" }), performance(history.filter { it.mode == "TYPING" && it.direction == "BG_TO_RU" }),
            performance(history.filter { it.mode == "TYPING" && it.direction != "BG_TO_RU" }), performance(history.filter { it.mode == "SENTENCE" }),
            streak, scheduledToday, daily(7), daily(30), difficult, recent, categories,
            input.wordSchedules.values.count { it.inherited } + input.sentenceSchedules.values.count { it.inherited })
    }
    private fun categoryCounts(values: List<LearningCategory>) = CategoryCounts(values.count { it == LearningCategory.NEW }, values.count { it == LearningCategory.DUE },
        values.count { it == LearningCategory.LEARNING }, values.count { it == LearningCategory.MASTERED })
}
