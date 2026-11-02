package com.danishbears.bulgariantrainer.flashcards

import java.time.Instant
import com.danishbears.bulgariantrainer.review.StudyScope

enum class FlashcardDirection { BG_TO_RU, RU_TO_BG, MIXED }
enum class CardDirection { BG_TO_RU, RU_TO_BG }
enum class FlashcardStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class CardResult { CORRECT, INCORRECT, SKIPPED }
enum class FlashcardError { INVALID_SETTINGS, EMPTY_DICTIONARY, EMPTY_REVIEW_QUEUE, EMPTY_NEW_QUEUE, SESSION_NOT_FOUND, SESSION_CLOSED,
    STALE_CARD, ANSWER_HIDDEN, RATING_CONFLICT, START_REQUEST_CHANGED, RESULTS_UNAVAILABLE, STORAGE_FAILED }
class FlashcardException(val code: FlashcardError) : IllegalStateException(code.name)

data class FlashcardSettings(val size: Int = 20, val direction: FlashcardDirection = FlashcardDirection.BG_TO_RU, val scope: StudyScope = StudyScope.MIXED)
data class FlashcardQuestion(
    val id: Long, val position: Int, val direction: CardDirection,
    val prompt: List<String>, val revealed: Boolean, val answer: List<String>,
)
data class FlashcardResult(
    val cardId: Long, val position: Int, val direction: CardDirection,
    val bulgarian: String, val russian: List<String>, val result: CardResult,
)
data class FlashcardState(
    val id: Long, val settings: FlashcardSettings, val status: FlashcardStatus,
    val total: Int, val answered: Int, val known: Int, val unknown: Int, val remaining: Int,
    val currentPosition: Int, val startedAt: Instant, val completedAt: Instant?,
    val question: FlashcardQuestion?, val results: List<FlashcardResult>,
) {
    val active get() = status == FlashcardStatus.IN_PROGRESS
}
data class RecognitionProgress(
    val wordId: Long, val attempts: Int, val known: Int, val consecutiveKnown: Int, val lastRatedAt: Instant,
)

/** Recognition counters only. No due dates, review intervals, mastery status or other training modes. */
internal object RecognitionUpdates {
    fun record(previous: RecognitionProgress?, wordId: Long, known: Boolean, at: Instant) = RecognitionProgress(
        wordId, (previous?.attempts ?: 0) + 1, (previous?.known ?: 0) + if (known) 1 else 0,
        if (known) (previous?.consecutiveKnown ?: 0) + 1 else 0, at,
    )
}
internal fun cardDirection(setting: FlashcardDirection, zeroBasedPosition: Int, firstBulgarian: Boolean): CardDirection = when (setting) {
    FlashcardDirection.BG_TO_RU -> CardDirection.BG_TO_RU
    FlashcardDirection.RU_TO_BG -> CardDirection.RU_TO_BG
    FlashcardDirection.MIXED -> if ((zeroBasedPosition % 2 == 0) == firstBulgarian) CardDirection.BG_TO_RU else CardDirection.RU_TO_BG
}
