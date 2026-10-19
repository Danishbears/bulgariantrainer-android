package com.danishbears.bulgariantrainer.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "sentences", indices = [Index(value = ["normalized_russian"], unique = true), Index(value = ["archived", "id"])])
internal data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_filename") val sourceFilename: String,
    @ColumnInfo(name = "source_row") val sourceRow: Int,
    @ColumnInfo(name = "russian_text") val russian: String,
    @ColumnInfo(name = "normalized_russian") val normalizedRussian: String,
    @ColumnInfo(name = "bulgarian_text") val bulgarian: String,
    val explanation: String?, val difficulty: Int?, val topic: String?,
    val archived: Boolean = false, val approved: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
@Entity(tableName = "sentence_alternatives", foreignKeys = [ForeignKey(entity = SentenceEntity::class,
    parentColumns = ["id"], childColumns = ["sentence_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sentence_id", "normalized_text"], unique = true)])
internal data class SentenceAlternativeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    val position: Int,
    @ColumnInfo(name = "bulgarian_text") val text: String,
    @ColumnInfo(name = "normalized_text") val key: String,
)
internal data class SentenceWithAlternatives(
    @Embedded val sentence: SentenceEntity,
    @Relation(parentColumn = "id", entityColumn = "sentence_id") val alternatives: List<SentenceAlternativeEntity>,
)
@Entity(tableName = "import_receipts")
internal data class ImportReceiptEntity(
    @PrimaryKey val id: String, val fingerprint: String,
    val created: Int,
    @ColumnInfo(name = "translations_added") val translationsAdded: Int,
    val skipped: Int,
    @ColumnInfo(name = "committed_at") val committedAt: Long,
)
