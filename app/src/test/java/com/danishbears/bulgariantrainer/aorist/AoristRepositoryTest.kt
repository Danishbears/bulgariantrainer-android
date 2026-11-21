package com.danishbears.bulgariantrainer.aorist

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristRepositoryTest {
    private lateinit var context:Context;private lateinit var db:TrainerDatabase;private lateinit var repo:AoristRepository
    private val filename="aorist-${UUID.randomUUID()}.db"
    private var at=Instant.parse("2026-11-18T12:00:00Z")
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=this;override fun instant()=at}
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,filename).addMigrations(MIGRATION_9_10, MIGRATION_10_11).build();repo=AoristRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(filename)}
    private fun source()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.use{it.readBytes()}
    private fun root()=AoristContent.strict(source().toString(Charsets.UTF_8)).jsonObject
    private fun bytes(entries:List<JsonObject>,dataset:String="bg-aorist-core-40",revision:Int=1)=JsonObject(root()+mapOf("datasetId" to JsonPrimitive(dataset),"datasetRevision" to JsonPrimitive(revision),"entries" to JsonArray(entries))).toString().toByteArray()
    private suspend fun seed():AoristPreview{val p=repo.preview("original.json",source());assertTrue(p.issues.isEmpty());repo.confirm(p,p.changes.map{it.externalId}.toSet());return p}
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("must reject")}catch(_:AoristException){}}
    private suspend fun snapshot()=BackupRepository(context,db,clock).snapshot()
    private suspend fun forms()=db.aoristDao().allIds().associateWith{id->db.aoristDao().forms(id).associate{it.person to it.id}}

    @Test fun originalFortyPreviewIsReadOnlyAndRepeatedImportDoesNotDuplicateVerbsOrTargets()=runBlocking<Unit> {
        val p=repo.preview("original.json",source());assertEquals(40,p.changes.size);assertEquals(0L,repo.list().total)
        assertEquals(40,repo.confirm(p,p.changes.map{it.externalId}.toSet()).created)
        val ids=repo.list(limit=100).rows.map{it.id};val targets=forms()
        assertEquals(240,targets.values.sumOf{it.size});assertEquals(40,repo.list(limit=100).rows.map{it.entry["id"]}.distinct().size)
        val repeated=repo.preview("same.json",source());assertTrue(repeated.changes.all{it.kind==AoristChangeKind.UNCHANGED})
        assertEquals(40,repo.confirm(repeated,repeated.changes.map{it.externalId}.toSet()).unchanged)
        assertEquals(ids,repo.list(limit=100).rows.map{it.id});assertEquals(targets,forms());assertEquals(1L,repo.datasets().single().revision)
    }
    @Test fun exactContentRoundTripIntoFreshDatabaseRetainsSourcesMetadataAndAnswerOrder()=runBlocking<Unit> {
        seed();val output=repo.export("bg-aorist-core-40")
        assertEquals(AoristContent.canonical(root()),AoristContent.canonical(AoristContent.strict(output.toString(Charsets.UTF_8))))
        val second=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build()
        try{val other=AoristRepository(second,clock);val p=other.preview("roundtrip.json",output);other.confirm(p,p.changes.map{it.externalId}.toSet())
            assertArrayEquals(output,other.export("bg-aorist-core-40"));assertTrue(second.aspectPracticeDao().allIds().isEmpty())
        }finally{second.close()}
    }
    @Test fun selectedUpdatesPreserveVerbAndPersonIdsArchiveAndNonselectedContent()=runBlocking<Unit> {
        seed();val rows=repo.list(limit=100).rows;val targets=forms()
        val archived=repo.archive(rows[0].id,rows[0].version,true)
        val changed=rows.take(2).map{JsonObject(it.entry+("note" to JsonPrimitive("Changed explicitly")))}
        val p=repo.preview("update.json",bytes(changed,revision=2));assertTrue(p.metadataChanged);assertTrue(p.changes.all{it.kind==AoristChangeKind.UPDATE})
        assertEquals(rows[0].entry,repo.get(rows[0].id).entry)
        repo.confirm(p,setOf(AoristContent.text(rows[0].entry,"id")))
        val updated=repo.get(rows[0].id);assertTrue(updated.archived);assertEquals(changed[0],updated.entry)
        assertEquals(rows[1].entry,repo.get(rows[1].id).entry);assertEquals(targets,forms());assertEquals(2L,repo.datasets().single().revision)
        assertEquals(39L,repo.list().total);assertEquals(1L,repo.list(AoristFilter(archived=true)).total)
        assertEquals(39,AoristContent.parse(repo.export("bg-aorist-core-40")).entries.size)
        assertEquals(40,AoristContent.parse(repo.export("bg-aorist-core-40",true)).entries.size)
        repo.archive(updated.id,updated.version,false);assertEquals(40L,repo.list().total)
        rejected{repo.update(updated.id,archived.version,changed[0])}
    }
    @Test fun idsAreScopedByDatasetAndManualCreationPersistsStableUuidWithSearchAndPagination()=runBlocking<Unit> {
        seed();val original=repo.list(limit=100).rows.first()
        val p=repo.preview("other.json",bytes(listOf(original.entry),"another-dataset"));repo.confirm(p,setOf(AoristContent.text(original.entry,"id")))
        val other=repo.list(AoristFilter(datasetId="another-dataset")).rows.single();assertNotEquals(original.id,other.id)
        val manual=repo.create("my-aorist",JsonObject(original.entry-"id"));UUID.fromString(AoristContent.text(manual.entry,"id"))
        val exported=repo.export("my-aorist");val again=repo.preview("manual.json",exported);assertEquals(AoristChangeKind.UNCHANGED,again.changes.single().kind)
        assertEquals(25,repo.list().rows.size);assertEquals(17,repo.list(offset=25).rows.size)
        assertEquals(1L,repo.list(AoristFilter(datasetId="bg-aorist-core-40",query="ЧЕТОХ",aspect="imperfective",studyGroup="4_oh")).total)
        assertEquals(2L,repo.list(AoristFilter(datasetId="bg-aorist-core-40",reflexiveParticle="се")).total)
        assertEquals(0L,repo.list(AoristFilter(query="%_!")).total)
        val current=repo.get(manual.id);rejected{repo.create("my-aorist",current.entry)}
    }
    @Test fun stalePreviewAfterEditArchiveConcurrentInsertExpiryAndRestoreCannotWrite()=runBlocking<Unit> {
        seed();val first=repo.list().rows.first()
        val p=repo.preview("same.json",source());repo.update(first.id,first.version,JsonObject(first.entry+("note" to JsonPrimitive("edited"))))
        rejected{repo.confirm(p,setOf(AoristContent.text(first.entry,"id")))}
        val current=repo.get(first.id);val archivedPreview=repo.preview("same.json",source());repo.archive(first.id,current.version,true)
        rejected{repo.confirm(archivedPreview,setOf(AoristContent.text(first.entry,"id")))}
        val other=repo.preview("other.json",bytes(listOf(first.entry),"concurrent"));repo.create("concurrent",first.entry)
        rejected{repo.confirm(other,setOf(AoristContent.text(first.entry,"id")))}
        val expiring=repo.preview("same.json",source());at=at.plusSeconds(1800)
        rejected{repo.confirm(expiring,setOf(AoristContent.text(first.entry,"id")))}
        val fresh=repo.preview("same.json",source());val backup=BackupRepository(context,db,clock);backup.restore(backup.prepare(backup.snapshot()))
        rejected{repo.confirm(fresh,setOf(AoristContent.text(first.entry,"id")))}
    }
    @Test fun concurrentConfirmationAndReopenReplayHaveOneReceiptAndConflictsDoNotApply()=runBlocking<Unit> {
        val p=repo.preview("original.json",source());val selected=p.changes.map{it.externalId}.toSet()
        val results=coroutineScope{listOf(async{repo.confirm(p,selected)},async{repo.confirm(p,selected)}).awaitAll()}
        assertEquals(results[0],results[1]);assertEquals(1,db.aoristDao().receipts().size)
        db.close();open();assertEquals(results[0],repo.confirm(p,selected));assertEquals(40L,repo.list().total)
        rejected{repo.confirm(p,selected-firstId())}
    }
    private fun firstId()="bgaor-001"
    @Test fun wholeFileErrorsAndFutureFormatsCannotWriteEvenIfValidRowsAreSelected()=runBlocking<Unit> {
        seed();val before=snapshot();val r=root();val invalid=r.getValue("entries").jsonArray.toMutableList()
        invalid[39]=JsonObject(invalid[39].jsonObject+("forms" to buildJsonObject{put("1sg",JsonArray(emptyList()))}))
        for(b in listOf(JsonObject(r+("entries" to JsonArray(invalid))).toString().toByteArray(),
            JsonObject(r+("formatVersion" to JsonPrimitive(9))).toString().toByteArray())) {
            val p=repo.preview("invalid.json",b);assertTrue(p.issues.isNotEmpty());assertTrue(p.changes.isEmpty())
            rejected{repo.confirm(p,setOf(firstId()))};assertArrayEquals(before,snapshot())
        }
    }
    @Test fun lateInsertAndAnswerUpdateFailureRollBackEveryRowReceiptAndSequence()=runBlocking<Unit> {
        val before=snapshot();val sql=db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_aorist BEFORE INSERT ON aorist_verbs WHEN NEW.externalId='bgaor-002' BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val p=repo.preview("original.json",source())
        try{repo.confirm(p,p.changes.map{it.externalId}.toSet());fail("must roll back")}catch(_:android.database.sqlite.SQLiteException){}
        assertArrayEquals(before,snapshot());sql.execSQL("DROP TRIGGER fail_aorist");seed()
        val record=repo.list().rows.first();val stable=snapshot()
        sql.execSQL("CREATE TRIGGER fail_aorist_answers BEFORE INSERT ON aorist_answers WHEN NEW.position=2 BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val forms=record.entry.getValue("forms").jsonObject;val changed=JsonObject(record.entry+("forms" to JsonObject(forms+("3pl" to JsonArray(listOf(JsonPrimitive("гледаха"),JsonPrimitive("синтетичен вариант")))))))
        try{repo.update(record.id,record.version,changed);fail("must roll back all prior slot writes")}catch(_:android.database.sqlite.SQLiteException){}
        assertArrayEquals(stable,snapshot());assertEquals(record,repo.get(record.id))
    }
}
