package com.danishbears.bulgariantrainer.backup

import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class BackupFormatTest {
    private fun bytes()=BackupFormat.encode(Instant.parse("2026-10-20T00:00:00Z"),JsonObject(BackupFormat.tables.associateWith{JsonArray(emptyList())}),JsonObject(BackupFormat.sequences.associateWith{JsonPrimitive(0)}))
    private fun reject(bytes: ByteArray,code: BackupError) { try{BackupFormat.decode(bytes);fail("accepted invalid file")}catch(e:BackupException){assertEquals(code,e.code)} }
    @Test fun versionedUtf8EnvelopeAcceptsBomWhitespaceAndPropertyReordering() {
        val root=BackupFormat.decode(bytes());val reordered=JsonObject(root.entries.reversed().associate{it.key to it.value})
        assertEquals(root,BackupFormat.decode(("\uFEFF \n"+reordered.toString()+"\n").toByteArray()))
    }
    @Test fun rejectsBothFutureFormatAndSchemaVersionsClearly() {
        for(key in listOf("formatVersion","schemaVersion")) {
            val root=BackupFormat.decode(bytes());reject(JsonObject(root+(key to JsonPrimitive(99))).toString().toByteArray(),BackupError.UNSUPPORTED_VERSION)
        }
    }
    @Test fun wrongApplicationAndChangedPayloadAreRejected() {
        val root=BackupFormat.decode(bytes())
        reject(JsonObject(root+("applicationId" to JsonPrimitive("other.app"))).toString().toByteArray(),BackupError.WRONG_APPLICATION)
        reject(JsonObject(root+("createdAt" to JsonPrimitive("2026-10-21T00:00:00Z"))).toString().toByteArray(),BackupError.CHECKSUM_MISMATCH)
    }
    @Test fun invalidUtf8TruncationAndTrailingGarbageAreRejected() {
        reject(byteArrayOf(0xd0.toByte()),BackupError.INVALID_FORMAT)
        reject(bytes().copyOf(20),BackupError.INVALID_FORMAT)
        reject(bytes()+"garbage".toByteArray(),BackupError.INVALID_FORMAT)
    }
    @Test fun duplicateObjectKeysAreRejectedEvenIfIdenticalAndChecksumWouldStillMatch() {
        val duplicated=bytes().toString(Charsets.UTF_8).replace("\"formatVersion\":1","\"formatVersion\":1,\"formatVersion\":1")
        reject(duplicated.toByteArray(),BackupError.INVALID_FORMAT)
    }
    @Test fun excessiveNestingIsRejectedBeforeTheJsonParserCanExhaustItsStack() {
        reject(("[".repeat(50_000)+"0"+"]".repeat(50_000)).toByteArray(),BackupError.INVALID_FORMAT)
    }
    @Test fun unpairedUnicodeEscapeIsRejectedInsteadOfBeingReplacedDuringUtf8OrSqliteEncoding() {
        val root=BackupFormat.decode(bytes())
        val tables=root.getValue("tables").jsonObject
        val changed=JsonObject(root+("tables" to JsonObject(tables+("words" to JsonArray(listOf(buildJsonObject{put("notes","\uD800")}))))))
        val signed=JsonObject(changed+("sha256" to JsonPrimitive(BackupFormat.digest(changed))))
        val raw=signed.toString().replace("\uD800","\\ud800").toByteArray()
        reject(raw,BackupError.INVALID_FORMAT)
    }
    @Test fun oversizedInputAndNonObjectFilesAreRejected() {
        reject(ByteArray(BackupFormat.MAX_BYTES+1),BackupError.TOO_LARGE)
        for(text in listOf("null","[]","123","\"backup\""))reject(text.toByteArray(),BackupError.INVALID_FORMAT)
    }
}
