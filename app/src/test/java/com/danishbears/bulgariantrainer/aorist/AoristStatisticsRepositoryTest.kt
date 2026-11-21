package com.danishbears.bulgariantrainer.aorist

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
import java.time.*
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristStatisticsRepositoryTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase
    private lateinit var content:AoristRepository;private lateinit var practice:AoristPracticeRepository;private lateinit var stats:AoristStatisticsRepository
    private val name="aorist-statistics-${UUID.randomUUID()}.db"
    private val now=Instant.parse("2026-11-21T12:00:00Z");private var at=now
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=this;override fun instant()=at}
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        content=AoristRepository(db,clock);practice=AoristPracticeRepository(db,clock,Random(8));stats=AoristStatisticsRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun request()=UUID.randomUUID().toString()
    private fun source()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.use{it.readBytes()}
    private suspend fun seed(dataset:String="test",all:Boolean=false) {
        val root=AoristContent.strict(source().toString(Charsets.UTF_8)).jsonObject
        val entries=root.getValue("entries").jsonArray.filter{all||it.jsonObject["lemma"]==JsonPrimitive("чета")}
        val bytes=JsonObject(root+mapOf("datasetId" to JsonPrimitive(dataset),"entries" to JsonArray(entries))).toString().toByteArray()
        val p=content.preview("fixture.json",bytes);content.confirm(p,p.changes.map{it.externalId}.toSet())
    }
    private suspend fun start(person:String="1sg",exercise:AoristExercise=AoristExercise.TYPED,dataset:String="test")=
        practice.start(request(),AoristSettings(size=1,persons=listOf(person),exercise=exercise,datasetId=dataset))
    private suspend fun answer(s:AoristPracticeState,correct:Boolean=true):AoristPracticeState {
        val q=s.question!!;val accepted=AoristContent.answers(content.get(q.verbId).entry,q.prompt.person).map(AoristContent::key)
        return if(q.exercise==AoristExercise.TYPED)practice.submitTyped(s.id,q.id,if(correct)accepted.first()else "неверный ответ")
        else practice.submitChoice(s.id,q.id,q.options.first{(AoristContent.key(it.text) in accepted)==correct}.id)
    }
    @Test fun emptyAndImportedContentHaveRealZerosWithSixPersonsTwoExercisesAndTwelveCells()=runBlocking<Unit>{
        val empty=stats.overview();assertEquals(AoristStatisticsCounts(),empty.counts)
        assertEquals(AoristContent.persons,empty.persons.keys.toList());assertEquals(AoristExercise.entries,empty.exercises.keys.toList());assertEquals(12,empty.cells.size)
        assertTrue(empty.cells.all{it.counts==AoristStatisticsCounts()})
        seed("bg-aorist-core-40",all=true)
        assertEquals(40,db.aoristDao().allIds().size);assertEquals(240,db.aoristDao().allIds().sumOf{db.aoristDao().forms(it).size})
        val ids=db.aoristDao().allIds();val forms=ids.flatMap{db.aoristDao().forms(it)}
        val p=content.preview("same.json",source());assertTrue(p.changes.all{it.kind==AoristChangeKind.UNCHANGED})
        content.confirm(p,p.changes.map{it.externalId}.toSet())
        assertEquals(ids,db.aoristDao().allIds());assertEquals(forms,ids.flatMap{db.aoristDao().forms(it)})
        assertEquals(empty.counts,stats.overview().counts);assertTrue(db.aoristPracticeDao().allIds().isEmpty())
    }
    @Test fun twelveTargetsAggregateByPersonAndTypeWithoutRevealsRetriesSkipsOrOtherModesBeingCountedAsAnswers()=runBlocking<Unit>{
        seed()
        for((i,person)in AoristContent.persons.withIndex())for(exercise in AoristExercise.entries){
            val s=start(person,exercise);val result=answer(s,i%2==0)
            if(exercise==AoristExercise.TYPED)practice.submitTyped(s.id,s.question!!.id,result.question!!.feedback!!.submitted!!)
            else practice.submitChoice(s.id,s.question!!.id,result.question!!.feedback!!.optionId!!)
            practice.next(s.id,s.question!!.id);practice.next(s.id,s.question.id)
        }
        val revealed=start();practice.reveal(revealed.id,revealed.question!!.id);practice.reveal(revealed.id,revealed.question.id)
        practice.complete(start("3pl").id)
        at=now.plus(Duration.ofDays(1))
        VocabularyRepository(db,clock).create(WordInput("дума",listOf(TranslationInput("слово"))))
        val flash=FlashcardRepository(db,clock);val f=flash.start(request(),FlashcardSettings(1));flash.reveal(f.id,f.question!!.id);flash.rate(f.id,f.question.id,true)
        val old=StatisticsRepository(db,clock).overview()
        val s=stats.overview("test");assertEquals(AoristStatisticsCounts(12,6,6,1,12),s.counts)
        for((i,person)in AoristContent.persons.withIndex()){
            val row=s.persons.getValue(person);assertEquals(2L,row.answered);assertEquals(if(i%2==0)2L else 0L,row.correct);assertEquals(2L,row.dueForReview)
        }
        for(exercise in AoristExercise.entries){val row=s.exercises.getValue(exercise);assertEquals(6L,row.answered);assertEquals(3L,row.correct);assertEquals(6L,row.dueForReview)}
        assertEquals(s.counts,s.cells.fold(AoristStatisticsCounts()){a,b->a+b.counts})
        seed("other");answer(start(dataset="other"));assertEquals(13L,stats.overview().counts.answered);assertEquals(1L,stats.overview("other").counts.answered)
        assertEquals(s,stats.overview("test"));assertEquals(AoristStatisticsCounts(),stats.overview("absent").counts)
        val verb=content.list(AoristFilter(datasetId="test")).rows.single()
        val edit=content.update(verb.id,verb.version,JsonObject(verb.entry+("note" to JsonPrimitive("edited"))))
        val archived=content.archive(edit.id,edit.version,true)
        assertEquals(s.counts.copy(dueForReview=0),stats.overview("test").counts)
        content.archive(archived.id,archived.version,false);assertEquals(s,stats.overview("test"))
        assertEquals(old,StatisticsRepository(db,clock).overview())
    }
    @Test fun dueUsesExactInstantBoundaryAndIndependentExerciseSchedulesEvenAcrossTimeZones()=runBlocking<Unit>{
        seed();answer(start())
        at=now.plus(Duration.ofHours(12));answer(start(exercise=AoristExercise.CHOICE))
        at=now.plus(Duration.ofDays(1)).minusMillis(1);assertEquals(0L,stats.overview().counts.dueForReview)
        at=now.plus(Duration.ofDays(1));val first=stats.overview();assertEquals(1L,first.counts.dueForReview)
        assertEquals(1L,first.exercises.getValue(AoristExercise.TYPED).dueForReview);assertEquals(0L,first.exercises.getValue(AoristExercise.CHOICE).dueForReview)
        val zoned=AoristStatisticsRepository(db,Clock.fixed(at,ZoneId.of("Europe/Sofia")))
        assertEquals(first,zoned.overview())
        at=now.plus(Duration.ofHours(36));assertEquals(2L,stats.overview().counts.dueForReview)
        at=now.minusMillis(1);assertEquals(0L,stats.overview().counts.dueForReview);assertEquals(2L,stats.overview().counts.answered)
    }
    @Test fun fullBackupReopenKeepsUnfinishedSnapshotsChoicesDraftsStatisticsAndStableIdentifiers()=runBlocking<Unit>{
        seed();var s=practice.start(request(),AoristSettings(size=2,persons=listOf("1sg","1pl"),datasetId="test"))
        s=answer(s);s=practice.next(s.id,s.question!!.id);practice.saveDraft(s.id,s.question!!.id,"черновик",null,1);s=practice.get(s.id)
        val choice=answer(start("2sg",AoristExercise.CHOICE),false)
        val revealed=start("3pl");practice.reveal(revealed.id,revealed.question!!.id)
        at=now.plus(Duration.ofDays(1));val before=stats.overview();val progress=db.aoristPracticeDao().progressRows();val schedules=db.aoristPracticeDao().schedules()
        val backup=BackupRepository(context,db,clock);val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        assertEquals(6,prepared.summary.aoristForms);assertEquals(6,prepared.summary.aoristAnswers);assertEquals(3,prepared.summary.aoristProgress);assertEquals(3,prepared.summary.aoristSchedules)
        answer(s);backup.restore(prepared);db.close();open()
        assertEquals(before,stats.overview());assertEquals(s,practice.get(s.id));assertEquals(choice,practice.get(choice.id))
        assertEquals(progress.toSet(),db.aoristPracticeDao().progressRows().toSet());assertEquals(schedules.toSet(),db.aoristPracticeDao().schedules().toSet())
        assertArrayEquals(bytes,BackupRepository(context,db,clock).snapshot())
        practice.submitChoice(choice.id,choice.question!!.id,choice.question!!.feedback!!.optionId!!)
        assertEquals(before,stats.overview())
    }
    @Test fun olderBackupPreviewNeverWritesAndExplicitReplacementClearsOnlyDataAbsentFromItsOriginalVersion()=runBlocking<Unit>{
        seed("test",all=true);answer(start());val backup=BackupRepository(context,db,clock)
        val bytes=backup.snapshot();val root=BackupFormat.decode(bytes);val before=stats.overview()
        for(version in 1..4){
            val tables=when(version){1->BackupFormat.legacyTables;2->BackupFormat.legacyTables+BackupFormat.aspectTables;3->BackupFormat.previousTables;else->BackupFormat.contentTables}
            val sequences=when(version){1->BackupFormat.legacySequences;2->BackupFormat.legacySequences+BackupFormat.aspectSequences;3->BackupFormat.previousSequences;else->BackupFormat.contentSequences}
            val original=JsonObject(root+mapOf("formatVersion" to JsonPrimitive(version),"schemaVersion" to JsonPrimitive(version+6),
                "tables" to JsonObject(root.getValue("tables").jsonObject.filterKeys{it in tables}),"sequences" to JsonObject(root.getValue("sequences").jsonObject.filterKeys{it in sequences})))
            val signed=JsonObject(original+("sha256" to JsonPrimitive(BackupFormat.digest(original)))).toString().toByteArray()
            val prepared=backup.prepare(signed);assertEquals(version,prepared.summary.sourceFormatVersion)
            assertEquals(if(version==4)240 else 0,prepared.summary.aoristForms)
            assertEquals(before,stats.overview());assertArrayEquals(bytes,backup.snapshot())
            backup.restore(prepared);assertEquals(AoristStatisticsCounts(),stats.overview().counts)
            assertEquals(if(version==4)40L else 0L,content.list().total);assertNull(practice.restore())
            backup.restore(backup.prepare(bytes));assertEquals(before,stats.overview())
        }
        val unsupported=JsonObject(root+("formatVersion" to JsonPrimitive(6))).toString().toByteArray()
        try{backup.prepare(unsupported);fail("future format")}catch(e:BackupException){assertEquals(BackupError.UNSUPPORTED_VERSION,e.code)}
        assertArrayEquals(bytes,backup.snapshot())
    }
    @Test fun statisticsAndWalSnapshotObserveOnlyACommittedGradingTransaction()=runBlocking<Unit>{
        seed();val s=start();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO){db.withTransaction{answer(s);entered.complete(Unit);release.await()}}
        entered.await();at=now.plus(Duration.ofDays(1))
        val reading=async(Dispatchers.IO){stats.overview()};val backup=BackupRepository(context,db,clock);val saving=async(Dispatchers.IO){backup.snapshot()}
        release.complete(Unit);writer.join();val value=reading.await()
        assertEquals(AoristStatisticsCounts(1,1,0,0,1),value.counts)
        val prepared=backup.prepare(saving.await());assertEquals(1,prepared.summary.aoristAttempts);assertEquals(1,prepared.summary.aoristSchedules)
        assertEquals("wal",db.openHelper.writableDatabase.query("PRAGMA journal_mode").use{it.moveToFirst();it.getString(0)})
        assertEquals(value,stats.overview());assertEquals(1,db.aoristPracticeDao().progressRows().single().attempts)
    }
}
