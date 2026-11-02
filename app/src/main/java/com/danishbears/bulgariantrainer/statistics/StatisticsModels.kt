package com.danishbears.bulgariantrainer.statistics

import com.danishbears.bulgariantrainer.review.*
import java.time.Instant
import java.time.LocalDate

enum class PracticeMode { FLASHCARD, TYPING, SENTENCE }
enum class GradingType { AUTOMATIC, SELF_ASSESSED, UNCLASSIFIED }
data class Rate(val attempts: Long, val correct: Long, val partial: Long, val incorrect: Long, val accuracyPercent: Double?)
data class Performance(val total: Rate, val automatic: Rate, val selfAssessed: Rate, val unclassified: Rate)
data class VocabularyStatistics(val total: Long, val active: Long, val archived: Long, val newItems: Long, val dueItems: Long, val masteredItems: Long)
data class DailyPoint(val date: LocalDate, val reviewAttempts: Long, val correctAnswers: Long, val completedSessions: Long)
data class DifficultWord(val wordId: Long, val bulgarian: String, val archived: Boolean, val translations: List<String>, val incorrectAttempts: Long, val lastIncorrectAt: Instant)
data class RecentSession(val mode: PracticeMode, val id: Long, val status: String, val startedAt: Instant, val completedAt: Instant?,
    val totalQuestions: Int, val answeredQuestions: Long, val correctAnswers: Long, val accuracyPercent: Double?)
data class CategoryCounts(val newItems: Int, val due: Int, val learning: Int, val mastered: Int)
data class StatisticsOverview(val at: Instant, val vocabulary: VocabularyStatistics, val completedSessions: Long, val reviewAttempts: Long,
    val overall: Performance, val flashcards: Performance, val typingBgToRu: Performance, val typingRuToBg: Performance, val sentences: Performance,
    val consecutiveStudyDays: Long, val reviewsScheduledToday: Long, val last7Days: List<DailyPoint>, val last30Days: List<DailyPoint>,
    val difficultWords: List<DifficultWord>, val recentSessions: List<RecentSession>, val categories: Map<ReviewMode, CategoryCounts>,
    val inheritedSchedules: Int)
data class WordMistake(val attemptId: Long, val mode: PracticeMode, val direction: String?, val submitted: String?, val at: Instant, val grading: GradingType)
data class WordMistakes(val wordId: Long, val bulgarian: String, val archived: Boolean, val translations: List<String>, val incorrectAttempts: Long,
    val recent: List<WordMistake>)
internal data class StatisticsWord(val id: Long, val bulgarian: String, val archived: Boolean, val translations: List<String>)
internal data class StatisticsInput(val words: List<StatisticsWord>, val trainableSentences: Set<Long>, val studiedWords: Set<Long>,
    val wordSchedules: Map<Pair<Long, ReviewMode>, ReviewSchedule>, val sentenceSchedules: Map<Long, ReviewSchedule>,
    val attempts: List<StatisticsAttempt>, val sessions: List<StatisticsSession>)
