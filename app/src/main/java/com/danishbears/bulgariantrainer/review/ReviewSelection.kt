package com.danishbears.bulgariantrainer.review

import kotlin.random.Random

internal object ReviewSelection {
    private val scheduler = ReviewScheduler()
    fun flashcards(ids: List<Long>, categories: Map<Long, LearningCategory>, scope: StudyScope, size: Int, random: Random): List<Long> =
        ids.filter { scheduler.eligible(categories[it] ?: LearningCategory.NEW, scope) }.shuffled(random)
            .sortedBy { scheduler.priority(categories[it] ?: LearningCategory.NEW) }.take(size).sorted().shuffled(random)
    fun sentences(ids: List<Long>, categories: Map<Long, LearningCategory>, scope: StudyScope, size: Int, random: Random,
        linkedDue: Set<Long> = emptySet()): List<Long> = ids.filter { scheduler.eligible(categories[it] ?: LearningCategory.NEW, scope) }
        .shuffled(random).sortedWith(compareBy<Long> { scheduler.priority(categories[it] ?: LearningCategory.NEW) }
            .thenBy { if (it in linkedDue) 0 else 1 }).take(size)
}
