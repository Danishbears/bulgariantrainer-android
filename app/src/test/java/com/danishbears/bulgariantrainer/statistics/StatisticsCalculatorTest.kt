package com.danishbears.bulgariantrainer.statistics

import com.danishbears.bulgariantrainer.review.*
import java.time.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StatisticsCalculatorTest {
    private val now = Instant.parse("2026-10-10T12:30:00Z")
    private fun attempt(id: Long, mode: String, result: String, grading: String, at: Instant = now, word: Long? = null, direction: String? = null) =
        StatisticsAttempt(id, mode, if (mode == "SENTENCE") 3 else if (mode == "TYPING") 2 else 1, word, direction, "answer", result, grading, at.toEpochMilli())
    private fun empty(attempts: List<StatisticsAttempt> = emptyList()) = StatisticsInput(emptyList(), emptySet(), emptySet(), emptyMap(), emptyMap(), attempts, emptyList())
    @Test fun matchesAllTwentyLiteralMetricsFromPinnedWebIntegrationFixture() {
        val raw = Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("web_statistics.json")!!.bufferedReader().use { it.readText() }).jsonObject
        val at = Instant.parse(raw.getValue("anchor").jsonPrimitive.content)
        val mastered = ReviewSchedule(5, 5, 5, 28, at, at.plus(Duration.ofDays(28)))
        val dueToday = ReviewSchedule(1, 1, 1, 1, at.minus(Duration.ofHours(25)), at.minus(Duration.ofHours(1)))
        val schedules = mapOf((1L to ReviewMode.FLASHCARD) to dueToday) + listOf(ReviewMode.FLASHCARD, ReviewMode.TYPING_BG_TO_RU, ReviewMode.TYPING_RU_TO_BG).associate { (3L to it) to mastered }
        val history = listOf(attempt(1,"FLASHCARD","CORRECT","SELF_ASSESSED",word=1,direction="BG_TO_RU"),
            attempt(2,"TYPING","INCORRECT","AUTOMATIC",at.minus(Duration.ofDays(1)),1,"BG_TO_RU"),
            attempt(3,"TYPING","CORRECT","AUTOMATIC",word=1,direction="RU_TO_BG"),
            attempt(4,"SENTENCE","CORRECT","AUTOMATIC"),attempt(5,"SENTENCE","PARTIAL","SELF_ASSESSED"),attempt(6,"SENTENCE","PENDING","UNCLASSIFIED"))
        val input = StatisticsInput(listOf(StatisticsWord(1,"куче",false,listOf("собака")),StatisticsWord(2,"котка",false,listOf("кошка")),
            StatisticsWord(3,"дом",false,listOf("дом")),StatisticsWord(4,"архив",true,listOf("архив"))),setOf(1),setOf(1,3),schedules,mapOf(1L to dueToday),history,
            listOf(StatisticsSession(1,"FLASHCARD","COMPLETED",at.toEpochMilli(),at.toEpochMilli(),1),StatisticsSession(2,"TYPING","COMPLETED",at.toEpochMilli(),at.toEpochMilli(),2),
                StatisticsSession(3,"SENTENCE","ABANDONED",at.toEpochMilli(),at.toEpochMilli(),3)))
        val s = StatisticsCalculator.calculate(input, at)
        val actual = mapOf("/vocabulary/total" to s.vocabulary.total,"/vocabulary/active" to s.vocabulary.active,"/vocabulary/archived" to s.vocabulary.archived,
            "/vocabulary/newItems" to s.vocabulary.newItems,"/vocabulary/dueItems" to s.vocabulary.dueItems,"/vocabulary/masteredItems" to s.vocabulary.masteredItems,
            "/reviewsScheduledToday" to s.reviewsScheduledToday,"/completedSessions" to s.completedSessions,"/reviewAttempts" to s.reviewAttempts,
            "/overall/total/correct" to s.overall.total.correct,"/overall/total/partial" to s.overall.total.partial,"/overall/total/accuracyPercent" to s.overall.total.accuracyPercent,
            "/overall/automatic/attempts" to s.overall.automatic.attempts,"/overall/selfAssessed/attempts" to s.overall.selfAssessed.attempts,
            "/flashcards/total/accuracyPercent" to s.flashcards.total.accuracyPercent,"/typingRuToBg/total/accuracyPercent" to s.typingRuToBg.total.accuracyPercent,
            "/sentences/total/accuracyPercent" to s.sentences.total.accuracyPercent,"/consecutiveStudyDays" to s.consecutiveStudyDays,
            "/last7Days/5/reviewAttempts" to s.last7Days[5].reviewAttempts,"/last7Days/6/reviewAttempts" to s.last7Days[6].reviewAttempts)
        assertEquals(actual.keys,raw.getValue("metrics").jsonObject.keys)
        raw.getValue("metrics").jsonObject.forEach { (path,value) -> assertEquals(path,value.jsonPrimitive.double,actual.getValue(path)!!.toDouble(),0.0) }
        assertEquals(0.0,s.typingBgToRu.total.accuracyPercent!!,0.0); assertEquals(1,s.difficultWords.size); assertEquals(3,s.recentSessions.size)
    }
    @Test fun emptyAccuracyIsUndefinedAndDailySeriesExplicitlyContainZeroDays() {
        val s = StatisticsCalculator.calculate(empty(),now)
        assertNull(s.overall.total.accuracyPercent); assertEquals(0L,s.completedSessions); assertEquals(0L,s.reviewAttempts)
        assertEquals(7,s.last7Days.size); assertEquals(30,s.last30Days.size); assertTrue(s.last30Days.all { it.reviewAttempts==0L })
    }
    @Test fun utcBoundariesLeapDaysAndStreakEndingYesterdayIgnoreDeviceZone() {
        val at = Instant.parse("2028-03-01T00:00:00Z")
        val history = listOf(attempt(1,"TYPING","CORRECT","AUTOMATIC",at.minusNanos(1)),attempt(2,"TYPING","CORRECT","AUTOMATIC",at.minus(Duration.ofDays(1)).minusNanos(1)))
        val s = StatisticsCalculator.calculate(empty(history),at)
        assertEquals(2L,s.consecutiveStudyDays); assertEquals(LocalDate.parse("2028-02-29"),s.last7Days[5].date)
        assertEquals(0L,s.last7Days.last().reviewAttempts); assertEquals(1L,s.last7Days[5].reviewAttempts)
        assertEquals(LocalDate.parse("2028-03-01"),StatisticsCalculator.utcDate(at))
        assertEquals(0L,StatisticsCalculator.calculate(empty(history),at.plus(Duration.ofDays(1))).consecutiveStudyDays)
    }
    @Test fun accuracyRoundsLikeJavaAndPendingIsNeverFinalized() {
        val rows=listOf(attempt(1,"SENTENCE","CORRECT","SELF_ASSESSED"),attempt(2,"SENTENCE","PARTIAL","SELF_ASSESSED"),attempt(3,"SENTENCE","INCORRECT","SELF_ASSESSED"))
        assertEquals(33.3,StatisticsCalculator.rate(rows).accuracyPercent!!,0.0)
        assertFalse(StatisticsCalculator.finalized(attempt(4,"SENTENCE","PENDING","UNCLASSIFIED")))
        assertFalse(StatisticsCalculator.finalized(attempt(5,"SENTENCE","SKIPPED","UNCLASSIFIED")))
    }
    @Test fun difficultWordsIncludeArchiveUseCurrentTranslationsAndHaveReferenceLimitAndRanking() {
        val words=(1L..22L).map { StatisticsWord(it,"дума$it",it==22L,listOf("новый перевод $it")) }
        val rows=words.map { attempt(it.id,"TYPING","INCORRECT","AUTOMATIC",word=it.id) }+attempt(99,"FLASHCARD","INCORRECT","SELF_ASSESSED",word=22)
        val s=StatisticsCalculator.calculate(empty(rows).copy(words=words),now)
        assertEquals(20,s.difficultWords.size); assertEquals(22L,s.difficultWords.first().wordId)
        assertTrue(s.difficultWords.first().archived); assertEquals(listOf("новый перевод 22"),s.difficultWords.first().translations)
    }
    @Test fun overdueBeforeTodayIsDueButNotScheduledTodayAndMasteryRequiresAllWordModes() {
        val old=ReviewSchedule(1,1,1,1,now.minus(Duration.ofDays(3)),now.minus(Duration.ofDays(2)))
        val mastered=ReviewSchedule(5,5,5,28,now,now.plus(Duration.ofDays(28)))
        val base=empty().copy(words=listOf(StatisticsWord(1,"дом",false,listOf("дом"))),studiedWords=setOf(1),wordSchedules=mapOf((1L to ReviewMode.FLASHCARD) to old))
        val s=StatisticsCalculator.calculate(base,now);assertEquals(1L,s.vocabulary.dueItems);assertEquals(0L,s.reviewsScheduledToday)
        assertEquals(0L,StatisticsCalculator.calculate(base.copy(wordSchedules=mapOf((1L to ReviewMode.FLASHCARD) to mastered)),now).vocabulary.masteredItems)
        val all=listOf(ReviewMode.FLASHCARD,ReviewMode.TYPING_BG_TO_RU,ReviewMode.TYPING_RU_TO_BG).associate { (1L to it) to mastered }
        assertEquals(1L,StatisticsCalculator.calculate(base.copy(wordSchedules=all),now).vocabulary.masteredItems)
    }
}
