package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Dao internal interface AspectDao {
    @Query("SELECT * FROM aspect_datasets ORDER BY id") suspend fun datasets():List<AspectDatasetEntity>
    @Query("SELECT * FROM aspect_datasets WHERE namespace=:namespace") suspend fun dataset(namespace:String):AspectDatasetEntity?
    @Query("SELECT * FROM aspect_datasets WHERE id=:id") suspend fun datasetById(id:Long):AspectDatasetEntity?
    @Insert suspend fun insertDataset(value:AspectDatasetEntity):Long
    @Update suspend fun updateDataset(value:AspectDatasetEntity)
    @Transaction
    @Query("SELECT p.*, d.namespace FROM aspect_pairs p JOIN aspect_datasets d ON d.id=p.datasetId WHERE p.id=:id")
    suspend fun get(id:Long):AspectGraph?
    @Query("SELECT * FROM aspect_pairs WHERE datasetId=:dataset AND externalId=:external")
    suspend fun identity(dataset:Long,external:String):AspectPairEntity?
    @Transaction
    @Query("""SELECT p.*, d.namespace FROM aspect_pairs p JOIN aspect_datasets d ON d.id=p.datasetId
        WHERE p.archived=:archived AND (:namespace IS NULL OR d.namespace=:namespace)
        AND (:stage IS NULL OR p.studyStage=:stage) AND (:topic IS NULL OR p.topic=:topic)
        AND (:relation IS NULL OR p.relationType=:relation)
        AND (:pattern IS NULL OR p.searchText LIKE :pattern ESCAPE '!')
        ORDER BY p.id LIMIT :limit OFFSET :offset""")
    suspend fun list(namespace:String?,archived:Boolean,stage:Int?,topic:String?,relation:String?,pattern:String?,limit:Int,offset:Int):List<AspectGraph>
    @Query("""SELECT COUNT(*) FROM aspect_pairs p JOIN aspect_datasets d ON d.id=p.datasetId
        WHERE p.archived=:archived AND (:namespace IS NULL OR d.namespace=:namespace)
        AND (:stage IS NULL OR p.studyStage=:stage) AND (:topic IS NULL OR p.topic=:topic)
        AND (:relation IS NULL OR p.relationType=:relation)
        AND (:pattern IS NULL OR p.searchText LIKE :pattern ESCAPE '!')""")
    suspend fun count(namespace:String?,archived:Boolean,stage:Int?,topic:String?,relation:String?,pattern:String?):Long
    @Transaction
    @Query("SELECT p.*, d.namespace FROM aspect_pairs p JOIN aspect_datasets d ON d.id=p.datasetId WHERE p.datasetId=:dataset AND (:archived OR p.archived=0) ORDER BY p.id LIMIT 1001")
    suspend fun export(dataset:Long,archived:Boolean):List<AspectGraph>
    @Query("SELECT * FROM aspect_pairs ORDER BY id") suspend fun all():List<AspectPairEntity>
    @Insert suspend fun insert(value:AspectPairEntity):Long
    @Update suspend fun update(value:AspectPairEntity)
    @Query("DELETE FROM aspect_answers WHERE pairId=:id") suspend fun deleteAnswers(id:Long)
    @Insert suspend fun insertAnswers(value:List<AspectAnswerEntity>)
    @Query("SELECT * FROM aspect_receipts WHERE requestId=:id") suspend fun receipt(id:String):AspectReceiptEntity?
    @Insert suspend fun insertReceipt(value:AspectReceiptEntity)
}
