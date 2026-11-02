package com.danishbears.bulgariantrainer.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["normalized_text"], unique = true),
        Index(value = ["archived", "normalized_part_of_speech", "id"]),
    ],
)
internal data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bulgarian_text") val bulgarianText: String,
    @ColumnInfo(name = "normalized_text") val normalizedText: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    @ColumnInfo(name = "normalized_part_of_speech") val normalizedPartOfSpeech: String?,
    @ColumnInfo(name = "grammatical_gender") val grammaticalGender: String?,
    val notes: String?,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "translations",
    foreignKeys = [ForeignKey(
        entity = WordEntity::class,
        parentColumns = ["id"],
        childColumns = ["word_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["word_id", "normalized_text"], unique = true),
        Index(value = ["normalized_text"]),
    ],
)
internal data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "russian_text") val russianText: String,
    @ColumnInfo(name = "normalized_text") val normalizedText: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
)

internal data class WordWithTranslations(
    @Embedded val word: WordEntity,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val translations: List<TranslationEntity>,
)
