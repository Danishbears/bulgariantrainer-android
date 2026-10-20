package com.danishbears.bulgariantrainer.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.MainActivity
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.backup.BackupError

@Composable
fun BackupScreen(model: BackupViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)model.export(uri) }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)model.inspect(uri) }
    BackHandler(state.busy || state.restored) { /* Replacement cannot be interrupted by navigation. */ }
    if(confirm!=null && state.previewId==confirm && !state.busy) AlertDialog(onDismissRequest={confirm=null},
        title={Text(stringResource(R.string.backup_confirm_title))},text={Text(stringResource(R.string.backup_confirm_text))},
        confirmButton={TextButton(onClick={ val id=confirm;confirm=null;if(id!=null)model.restoreConfirmed(id) }) { Text(stringResource(R.string.backup_replace)) }},
        dismissButton={TextButton(onClick={confirm=null}) { Text(stringResource(R.string.cancel_edit)) }})
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.backup_title),style=MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.backup_description)) }
        if(state.busy)item { CircularProgressIndicator();Text(stringResource(R.string.backup_busy)) }
        state.error?.let{ item { FailureMessage(backupErrorLabel(it)) } }
        if(state.saved)item { Text(stringResource(R.string.backup_saved)) }
        if(state.restored)item {
            Text(stringResource(R.string.backup_restored))
            Button(onClick={context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}) {
                Text(stringResource(R.string.backup_reopen))
            }
        }
        if(!state.restored) {
            item {
                Button(enabled=!state.busy,onClick={create.launch("bulgarian-trainer-full-backup.json")},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(enabled=!state.busy,onClick={open.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_open)) }
            }
            state.summary?.let { summary -> item {
                Text(stringResource(R.string.backup_preview),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.backup_date,summary.createdAt.toString()))
                Text(stringResource(R.string.backup_content_counts,summary.words,summary.translations,summary.sentences,summary.alternatives))
                Text(stringResource(R.string.backup_learning_counts,summary.sessions,summary.activeSessions,summary.attempts,summary.progress,summary.schedules))
                Text(stringResource(if(summary.preferences)R.string.backup_has_settings else R.string.backup_default_settings))
                Text(stringResource(R.string.backup_confirm_text))
                Button(enabled=!state.busy,onClick={confirm=state.previewId},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore)) }
                TextButton(enabled=!state.busy,onClick=model::cancelPreview) { Text(stringResource(R.string.cancel_edit)) }
            } }
        }
    }
}
@Composable private fun backupErrorLabel(error: BackupError) = stringResource(when(error) {
    BackupError.UNSUPPORTED_VERSION -> R.string.backup_unsupported
    BackupError.WRONG_APPLICATION -> R.string.backup_wrong_app
    BackupError.CHECKSUM_MISMATCH -> R.string.backup_checksum_error
    BackupError.INVALID_FORMAT,BackupError.INVALID_DATA -> R.string.backup_invalid
    BackupError.TOO_LARGE -> R.string.backup_too_large
    BackupError.READ_FAILED -> R.string.backup_read_error
    BackupError.WRITE_FAILED -> R.string.backup_write_error
    BackupError.RESTORE_FAILED -> R.string.backup_restore_error
})
