package com.danishbears.bulgariantrainer.typing

import com.danishbears.bulgariantrainer.data.VocabularyText
import java.time.Instant
import kotlin.random.Random

enum class TypingDirection { BG_TO_RU, RU_TO_BG }
enum class TypingStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class TypingError { EMPTY_DICTIONARY, SHORTER_SESSION_REQUIRED, INVALID_REQUEST, START_REQUEST_CHANGED,
    SESSION_NOT_FOUND, SESSION_CLOSED, STALE_QUESTION, ANSWER_CONFLICT, ANSWER_REQUIRED, RESULTS_UNAVAILABLE, STORAGE_FAILED }
class TypingException(val code: TypingError) : IllegalStateException(code.name)
data class TypingFeedback(val correct: Boolean, val submitted: String, val accepted: List<String>)
data class TypingQuestion(val id: Long, val position: Int, val direction: TypingDirection, val prompt: List<String>, val feedback: TypingFeedback?)
data class TypingMistake(val questionId: Long, val position: Int, val direction: TypingDirection,
    val prompt: List<String>, val submitted: String, val accepted: List<String>)
data class TypingState(val id: Long, val status: TypingStatus, val total: Int, val answered: Int, val correct: Int,
    val currentPosition: Int, val revision: Long, val question: TypingQuestion?, val mistakes: List<TypingMistake>) {
    val active get() = status == TypingStatus.IN_PROGRESS
    val incorrect get() = answered - correct
    val remaining get() = total - answered
}
data class TypingProgress(val wordId: Long, val direction: TypingDirection, val attempts: Int, val correct: Int,
    val consecutiveCorrect: Int, val lastSubmittedAt: Instant)

/** Exact web VocabularyText contract; no accent removal, transliteration, punctuation stripping or fuzzy grading. */
internal object TypingEvaluator {
    fun key(answer: String) = VocabularyText.key(answer)
    fun accepts(answer: String, accepted: List<String>) = key(answer).let { normalized ->
        normalized.isNotEmpty() && accepted.any { key(it) == normalized }
    }
}
internal data class PlannedTypingWord(val wordId: Long, val direction: TypingDirection)
/** All candidates are eligible in both directions until scheduling is integrated separately.
 * Select without replacement, assign the less-used direction (BG on ties), then shuffle pairs.
 * The injected generator controls BOTH selection and order; SQL randomness is never involved.
 */
internal class TypingPlanner(private val random: Random) {
    fun plan(ids: List<Long>): List<PlannedTypingWord> = ids.distinct().shuffled(random).take(30)
        .mapIndexed { index, id -> PlannedTypingWord(id, if (index % 2 == 0) TypingDirection.BG_TO_RU else TypingDirection.RU_TO_BG) }
        .shuffled(random)
}
