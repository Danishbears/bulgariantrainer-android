package com.danishbears.bulgariantrainer.aspect

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.*
import com.danishbears.bulgariantrainer.statistics.StatisticsRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AspectRepositoryTest{
    private lateinit var context:Context
    private lateinit var db:TrainerDatabase
    private lateinit var repo:AspectRepository
    private val name="aspects-"+UUID.randomUUID()+".db"
    private val clock=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"),ZoneOffset.UTC)
    private val ns="bulgarian-aspect-pairs-100"
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();open()}
    private fun open(){db=Room.databaseBuilder(context,TrainerDatabase::class.java,name).addMigrations(MIGRATION_7_8, MIGRATION_8_9).build();repo=AspectRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun bytes()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.use{it.readBytes()}
    private fun entry()=AspectContent.parse(bytes()).entries.first()
    private fun added(id:String=UUID.randomUUID().toString())=JsonObject(entry()+mapOf("id" to JsonPrimitive(id),"provenance" to buildJsonObject{put("kind","added")}))
    private fun json(e:JsonObject)=AspectContent.document(buildJsonObject{put("format",AspectContent.FORMAT);put("formatVersion",1)},listOf(e)).toString().toByteArray()
    private suspend fun imported():AspectPreview{val p=repo.preview(ns,"fixture.json",bytes());assertTrue(p.issues.isEmpty());repo.confirm(p,p.changes.map{it.externalId}.toSet());return p}
    private suspend fun snapshot()=BackupFormat.decode(BackupRepository(context,db,clock).snapshot())
    private suspend fun legacyRows()=snapshot().getValue("tables").jsonObject.filterKeys{it in BackupFormat.legacyTables}
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("expected rejection")}catch(_:AspectException){}}
    private suspend fun seedLegacy(){
        repeat(2){VocabularyRepository(db,clock).create(WordInput("дума$it",listOf(TranslationInput("слово$it"))))}
        val flash=FlashcardRepository(db,clock);val f=flash.start(UUID.randomUUID().toString(),FlashcardSettings(1));flash.reveal(f.id,f.question!!.id);flash.rate(f.id,f.question!!.id,true)
        val typing=TypingRepository(db,clock);val t=typing.start(UUID.randomUUID().toString(),true);typing.submit(t.id,t.question!!.id,"Ошибка")
        SentenceRepository(db,clock).create(SentenceInput("Я здесь","Тук съм",difficulty=1))
        val sentence=SentencePracticeRepository(db,clock);val s=sentence.start(UUID.randomUUID().toString(),1);sentence.submit(s.id,s.question!!.id,"Другой вариант");sentence.selfRate(s.id,s.question!!.id,SentenceRating.PARTIAL)
        TrainerPreferencesRepository(db).flashcardSize(7)
    }
    @Test fun actualDatasetRoundTripsAllFieldsAndReimportIsIdempotent()=runBlocking<Unit>{
        val p=repo.preview(ns,"fixture.json",bytes());assertEquals(100,p.changes.size);assertTrue(p.issues.isEmpty());assertEquals(0L,repo.list().total)
        assertEquals(100,repo.confirm(p,p.changes.map{it.externalId}.toSet()).created)
        val output=repo.export(ns)
        assertEquals(Json.parseToJsonElement(bytes().toString(Charsets.UTF_8)),Json.parseToJsonElement(output.toString(Charsets.UTF_8)))
        val repeat=repo.preview(ns,"again.json",output);assertTrue(repeat.changes.all{it.kind=="UNCHANGED"})
        assertEquals(100,repo.confirm(repeat,repeat.changes.map{it.externalId}.toSet()).unchanged)
        assertEquals(100L,repo.list().total);assertEquals(AspectContent.hash(output),repo.datasets().single().revision)
    }
    @Test fun selectionUpdatesArchiveAndEditsRetainIdentityAndEveryLegacyRecord()=runBlocking<Unit>{
        seedLegacy();val before=legacyRows();val stats=StatisticsRepository(db,clock).overview();imported()
        val record=repo.list().rows.first();val archived=repo.archive(record.id,record.version,true)
        val updated=JsonObject(archived.entry+("note" to JsonPrimitive("Исправлено")))
        val root=Json.parseToJsonElement(bytes().toString(Charsets.UTF_8)).jsonObject
        val document=JsonObject(root+("entries" to JsonArray(listOf(updated)))).toString().toByteArray()
        val preview=repo.preview(ns,"update.json",document);assertEquals("UPDATE",preview.changes.single().kind)
        assertEquals(record.entry,repo.get(record.id).entry)
        repo.confirm(preview,setOf(record.entry.getValue("id").jsonPrimitive.content))
        assertEquals(record.id,repo.list(AspectFilter(archived=true)).rows.single().id);assertTrue(repo.get(record.id).archived)
        assertEquals(updated,repo.get(record.id).entry);assertEquals(100L,repo.list().total+repo.list(AspectFilter(archived=true)).total)
        assertEquals(before,legacyRows());assertEquals(stats,StatisticsRepository(db,clock).overview())
        val current=repo.get(record.id);repo.archive(current.id,current.version,false);assertEquals(100L,repo.list().total)
    }
    @Test fun previewIsStaleAfterEditArchiveOrConcurrentIdentityCreation()=runBlocking<Unit>{
        val e=added("same")
        val first=repo.create("mine",e);val p=repo.preview("mine","file.json",json(JsonObject(e+("note" to JsonPrimitive("new")))))
        repo.archive(first.id,first.version,true)
        rejected{repo.confirm(p,setOf("same"))};assertTrue(repo.get(first.id).archived)
        val new=repo.preview("other","new.json",json(added("new")));repo.create("other",added("new"))
        rejected{repo.confirm(new,setOf("new"))}
    }
    @Test fun receiptSurvivesReopenAndChangedConfirmationIsRejected()=runBlocking<Unit>{
        val p=imported();val selection=p.changes.map{it.externalId}.toSet()
        db.close();open()
        assertEquals(100,repo.confirm(p,selection).created)
        rejected{repo.confirm(p,selection.drop(1).toSet())}
        assertEquals(100L,repo.list().total)
    }
    @Test fun malformedOrUnsupportedWholeDocumentCannotWriteEvenWithSelectedIds()=runBlocking<Unit>{
        imported();val before=snapshot()
        val root=Json.parseToJsonElement(bytes().toString(Charsets.UTF_8)).jsonObject
        val badEntries=listOf(
            JsonObject(added()+("acceptedPerfective" to JsonArray(emptyList()))),
            JsonObject(added()+("studyStage" to JsonPrimitive(4))),
            JsonObject(added()+("examples" to JsonPrimitive("bad"))),
            JsonObject(added()+("relationType" to JsonPrimitive("unknown")))
        )
        val variants=listOf(
            JsonObject(root+("formatVersion" to JsonPrimitive(2))),
            JsonObject(root+("format" to JsonPrimitive("wrong"))),
            JsonObject(root+("entries" to JsonArray(listOf(added("duplicate"),added("duplicate")))))
        ) + badEntries.map { JsonObject(root+("entries" to JsonArray(listOf(it)))) }
        for(value in variants){val p=repo.preview(ns,"bad.json",value.toString().toByteArray());assertTrue(p.issues.isNotEmpty());rejected{repo.confirm(p,setOf("bgasp-001"))};assertEquals(before,snapshot())}
        for(raw in listOf("{","{} trailing","{\"format\":1,\"format\":1}"))rejected{repo.preview(ns,"bad.json",raw.toByteArray())}
        rejected{repo.preview(ns,"bad.json",byteArrayOf(0xd0.toByte()))}
        assertEquals(before,snapshot())
    }
    @Test fun failureOnSecondInsertRollsBackPairsDatasetAnswersAndReceipt()=runBlocking<Unit>{
        val root=AspectContent.document(buildJsonObject{put("format",AspectContent.FORMAT);put("formatVersion",1)},listOf(added("first"),added("second")))
        val p=repo.preview("rollback","two.json",root.toString().toByteArray());val before=snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_second BEFORE INSERT ON aspect_pairs WHEN NEW.externalId='second' BEGIN SELECT RAISE(ABORT,'fixture'); END")
        try{repo.confirm(p,setOf("first","second"));fail("expected SQL rollback")}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot());assertTrue(repo.datasets().isEmpty())
    }
    @Test fun concurrentConfirmationsCommitOneContentWriteAndOneReceipt()=runBlocking<Unit>{
        val p=repo.preview("concurrent","one.json",json(added("one")))
        val results=coroutineScope { listOf(async(Dispatchers.Default){repo.confirm(p,setOf("one"))},async(Dispatchers.Default){repo.confirm(p,setOf("one"))}).awaitAll() }
        assertEquals(results[0],results[1]);assertEquals(1L,repo.list().total)
        assertEquals(1,db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM aspect_receipts").use{it.moveToFirst();it.getInt(0)})
    }
    @Test fun sourcesFromOmittedOlderEntriesRemainExportableAfterLaterImports()=runBlocking<Unit>{
        val root=Json.parseToJsonElement(bytes().toString(Charsets.UTF_8)).jsonObject
        val old=JsonObject(root+("entries" to JsonArray(listOf(entry()))))
        val p=repo.preview("sources","a.json",old.toString().toByteArray());repo.confirm(p,setOf("bgasp-001"))
        val nextDocument=AspectContent.document(buildJsonObject{
            put("format",AspectContent.FORMAT);put("formatVersion",1)
            put("sources",buildJsonArray{add(buildJsonObject{put("id","unused-source");put("title","Новый источник")})})
        },listOf(added("added")))
        val next=repo.preview("sources","b.json",nextDocument.toString().toByteArray());repo.confirm(next,setOf("added"))
        val export=AspectContent.parse(repo.export("sources"));assertTrue(export.issues.isEmpty());assertEquals(2,export.entries.size)
        assertTrue(export.metadata.getValue("sources").jsonArray.any{it.jsonObject["id"]==JsonPrimitive("lesson13")})
        assertTrue(export.metadata.getValue("sources").jsonArray.any{it.jsonObject["id"]==JsonPrimitive("unused-source")})
    }
    @Test fun namespaceFiltersPaginationStableManualIdsAndSearchAreIndependent()=runBlocking<Unit>{
        for(i in 0..30)repo.create("mine",added("manual-$i"))
        repo.create("other",added("manual-0"))
        val first=repo.list(AspectFilter(namespace="mine"),0,25);val second=repo.list(AspectFilter(namespace="mine"),25,25)
        assertEquals(31L,first.total);assertEquals(6,second.rows.size);assertTrue(first.rows.map{it.id}.intersect(second.rows.map{it.id}.toSet()).isEmpty())
        assertEquals(31L,repo.list(AspectFilter(namespace="mine",query="взимам",stage=1,topic="Быт",relation="core")).total)
        assertEquals(0L,repo.list(AspectFilter(query="%_!")).total)
        val edited=repo.update(first.rows.first().id,first.rows.first().version,JsonObject(first.rows.first().entry+("note" to JsonPrimitive("note"))))
        assertEquals(first.rows.first().entry["id"],edited.entry["id"])
        rejected{repo.create("mine",added("manual-0"))}
    }
    @Test fun newFullBackupRoundTripIncludesContentAndOldV1RestoreAuthenticatesBeforeReplacement()=runBlocking<Unit>{
        seedLegacy();imported();val backup=BackupRepository(context,db,clock);val bytes=backup.snapshot();val prepared=backup.prepare(bytes)
        assertEquals(100,prepared.summary.aspectPairs);assertEquals(1,prepared.summary.aspectDatasets)
        repo.create("mine",added());backup.restore(prepared);assertArrayEquals(bytes,backup.snapshot())
        val current=BackupFormat.decode(bytes)
        val tables=current.getValue("tables").jsonObject
        val receipts=tables.getValue("aspect_receipts").jsonArray.toMutableList()
        receipts[0]=JsonObject(receipts[0].jsonObject+("summary" to JsonPrimitive("{\"created\":-1,\"updated\":0,\"unchanged\":0,\"deselected\":0}")))
        val corrupt=JsonObject(current+("tables" to JsonObject(tables+("aspect_receipts" to JsonArray(receipts)))))
        val signedCorrupt=JsonObject(corrupt+("sha256" to JsonPrimitive(BackupFormat.digest(corrupt)))).toString().toByteArray()
        try{backup.prepare(signedCorrupt);fail("invalid receipt accepted")}catch(e:BackupException){assertEquals(BackupError.INVALID_DATA,e.code)}
        assertArrayEquals(bytes,backup.snapshot())
        val legacy=JsonObject(current+mapOf("formatVersion" to JsonPrimitive(1),"schemaVersion" to JsonPrimitive(7),
            "tables" to JsonObject(current.getValue("tables").jsonObject.filterKeys{it in BackupFormat.legacyTables}),
            "sequences" to JsonObject(current.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.legacySequences})))
        val signed=JsonObject(legacy+("sha256" to JsonPrimitive(BackupFormat.digest(legacy)))).toString().toByteArray()
        val old=backup.prepare(signed);assertEquals(0,old.summary.aspectPairs)
        val bad=JsonObject(legacy+("sha256" to JsonPrimitive("invalid"))).toString().toByteArray()
        try{backup.prepare(bad);fail("bad v1 checksum accepted")}catch(e:BackupException){assertEquals(BackupError.CHECKSUM_MISMATCH,e.code)}
        assertArrayEquals(bytes,backup.snapshot())
        backup.restore(old);assertEquals(0L,repo.list().total)
        assertEquals(current.getValue("tables").jsonObject.filterKeys{it in BackupFormat.legacyTables},legacyRows())
    }
    @Test fun exportedSchema7MigrationRetainsAllLegacyRowsHighWaterAndResumableSessions()=runBlocking<Unit>{
        seedLegacy();val beforeSnapshot=snapshot();db.close();context.deleteDatabase(name)
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use{sql->
            val schema=javaClass.classLoader!!.getResourceAsStream("com.danishbears.bulgariantrainer.data.TrainerDatabase/7.json")!!.bufferedReader().use{it.readText()}
            for(value in Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray){
                val e=value.jsonObject;val table=e.getValue("tableName").jsonPrimitive.content
                sql.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                for(index in e["indices"]?.jsonArray.orEmpty())sql.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
            }
            for(table in BackupFormat.legacyTables)for(value in beforeSnapshot.getValue("tables").jsonObject.getValue(table).jsonArray){
                val row=value.jsonObject
                val columns=row.keys.toList()
                val bind=columns.map{k->val v=row.getValue(k);if(v==JsonNull)null else if(v.jsonPrimitive.isString)v.jsonPrimitive.content else v.jsonPrimitive.long}.toTypedArray()
                sql.execSQL("INSERT INTO `"+table+"` ("+columns.joinToString(","){"`"+it+"`"}+") VALUES ("+columns.joinToString(","){"?"}+")",bind)
            }
            for((table,value) in beforeSnapshot.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.legacySequences}){
                sql.execSQL("DELETE FROM sqlite_sequence WHERE name=?",arrayOf(table));sql.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)",arrayOf(table,value.jsonPrimitive.long))
            }
            sql.version=7
        }
        open()
        assertEquals(9,db.openHelper.writableDatabase.version)
        assertEquals(beforeSnapshot.getValue("tables").jsonObject.filterKeys{it in BackupFormat.legacyTables},legacyRows())
        val migrated=snapshot()
        assertEquals(beforeSnapshot.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.legacySequences},migrated.getValue("sequences").jsonObject.filterKeys{it in BackupFormat.legacySequences})
        assertNotNull(FlashcardRepository(db,clock).restore());assertNotNull(TypingRepository(db,clock).restore());assertNotNull(SentencePracticeRepository(db,clock).restore())
        assertTrue(repo.datasets().isEmpty());assertEquals(0L,repo.list().total)
        imported();assertEquals(100L,repo.list().total)
    }
}
