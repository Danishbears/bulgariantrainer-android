package com.danishbears.bulgariantrainer.sentences

import com.danishbears.bulgariantrainer.data.VocabularyText
import java.time.Instant

data class SentenceInput(val russian: String, val bulgarian: String, val alternatives: List<String> = emptyList(),
    val explanation: String? = null, val difficulty: Int? = null, val topic: String? = null)
data class SentenceRecord(val id: Long, val input: SentenceInput, val archived: Boolean, val approved: Boolean,
    val sourceId: String, val sourceFilename: String, val sourceRow: Int)
enum class SentenceError { INVALID_SENTENCE, DUPLICATE_SENTENCE, SENTENCE_NOT_FOUND, INVALID_SETTINGS, EMPTY_SENTENCES,
    INVALID_ANSWER, SESSION_NOT_FOUND, SESSION_CLOSED, STALE_QUESTION, ANSWER_REQUIRED, EVALUATION_REQUIRED,
    ANSWER_CONFLICT, EVALUATION_CONFLICT, START_REQUEST_CHANGED, RESULTS_UNAVAILABLE, STORAGE_FAILED }
class SentenceException(val code: SentenceError) : IllegalStateException(code.name)
enum class SentenceStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class SentenceRating { CORRECT, PARTIAL, INCORRECT }
enum class SentenceResult { CORRECT, PARTIAL, INCORRECT, PENDING, SKIPPED }
enum class SentenceEvaluation { AUTOMATIC, SELF }
data class SentenceFeedback(val submitted: String, val automaticMatch: Boolean, val selfRating: SentenceRating?,
    val accepted: List<String>, val explanation: String?) {
    val result get() = if (automaticMatch) SentenceResult.CORRECT else selfRating?.let { SentenceResult.valueOf(it.name) } ?: SentenceResult.PENDING
    val evaluation get() = if (automaticMatch) SentenceEvaluation.AUTOMATIC else selfRating?.let { SentenceEvaluation.SELF }
    val needsSelfAssessment get() = !automaticMatch && selfRating == null
}
data class SentenceQuestion(val id: Long, val position: Int, val russian: String, val feedback: SentenceFeedback?)
data class SentenceReview(val questionId: Long, val position: Int, val russian: String, val feedback: SentenceFeedback?,
    val accepted: List<String>, val explanation: String?)
data class SentencePracticeState(val id: Long, val status: SentenceStatus, val requestedSize: Int, val total: Int,
    val submitted: Int, val evaluated: Int, val correct: Int, val partial: Int, val incorrect: Int, val pending: Int,
    val currentPosition: Int, val revision: Long, val question: SentenceQuestion?, val review: List<SentenceReview>) {
    val active get() = status == SentenceStatus.IN_PROGRESS
    val unanswered get() = total - submitted
}
data class SentenceProgress(val sentenceId: Long, val attempts: Int, val correct: Int, val partial: Int,
    val incorrect: Int, val consecutiveCorrect: Int, val lastEvaluatedAt: Instant)

internal object SentenceValidation {
    fun normalize(input: SentenceInput): SentenceInput {
        fun required(value: String, max: Int): String {
            val cleaned = VocabularyText.clean(value)
            if (value.length > max || cleaned.isEmpty()) throw SentenceException(SentenceError.INVALID_SENTENCE)
            return cleaned
        }
        fun optional(value: String?, max: Int): String? {
            if (value != null && value.length > max) throw SentenceException(SentenceError.INVALID_SENTENCE)
            return VocabularyText.optional(value)
        }
        if (input.difficulty != null && input.difficulty !in 1..5 || input.alternatives.size > 50)
            throw SentenceException(SentenceError.INVALID_SENTENCE)
        val russian = required(input.russian, 4000); val bulgarian = required(input.bulgarian, 4000)
        val keys = hashSetOf(VocabularyText.key(bulgarian))
        val alternatives = input.alternatives.map { required(it, 4000) }
        if (alternatives.any { !keys.add(VocabularyText.key(it)) }) throw SentenceException(SentenceError.INVALID_SENTENCE)
        return SentenceInput(russian, bulgarian, alternatives, optional(input.explanation, 4000), input.difficulty, optional(input.topic, 255))
    }
}
