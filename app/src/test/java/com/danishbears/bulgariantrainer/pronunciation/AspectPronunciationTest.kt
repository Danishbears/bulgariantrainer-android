package com.danishbears.bulgariantrainer.pronunciation

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.aspect.*
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectPronunciationTest {
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectPracticeRepository
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrainerDatabase::class.java).build();repo=AspectPracticeRepository(db)}
    @After fun close(){db.close()}
    private suspend fun seed(id:String) {
        val root=Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.bufferedReader().use{it.readText()}).jsonObject
        val entry=root.getValue("entries").jsonArray.single{it.jsonObject["id"]==JsonPrimitive(id)}
        val content=AspectRepository(db);val preview=content.preview("fixture","fixture.json",JsonObject(root+("entries" to JsonArray(listOf(entry)))).toString().toByteArray())
        content.confirm(preview,setOf(id))
    }
    @Test fun sourceAudioCannotRevealTargetOrGradeAnUnansweredPair()=runBlocking<Unit>{
        seed("bgasp-057");val s=repo.start(UUID.randomUUID().toString(),AspectSettings(1,direction=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE));val q=s.question!!
        val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(this,{fake});speech.initialize();fake.initialized!!(true)
        try {
            assertTrue(PronunciationPolicy.aspectAnswers(s.id,q).isEmpty())
            val source=PronunciationPolicy.aspectSource(s.id,q)!!;speech.register(source)
            repeat(3){assertTrue(speech.speak(source.owner));assertTrue(speech.speak(source.owner,true))}
            assertTrue(fake.spoken.all{it.text=="купувам"});assertEquals(setOf(1f,0.5f),fake.spoken.map{it.rate}.toSet())
            assertEquals(s,repo.get(s.id));assertTrue(db.aspectPracticeDao().schedules().isEmpty());assertTrue(db.aspectPracticeDao().progressRows().isEmpty())
        }finally{speech.close()}
    }
    @Test fun reverseFeedbackPlaysApprovedAlternativesAndNeverTheIncorrectSubmission()=runBlocking<Unit>{
        seed("bgasp-043");val s=repo.start(UUID.randomUUID().toString(),AspectSettings(1,listOf(2),direction=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE));val q=s.question!!
        assertEquals("прочета",PronunciationPolicy.aspectSource(s.id,q)!!.text);assertTrue(PronunciationPolicy.aspectAnswers(s.id,q).isEmpty())
        val graded=repo.submit(s.id,q.id,"неверно");val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(this,{fake});speech.initialize();fake.initialized!!(true)
        try {
            val approved=PronunciationPolicy.aspectAnswers(s.id,graded.question!!);assertEquals(listOf("чета","прочитам"),approved.map{it.text})
            approved.forEach{speech.register(it);speech.speak(it.owner);speech.speak(it.owner,true)}
            assertTrue(fake.spoken.none{it.text=="неверно"});assertEquals(graded,repo.get(s.id));assertEquals(1,db.aspectPracticeDao().schedules().single().attempts)
        }finally{speech.close()}
    }
    @Test fun missingBulgarianVoiceDoesNotBlockPracticeOrChangeItsResult()=runBlocking<Unit>{
        seed("bgasp-057");val s=repo.start(UUID.randomUUID().toString(),AspectSettings(1,direction=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE));val q=s.question!!
        val fake=FakeTtsAdapter().apply{availability=LanguageAvailability.UNSUPPORTED;available=emptyList()};val speech=BulgarianPronunciation(this,{fake});speech.initialize();fake.initialized!!(true)
        try {
            val source=PronunciationPolicy.aspectSource(s.id,q)!!;speech.register(source);assertFalse(speech.speak(source.owner))
            assertEquals(1,repo.submit(s.id,q.id,"купя").correct);assertEquals(1,db.aspectPracticeDao().schedules().single().successes);assertTrue(fake.spoken.isEmpty())
        }finally{speech.close()}
    }
}
