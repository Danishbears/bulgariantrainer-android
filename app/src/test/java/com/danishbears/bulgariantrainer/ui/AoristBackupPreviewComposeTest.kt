package com.danishbears.bulgariantrainer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aorist.*
import com.danishbears.bulgariantrainer.backup.*
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class AoristBackupPreviewComposeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TrainerDatabase;private val store=ViewModelStore()
    @After fun close(){store.clear();db.close()}
    private fun awaitText(value:String){compose.waitUntil(20_000){
        shadowOf(Looper.getMainLooper()).idle();compose.waitForIdle()
        if(compose.onAllNodesWithText(value).fetchSemanticsNodes().isEmpty())try{compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value))}catch(_:AssertionError){}
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }}
    @Test fun oldBackupWarningsAreInPreviewAndConfirmationAndOnlyExplicitReplacementClearsPractice() {
        db=Room.inMemoryDatabaseBuilder(context,TrainerDatabase::class.java).build()
        val content=AoristRepository(db);val practice=AoristPracticeRepository(db);val stats=AoristStatisticsRepository(db)
        val backup=BackupRepository(context,db)
        val original=runBlocking{
            val p=content.preview("starter.json",context.assets.open("bulgarian_aorist_40.json").use{it.readBytes()});content.confirm(p,p.changes.map{it.externalId}.toSet())
            val s=practice.start(UUID.randomUUID().toString(),AoristSettings(size=1));practice.submitTyped(s.id,s.question!!.id,"неверный ответ")
            BackupFormat.decode(backup.snapshot())
        }
        ShadowContentResolver.registerProviderInternal("aorist-backup-ui",Robolectric.buildContentProvider(FixtureDocuments::class.java).create().get())
        val resolver=shadowOf(context.contentResolver);val model=BackupViewModel(backup,BackupDocuments(context));store.put("backup",model)
        compose.setContent{MaterialTheme{BackupScreen(model)}}
        for(version in listOf(3,4)){
            val tables=if(version==3)BackupFormat.previousTables else BackupFormat.contentTables
            val seq=if(version==3)BackupFormat.previousSequences else BackupFormat.contentSequences
            val old=JsonObject(original+mapOf("formatVersion" to JsonPrimitive(version),"schemaVersion" to JsonPrimitive(version+6),
                "tables" to JsonObject(original.getValue("tables").jsonObject.filterKeys{it in tables}),"sequences" to JsonObject(original.getValue("sequences").jsonObject.filterKeys{it in seq})))
            val bytes=JsonObject(old+("sha256" to JsonPrimitive(BackupFormat.digest(old)))).toString().toByteArray()
            val uri=Uri.parse("content://aorist-backup-ui/old-$version.json");resolver.registerInputStream(uri,ByteArrayInputStream(bytes))
            compose.runOnIdle{model.inspect(uri)}
            val warning=context.getString(if(version==3)R.string.aorist_backup_legacy else R.string.aop_backup_v4)
            awaitText(warning);compose.onNodeWithText(warning).performScrollTo().assertExists()
            assertEquals(1L,runBlocking{stats.overview().counts.answered});assertEquals(40L,runBlocking{content.list().total})
            val restore=context.getString(R.string.backup_restore);awaitText(restore);compose.onNodeWithText(restore).performScrollTo().performClick()
            compose.onNode(hasText(warning) and hasAnyAncestor(isDialog())).assertExists()
            assertEquals(1L,runBlocking{stats.overview().counts.answered})
            if(version==3){
                compose.onNode(hasText(context.getString(R.string.cancel_edit)) and hasAnyAncestor(isDialog())).performClick()
                compose.runOnIdle{model.cancelPreview()}
            }else{
                compose.onNodeWithText(context.getString(R.string.backup_replace)).performClick();awaitText(context.getString(R.string.backup_restored))
                assertEquals(AoristStatisticsCounts(),runBlocking{stats.overview().counts})
                assertEquals(40L,runBlocking{content.list().total});assertNull(runBlocking{practice.restore()})
            }
        }
    }
}
