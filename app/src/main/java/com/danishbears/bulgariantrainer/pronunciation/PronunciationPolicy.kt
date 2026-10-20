package com.danishbears.bulgariantrainer.pronunciation

import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.SentenceQuestion

/** Mirrors pronunciation.js visibility helpers. Never accepts raw learner input. */
object PronunciationPolicy {
    fun flashcard(sessionId: Long, question: FlashcardQuestion): PronunciationTarget? {
        val text = if (question.direction == CardDirection.BG_TO_RU) question.prompt.firstOrNull()
            else if (question.revealed) question.answer.firstOrNull() else null
        return target("flashcard-$sessionId-${question.id}", text)
    }
    fun typing(sessionId: Long, question: TypingQuestion): PronunciationTarget? {
        val text = if (question.direction == TypingDirection.BG_TO_RU) question.prompt.firstOrNull()
            else question.feedback?.accepted?.firstOrNull()
        return target("typing-$sessionId-${question.id}", text)
    }
    fun sentence(sessionId: Long, question: SentenceQuestion): List<PronunciationTarget> =
        question.feedback?.accepted.orEmpty().mapIndexedNotNull { index, text -> target("sentence-$sessionId-${question.id}-$index", text) }
    fun vocabulary(wordId: Long, bulgarian: String) = target("word-$wordId", bulgarian)
    private fun target(owner: String, text: String?) = text?.trim()?.takeIf { it.isNotEmpty() }?.let { PronunciationTarget(owner, it) }
}
