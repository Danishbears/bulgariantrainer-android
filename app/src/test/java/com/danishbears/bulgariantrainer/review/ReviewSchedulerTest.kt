package com.danishbears.bulgariantrainer.review

import com.danishbears.bulgariantrainer.typing.*
import java.time.*
import kotlin.random.Random
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReviewSchedulerTest {
    @Test fun identicalHistoriesMatchFixturesVerifiedByActualPinnedWebJavaScheduler() {
        val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("web_scheduler.json")!!.bufferedReader().use { it.readText() }).jsonObject
        val at = Instant.parse(fixture.getValue("anchor").jsonPrimitive.content)
        val scheduler = ReviewScheduler(Clock.fixed(at, ZoneOffset.UTC))
        assertEquals("de0a3e883d51561123ca54b7d5dd2e53757baf0a", fixture.getValue("referenceCommit").jsonPrimitive.content)
        for (element in fixture.getValue("cases").jsonArray) {
            val row = element.jsonObject; val history = row.getValue("history").jsonPrimitive.content
            var state: ReviewSchedule? = null
            for (result in history) state = scheduler.record(state, result == 'T')
            val actual = state ?: ReviewSchedule()
            assertEquals(row.getValue("attempts").jsonPrimitive.int, actual.attempts)
            assertEquals(row.getValue("successes").jsonPrimitive.int, actual.successes)
            assertEquals(row.getValue("streak").jsonPrimitive.int, actual.streak)
            assertEquals(row.getValue("intervalDays").jsonPrimitive.int, actual.intervalDays)
            assertEquals(row.getValue("nextReviewAt").jsonPrimitive.contentOrNull?.let(Instant::parse), actual.nextReview)
            assertEquals(row.getValue("category").jsonPrimitive.content, scheduler.category(state).name)
            actual.nextReview?.let {
                assertEquals(row.getValue("beforeDeadline").jsonPrimitive.content, scheduler.category(state, it.minusNanos(1)).name)
                assertEquals(row.getValue("atDeadline").jsonPrimitive.content, scheduler.category(state, it).name)
            }
        }
    }
    @Test fun duePrecedesMasteryAndNullScheduleOrZeroAttemptsRemainNew() {
        val at = Instant.parse("2026-10-10T12:00:00Z"); val scheduler = ReviewScheduler(Clock.fixed(at, ZoneOffset.UTC))
        assertEquals(LearningCategory.NEW, scheduler.category(null)); assertEquals(LearningCategory.NEW, scheduler.category(ReviewSchedule(nextReview = at)))
        assertEquals(LearningCategory.DUE, scheduler.category(ReviewSchedule(attempts = 1)))
        val mastered = ReviewSchedule(5, 5, 5, 28, at, at.plusSeconds(1))
        assertEquals(LearningCategory.MASTERED, scheduler.category(mastered))
        assertEquals(LearningCategory.DUE, scheduler.category(mastered, at.plusSeconds(1)))
        assertEquals(LearningCategory.LEARNING, scheduler.category(mastered.copy(streak = 4)))
        assertEquals(LearningCategory.LEARNING, scheduler.category(mastered.copy(intervalDays = 27)))
    }
    @Test fun configuredMasteryCriteriaAndEveryScopeFollowReferencePrecedence() {
        val at = Instant.parse("2026-10-10T12:00:00Z"); val scheduler = ReviewScheduler(Clock.fixed(at, ZoneOffset.UTC), 2, 3)
        assertEquals(LearningCategory.MASTERED, scheduler.category(ReviewSchedule(2, 2, 2, 3, at, at.plusSeconds(1))))
        for (category in LearningCategory.entries) {
            assertTrue(scheduler.eligible(category, StudyScope.MIXED))
            assertEquals(category == LearningCategory.DUE, scheduler.eligible(category, StudyScope.DUE))
            assertEquals(category == LearningCategory.NEW, scheduler.eligible(category, StudyScope.NEW))
        }
        assertEquals(listOf(0, 1, 2, 3), listOf(LearningCategory.DUE, LearningCategory.NEW, LearningCategory.LEARNING, LearningCategory.MASTERED).map(scheduler::priority))
        try { ReviewScheduler(masterySuccesses = 0); fail("Invalid config") } catch (_: IllegalArgumentException) { }
    }
    @Test fun exactDurationDaysIgnoreDeviceZoneAndDstTransitions() {
        for (instant in listOf("2026-03-28T12:30:00Z", "2026-10-24T12:30:00Z", "2028-02-28T23:59:59Z")) {
            val at = Instant.parse(instant)
            val berlin = ReviewScheduler(Clock.fixed(at, ZoneId.of("Europe/Berlin"))).record(null, true)
            val honolulu = ReviewScheduler(Clock.fixed(at, ZoneId.of("Pacific/Honolulu"))).record(null, true)
            assertEquals(berlin, honolulu); assertEquals(Duration.ofHours(24), Duration.between(at, berlin.nextReview))
        }
    }
    @Test fun flashcardsSelectDueThenNewWithoutRepeatsAndKeepScopesExact() {
        val ids = (1L..7L).toList(); val categories = mapOf(1L to LearningCategory.DUE, 2L to LearningCategory.DUE,
            3L to LearningCategory.NEW, 4L to LearningCategory.LEARNING, 5L to LearningCategory.MASTERED, 6L to LearningCategory.LEARNING, 7L to LearningCategory.NEW)
        assertEquals(setOf(1L,2L), ReviewSelection.flashcards(ids, categories, StudyScope.MIXED, 2, Random(2)).toSet())
        assertEquals(setOf(1L,2L,3L,7L), ReviewSelection.flashcards(ids, categories, StudyScope.MIXED, 4, Random(2)).toSet())
        assertEquals(setOf(3L,7L), ReviewSelection.flashcards(ids, categories, StudyScope.NEW, 100, Random(2)).toSet())
        assertEquals(setOf(1L,2L), ReviewSelection.flashcards(ids, categories, StudyScope.DUE, 100, Random(2)).toSet())
    }
    @Test fun sentencePriorityKeepsLinkedDueTieBreakWithinCategoryAndSeededOrder() {
        val ids = (1L..5L).toList(); val categories = mapOf(1L to LearningCategory.DUE, 2L to LearningCategory.NEW, 3L to LearningCategory.DUE)
        val result = ReviewSelection.sentences(ids, categories, StudyScope.MIXED, 5, Random(7), setOf(3))
        assertEquals(listOf(3L, 1L), result.take(2))
        assertEquals(result, ReviewSelection.sentences(ids, categories, StudyScope.MIXED, 5, Random(7), setOf(3)))
    }
    @Test fun typingScopedExclusiveDirectionsCannotBeSilentlyBalancedOrRepeated() {
        val ids = (1L..35L).toList(); val bg = ids.associateWith { LearningCategory.DUE }; val ru = ids.associateWith { LearningCategory.LEARNING }
        val plan = TypingPlanner(Random(8)).plan(ids, bg, ru, StudyScope.DUE)
        assertEquals(30, plan.size); assertEquals(30, plan.map { it.wordId }.toSet().size)
        assertTrue(plan.all { it.direction == TypingDirection.BG_TO_RU })
        assertTrue(TypingPlanner(Random(8)).plan(ids, bg, ru, StudyScope.NEW).isEmpty())
    }
    @Test fun typingPreservesWebGreedyTieBehaviorAndOppositeDueExclusivityRules() {
        // Pinned web select chooses BG first on a tie, even if all opposite RU reviews are due.
        val ids = listOf(1L); val plan = TypingPlanner(Random(1)).plan(ids,
            mapOf(1L to LearningCategory.NEW), mapOf(1L to LearningCategory.DUE), StudyScope.MIXED)
        assertEquals(TypingDirection.BG_TO_RU, plan.single().direction)
        val due = TypingPlanner(Random(1)).plan(ids, mapOf(1L to LearningCategory.NEW), mapOf(1L to LearningCategory.DUE), StudyScope.DUE)
        assertEquals(TypingDirection.RU_TO_BG, due.single().direction)
    }
}
