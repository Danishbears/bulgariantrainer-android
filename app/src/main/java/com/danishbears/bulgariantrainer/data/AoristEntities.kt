package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Entity(tableName="aorist_datasets", indices=[Index(value=["externalId"], unique=true)])
internal data class AoristDatasetEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,
    val externalId:String, val revision:Long, val metadata:String, val version:Long=0,
    val createdAt:Long, val updatedAt:Long)

@Entity(tableName="aorist_verbs", foreignKeys=[ForeignKey(entity=AoristDatasetEntity::class,
    parentColumns=["id"], childColumns=["datasetId"])], indices=[Index(value=["datasetId","externalId"],unique=true),
    Index(value=["archived","aspect","studyGroup","id"])])
internal data class AoristVerbEntity(@PrimaryKey(autoGenerate=true) val id:Long=0, val datasetId:Long,
    val externalId:String, val lemma:String, val russian:String, val aspect:String, val tense:String,
    val reflexiveParticle:String?, val studyGroup:String, val note:String, val source:String,
    val searchText:String, val contentHash:String, val archived:Boolean=false, val version:Long=0,
    val createdAt:Long, val updatedAt:Long)

@Entity(tableName="aorist_forms", foreignKeys=[ForeignKey(entity=AoristVerbEntity::class,
    parentColumns=["id"], childColumns=["verbId"])], indices=[Index(value=["verbId","person"],unique=true)])
internal data class AoristFormEntity(@PrimaryKey(autoGenerate=true) val id:Long=0, val verbId:Long, val person:String)

@Entity(tableName="aorist_answers", foreignKeys=[ForeignKey(entity=AoristFormEntity::class,
    parentColumns=["id"], childColumns=["formId"])], indices=[Index(value=["formId","position"],unique=true),
    Index(value=["formId","normalizedKey"],unique=true)])
internal data class AoristAnswerEntity(@PrimaryKey(autoGenerate=true) val id:Long=0, val formId:Long,
    val position:Int, val text:String, val normalizedKey:String)

@Entity(tableName="aorist_receipts", foreignKeys=[ForeignKey(entity=AoristDatasetEntity::class,
    parentColumns=["id"], childColumns=["datasetId"])], indices=[Index("datasetId"), Index(value=["previewId"],unique=true)])
internal data class AoristReceiptEntity(@PrimaryKey val requestId:String, val previewId:String, val datasetId:Long,
    val requestHash:String, val fileHash:String, val summary:String, val createdAt:Long)

internal data class AoristFormGraph(@Embedded val form:AoristFormEntity,
    @Relation(parentColumn="id",entityColumn="formId") val answers:List<AoristAnswerEntity>)
internal data class AoristGraph(@Embedded val verb:AoristVerbEntity, val namespace:String,
    @Relation(entity=AoristFormEntity::class,parentColumn="id",entityColumn="verbId") val forms:List<AoristFormGraph>)
