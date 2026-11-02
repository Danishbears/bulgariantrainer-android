package com.danishbears.bulgariantrainer.aspect

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
import java.time.*
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectStatisticsRepositoryTest {
    private lateinit var db:TrainerDatabase
    private lateinit var content:AspectRepository
    private lateinit var practice:AspectPracticeRepository
    private lateinit var stats:AspectStatisticsRepository
    private val now=Instant.parse("2026-11-02T12:00:00Z")
    private var at=now
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId):Clock=this;override fun instant()=at}
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build();content=AspectRepository(db,clock);practice=AspectPracticeRepository(db,clock,Random(4));stats=AspectStatisticsRepository(db,clock)}
    @After fun close(){db.close()}
    private fun request()=UUID.randomUUID().toString()
    private suspend fun seed(namespace:String="test",ids:Set<String> = setOf("bgasp-057","bgasp-043")) {
        val source=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.bufferedReader().use{it.readText()}).jsonObject
        val entries=source.getValue("entries").jsonArray.filter{it.jsonObject.getValue("id").jsonPrimitive.content in ids}
        val p=content.preview(namespace,"fixture.json",JsonObject(source+("entries" to JsonArray(entries))).toString().toByteArray());content.confirm(p,ids)
    }
    private suspend fun start(direction:AspectDirection,stage:Int=1,namespace:String="test")=practice.start(request(),AspectSettings(1,listOf(stage),direction=direction,namespace=namespace))
    @Test fun emptyModeHasRealZeroCountsAndExactlyTwoDirections()=runBlocking<Unit>{
        val s=stats.overview();assertEquals(0L,s.answered);assertEquals(0L,s.correct);assertEquals(0L,s.incorrect);assertEquals(0L,s.revealed);assertEquals(0L,s.dueForReview);assertEquals(0L,s.dueDirections)
        assertEquals(listOf(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE),s.directions.map{it.direction})
        assertTrue(db.aspectPracticeDao().allIds().isEmpty());assertTrue(db.aspectPracticeDao().progressRows().isEmpty())
    }
    /** Literal outcome/namespace/archive assertions from pinned web AspectPracticeIntegrationTests. */
    @Test fun pinnedWebStatisticsHistoryExcludesRevealsSkipsAndCountsADuePairOnlyOnce()=runBlocking<Unit>{
        VocabularyRepository(db,clock).create(WordInput("дума",listOf(TranslationInput("слово"))))
        val flash=FlashcardRepository(db,clock);val legacySession=flash.start(request(),FlashcardSettings(1));flash.reveal(legacySession.id,legacySession.question!!.id);flash.rate(legacySession.id,legacySession.question!!.id,true)
        val legacy=StatisticsRepository(db,clock).overview();assertEquals(1L,legacy.reviewAttempts)
        seed();at=now.minus(Duration.ofDays(1))
        val forward=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE);practice.submit(forward.id,forward.question!!.id,"купя");practice.submit(forward.id,forward.question!!.id,"купя")
        val reverse=start(AspectDirection.PERFECTIVE_TO_IMPERFECTIVE);practice.submit(reverse.id,reverse.question!!.id,"неверная форма")
        val reveal=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE);practice.reveal(reveal.id,reveal.question!!.id);practice.reveal(reveal.id,reveal.question!!.id)
        at=now
        val second=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,2);practice.submit(second.id,second.question!!.id,"прочета")
        val skip=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE);practice.complete(skip.id)
        seed("other",setOf("bgasp-057"));val other=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,namespace="other");practice.submit(other.id,other.question!!.id,"купя")
        val s=stats.overview();assertEquals(4L,s.answered);assertEquals(3L,s.correct);assertEquals(1L,s.incorrect);assertEquals(1L,s.revealed);assertEquals(1L,s.dueForReview);assertEquals(2L,s.dueDirections);assertEquals(now,s.asOf)
        val own=stats.overview("test");assertEquals(3L,own.answered);assertEquals(2L,own.directions[0].answered);assertEquals(1L,own.directions[1].incorrect)
        assertEquals(1L,stats.overview("other").answered);assertEquals(0L,stats.overview("other").dueForReview)
        val pair=content.list(AspectFilter(namespace="test",query="купувам")).rows.single();val archived=content.archive(pair.id,pair.version,true)
        val a=stats.overview();assertEquals(s.answered,a.answered);assertEquals(s.correct,a.correct);assertEquals(s.revealed,a.revealed);assertEquals(0L,a.dueForReview);assertEquals(0L,a.dueDirections)
        content.archive(archived.id,archived.version,false);assertEquals(s,stats.overview());assertEquals(legacy,StatisticsRepository(db,clock).overview())
    }
    @Test fun dueBoundaryUsesDeviceInstantsAndKeepsDirectionsIndependent()=runBlocking<Unit>{
        seed(ids=setOf("bgasp-057"));val f=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE);practice.submit(f.id,f.question!!.id,"купя")
        at=now.plus(Duration.ofHours(12));val r=start(AspectDirection.PERFECTIVE_TO_IMPERFECTIVE);practice.submit(r.id,r.question!!.id,"купувам")
        at=now.plus(Duration.ofDays(1)).minusMillis(1);assertEquals(0L,stats.overview().dueForReview)
        at=now.plus(Duration.ofDays(1));val first=stats.overview();assertEquals(1L,first.dueForReview);assertEquals(1L,first.dueDirections);assertEquals(0L,first.directions[1].dueForReview)
        at=now.plus(Duration.ofHours(36));assertEquals(1L,stats.overview().dueForReview);assertEquals(2L,stats.overview().dueDirections)
        at=now.minusSeconds(1);assertEquals(0L,stats.overview().dueForReview);assertEquals(2L,stats.overview().answered)
    }
    @Test fun unfinishedSessionsAndInvalidNamespaceDoNotFabricateOrDuplicateAttempts()=runBlocking<Unit>{
        seed(ids=setOf("bgasp-057"));val s=start(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)
        assertEquals(0L,stats.overview().answered);val result=practice.reveal(s.id,s.question!!.id)
        assertEquals(0L,stats.overview().answered);assertEquals(1L,stats.overview().revealed)
        try{practice.submit(s.id,s.question!!.id,"купя");fail("reveal must be terminal")}catch(_:AspectException){}
        repeat(3){practice.get(s.id);practice.reveal(s.id,s.question!!.id);stats.overview()}
        assertEquals(1,db.aspectPracticeDao().schedules().single().attempts);assertEquals(0L,stats.overview().correct)
        try{stats.overview("INVALID NAMESPACE");fail("invalid namespace")}catch(_:AspectException){}
        assertEquals(result,practice.get(s.id))
    }
}
