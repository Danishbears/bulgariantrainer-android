package com.danishbears.bulgariantrainer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.backup.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(val busy: Boolean=false,val summary: BackupSummary?=null,val previewId: String?=null,
    val error: BackupError?=null,val saved: Boolean=false,val restored: Boolean=false)
class BackupViewModel(private val repository: BackupRepository,private val documents: BackupDocuments): ViewModel() {
    private val mutable=MutableStateFlow(BackupUiState())
    val state=mutable.asStateFlow()
    private var prepared: PreparedBackup?=null
    fun inspect(uri: Uri) {
        if(mutable.value.busy || mutable.value.restored)return
        prepared=null;mutable.value=BackupUiState(busy=true)
        viewModelScope.launch {
            try {
                val result=repository.prepare(documents.read(uri));prepared=result
                mutable.value=BackupUiState(summary=result.summary,previewId=UUID.randomUUID().toString())
            } catch(e: CancellationException) { throw e }
            catch(e: BackupException) { mutable.value=BackupUiState(error=e.code) }
            catch(_: Exception) { mutable.value=BackupUiState(error=BackupError.READ_FAILED) }
        }
    }
    fun export(uri: Uri) {
        if(mutable.value.busy || mutable.value.restored)return
        prepared=null;mutable.value=BackupUiState(busy=true)
        viewModelScope.launch {
            try { documents.write(uri,repository.snapshot());mutable.value=BackupUiState(saved=true) }
            catch(e: CancellationException) { throw e }
            catch(e: BackupException) { mutable.value=BackupUiState(error=e.code) }
            catch(_: Exception) { mutable.value=BackupUiState(error=BackupError.WRITE_FAILED) }
        }
    }
    fun cancelPreview() { if(!mutable.value.busy && !mutable.value.restored) { prepared=null;mutable.value=BackupUiState() } }
    fun restoreConfirmed(previewId: String) {
        val selected=prepared ?: return
        if(mutable.value.busy || mutable.value.restored || mutable.value.previewId!=previewId)return
        mutable.value=mutable.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try { repository.restore(selected);prepared=null;mutable.value=BackupUiState(restored=true) }
            catch(e: CancellationException) { throw e }
            catch(e: BackupException) { mutable.value=mutable.value.copy(busy=false,error=e.code) }
            catch(_: Exception) { mutable.value=mutable.value.copy(busy=false,error=BackupError.RESTORE_FAILED) }
        }
    }
}
