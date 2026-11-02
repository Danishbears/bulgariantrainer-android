package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Entity(tableName="aspect_datasets", indices=[Index(value=["namespace"],unique=true)])
internal data class AspectDatasetEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val namespace:String,
    val metadata:String,val revision:String?=null,val version:Long=0,val createdAt:Long,val updatedAt:Long)
@Entity(tableName="aspect_pairs",foreignKeys=[ForeignKey(entity=AspectDatasetEntity::class,parentColumns=["id"],childColumns=["datasetId"])],
    indices=[Index(value=["datasetId","externalId"],unique=true),Index(value=["archived","studyStage","topic","id"])])
internal data class AspectPairEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val datasetId:Long,val externalId:String,
    val imperfective:String,val perfective:String,val russianImperfective:String,val russianPerfective:String,
    val topic:String,val studyStage:Int,val relationType:String,val note:String,val provenance:String,val examples:String?,
    val sourceContext:String,val searchText:String,val contentHash:String,val archived:Boolean=false,val version:Long=0,
    val createdAt:Long,val updatedAt:Long)
@Entity(tableName="aspect_answers",foreignKeys=[ForeignKey(entity=AspectPairEntity::class,parentColumns=["id"],childColumns=["pairId"])],
    indices=[Index(value=["pairId","side","position"],unique=true),Index(value=["pairId","side","normalizedKey"],unique=true)])
internal data class AspectAnswerEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val pairId:Long,val side:String,
    val position:Int,val text:String,val normalizedKey:String)
@Entity(tableName="aspect_receipts",foreignKeys=[ForeignKey(entity=AspectDatasetEntity::class,parentColumns=["id"],childColumns=["datasetId"])],
    indices=[Index("datasetId"),Index(value=["previewId"],unique=true)])
internal data class AspectReceiptEntity(@PrimaryKey val requestId:String,val previewId:String,val datasetId:Long,
    val requestHash:String,val fileHash:String,val metadata:String,val summary:String,val createdAt:Long)
internal data class AspectGraph(@Embedded val pair:AspectPairEntity,val namespace:String,
    @Relation(parentColumn="id",entityColumn="pairId")val answers:List<AspectAnswerEntity>)
