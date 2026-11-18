package com.danishbears.bulgariantrainer.data

import androidx.room.*

@Dao internal interface AoristDao {
    @Query("SELECT * FROM aorist_datasets ORDER BY id") suspend fun datasets():List<AoristDatasetEntity>
    @Query("SELECT * FROM aorist_datasets WHERE externalId=:external") suspend fun dataset(external:String):AoristDatasetEntity?
    @Query("SELECT * FROM aorist_datasets WHERE id=:id") suspend fun datasetById(id:Long):AoristDatasetEntity?
    @Insert suspend fun insertDataset(value:AoristDatasetEntity):Long
    @Update suspend fun updateDataset(value:AoristDatasetEntity)
    @Transaction @Query("SELECT v.*,d.externalId AS namespace FROM aorist_verbs v JOIN aorist_datasets d ON d.id=v.datasetId WHERE v.id=:id")
    suspend fun get(id:Long):AoristGraph?
    @Query("SELECT * FROM aorist_verbs WHERE datasetId=:dataset AND externalId=:external") suspend fun identity(dataset:Long,external:String):AoristVerbEntity?
    @Transaction @Query("""SELECT v.*,d.externalId AS namespace FROM aorist_verbs v JOIN aorist_datasets d ON d.id=v.datasetId
        WHERE v.archived=:archived AND (:namespace IS NULL OR d.externalId=:namespace)
        AND (:aspect IS NULL OR v.aspect=:aspect) AND (:studyGroup IS NULL OR v.studyGroup=:studyGroup)
        AND (:particle IS NULL OR COALESCE(v.reflexiveParticle,'')=:particle)
        AND (:pattern IS NULL OR v.searchText LIKE :pattern ESCAPE '!') ORDER BY v.id LIMIT :limit OFFSET :offset""")
    suspend fun list(namespace:String?,archived:Boolean,aspect:String?,studyGroup:String?,particle:String?,pattern:String?,limit:Int,offset:Int):List<AoristGraph>
    @Query("""SELECT COUNT(*) FROM aorist_verbs v JOIN aorist_datasets d ON d.id=v.datasetId
        WHERE v.archived=:archived AND (:namespace IS NULL OR d.externalId=:namespace)
        AND (:aspect IS NULL OR v.aspect=:aspect) AND (:studyGroup IS NULL OR v.studyGroup=:studyGroup)
        AND (:particle IS NULL OR COALESCE(v.reflexiveParticle,'')=:particle)
        AND (:pattern IS NULL OR v.searchText LIKE :pattern ESCAPE '!')""")
    suspend fun count(namespace:String?,archived:Boolean,aspect:String?,studyGroup:String?,particle:String?,pattern:String?):Long
    @Transaction @Query("SELECT v.*,d.externalId AS namespace FROM aorist_verbs v JOIN aorist_datasets d ON d.id=v.datasetId WHERE v.datasetId=:dataset AND (:archived OR v.archived=0) ORDER BY v.id LIMIT 1001")
    suspend fun export(dataset:Long,archived:Boolean):List<AoristGraph>
    @Query("SELECT id FROM aorist_verbs ORDER BY id") suspend fun allIds():List<Long>
    @Insert suspend fun insert(value:AoristVerbEntity):Long
    @Update suspend fun update(value:AoristVerbEntity)
    @Query("SELECT * FROM aorist_forms WHERE verbId=:id ORDER BY id") suspend fun forms(id:Long):List<AoristFormEntity>
    @Insert suspend fun insertForm(value:AoristFormEntity):Long
    @Query("DELETE FROM aorist_answers WHERE formId=:id") suspend fun deleteAnswers(id:Long)
    @Insert suspend fun insertAnswers(values:List<AoristAnswerEntity>)
    @Query("SELECT * FROM aorist_receipts WHERE requestId=:id") suspend fun receipt(id:String):AoristReceiptEntity?
    @Query("SELECT * FROM aorist_receipts ORDER BY requestId") suspend fun receipts():List<AoristReceiptEntity>
    @Insert suspend fun insertReceipt(value:AoristReceiptEntity)
}
