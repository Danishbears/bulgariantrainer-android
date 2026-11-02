package com.danishbears.bulgariantrainer.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF URIs only: no filesystem path resolution, broad permissions, or automatic imports. */
class DocumentGateway(private val context: Context) {
    suspend fun read(uri: Uri, kind: ContentKind): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: when (resolver.getType(uri)) {
            "text/csv", "application/csv" -> "content.csv"
            "text/plain" -> if (kind == ContentKind.WORDS) "words.txt" else "sentences.csv"
            else -> throw TransferException(TransferError.UNSUPPORTED_FILE)
        }
        val stream = resolver.openInputStream(uri) ?: throw TransferException(TransferError.READ_FAILED)
        name to stream.use(::readBounded)
    }
    suspend fun sample(): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        "bulgarian_sentences_100.csv" to context.assets.open("bulgarian_sentences_100.csv").use(::readBounded)
    }
    suspend fun write(uri: Uri, repository: ContentTransferRepository, kind: ContentKind, includeArchived: Boolean): Long = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw TransferException(TransferError.WRITE_FAILED)
        stream.bufferedWriter(Charsets.UTF_8).use { repository.export(kind, includeArchived, it) }
    }
    private fun readBounded(stream: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (result.size() <= ContentParser.MAX_BYTES) {
            val read = stream.read(buffer, 0, minOf(buffer.size, ContentParser.MAX_BYTES + 1 - result.size()))
            if (read == -1) break
            if (read == 0) continue
            result.write(buffer, 0, read)
        }
        if (result.size() > ContentParser.MAX_BYTES) throw TransferException(TransferError.FILE_TOO_LARGE)
        return result.toByteArray()
    }
}
