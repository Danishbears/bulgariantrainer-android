package com.danishbears.bulgariantrainer.typing

import com.danishbears.bulgariantrainer.data.VocabularyText
import java.time.Instant
import com.danishbears.bulgariantrainer.review.*
import kotlin.random.Random

enum class TypingDirection { BG_TO_RU, RU_TO_BG }
enum class TypingStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class TypingError { EMPTY_DICTIONARY, EMPTY_REVIEW_QUEUE, EMPTY_NEW_QUEUE, SHORTER_SESSION_REQUIRED, INVALID_REQUEST, START_REQUEST_CHANGED,
    SESSION_NOT_FOUND, SESSION_CLOSED, STALE_QUESTION, ANSWER_CONFLICT, ANSWER_REQUIRED, RESULTS_UNAVAILABLE, STORAGE_FAILED }
class TypingException(val code: TypingError) : IllegalStateException(code.name)
data class TypingFeedback(val correct: Boolean, val submitted: String, val accepted: List<String>)
data class TypingQuestion(val id: Long, val position: Int, val direction: TypingDirection, val prompt: List<String>, val feedback: TypingFeedback?)
data class TypingMistake(val questionId: Long, val position: Int, val direction: TypingDirection,
    val prompt: List<String>, val submitted: String, val accepted: List<String>)
data class TypingState(val id: Long, val status: TypingStatus, val total: Int, val answered: Int, val correct: Int,
    val currentPosition: Int, val revision: Long, val question: TypingQuestion?, val mistakes: List<TypingMistake>,
    val scope: StudyScope = StudyScope.MIXED, val bgToRuQuestions: Int = 0, val ruToBgQuestions: Int = 0) {
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
/** Literal web TypingService.select greedy assignment and final shuffle.
 * Scoped direction eligibility can prevent 15/15; do not silently fill or repeat entries.
 */
internal class TypingPlanner(private val random: Random) {
    fun plan(ids: List<Long>, bg: Map<Long, LearningCategory> = emptyMap(), ru: Map<Long, LearningCategory> = emptyMap(),
        scope: StudyScope = StudyScope.MIXED): List<PlannedTypingWord> {
        val scheduler = ReviewScheduler()
        fun category(map: Map<Long, LearningCategory>, id: Long) = map[id] ?: LearningCategory.NEW
        val distinct = ids.distinct()
        val bgIds = distinct.filter { scheduler.eligible(category(bg, it), scope) }.shuffled(random).toMutableList()
        val ruIds = distinct.filter { scheduler.eligible(category(ru, it), scope) }.shuffled(random).toMutableList()
        val bgSet = bgIds.toSet(); val ruSet = ruIds.toSet()
        bgIds.sortWith(compareBy<Long> { scheduler.priority(category(bg, it)) }.thenBy { if (it in ruSet) 1 else 0 }
            .thenBy { if (category(ru, it) == LearningCategory.DUE) 1 else 0 })
        ruIds.sortWith(compareBy<Long> { scheduler.priority(category(ru, it)) }.thenBy { if (it in bgSet) 1 else 0 }
            .thenBy { if (category(bg, it) == LearningCategory.DUE) 1 else 0 })
        val used = mutableSetOf<Long>(); val result = mutableListOf<PlannedTypingWord>()
        var bgCount = 0; var ruCount = 0
        while (result.size < 30) {
            val preferBg = bgCount <= ruCount
            var direction = if (preferBg) TypingDirection.BG_TO_RU else TypingDirection.RU_TO_BG
            var id = (if (preferBg) bgIds else ruIds).firstOrNull { it !in used }
            if (id == null) {
                direction = if (preferBg) TypingDirection.RU_TO_BG else TypingDirection.BG_TO_RU
                id = (if (preferBg) ruIds else bgIds).firstOrNull { it !in used }
            }
            if (id == null) break
            used.add(id); result.add(PlannedTypingWord(id, direction))
            if (direction == TypingDirection.BG_TO_RU) bgCount++ else ruCount++
        }
        return result.shuffled(random)
    }
}
