package com.danishbears.bulgariantrainer.backup

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only user-selected SAF documents. Never resolve content URIs into filesystem paths. */
class BackupDocuments(private val context: Context) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            val stream=context.contentResolver.openInputStream(uri) ?: throw BackupException(BackupError.READ_FAILED)
            stream.use { input ->
                val result=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(result.size()<=BackupFormat.MAX_BYTES) {
                    val count=input.read(buffer,0,minOf(buffer.size,BackupFormat.MAX_BYTES+1-result.size()))
                    if(count<0)break
                    if(count>0)result.write(buffer,0,count)
                }
                if(result.size()>BackupFormat.MAX_BYTES)throw BackupException(BackupError.TOO_LARGE)
                result.toByteArray()
            }
        } catch(e: BackupException) { throw e }
        catch(_: Exception) { throw BackupException(BackupError.READ_FAILED) }
    }
    suspend fun write(uri: Uri,bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val stream=context.contentResolver.openOutputStream(uri,"wt") ?: throw BackupException(BackupError.WRITE_FAILED)
            stream.use { it.write(bytes);it.flush() }
        } catch(e: BackupException) { throw e }
        catch(_: Exception) { throw BackupException(BackupError.WRITE_FAILED) }
    }
}
