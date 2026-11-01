package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive content-only increment; practice/progress tables are reserved for the next stage. */
internal val MIGRATION_7_8=object:Migration(7,8){
    override fun migrate(db:SupportSQLiteDatabase){
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_datasets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, namespace TEXT NOT NULL, metadata TEXT NOT NULL, revision TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_datasets_namespace ON aspect_datasets(namespace)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aspect_pairs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            datasetId INTEGER NOT NULL,externalId TEXT NOT NULL,imperfective TEXT NOT NULL,perfective TEXT NOT NULL,
            russianImperfective TEXT NOT NULL,russianPerfective TEXT NOT NULL,topic TEXT NOT NULL,studyStage INTEGER NOT NULL,
            relationType TEXT NOT NULL,note TEXT NOT NULL,provenance TEXT NOT NULL,examples TEXT,sourceContext TEXT NOT NULL,
            searchText TEXT NOT NULL,contentHash TEXT NOT NULL,archived INTEGER NOT NULL,version INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,FOREIGN KEY(datasetId) REFERENCES aspect_datasets(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_pairs_datasetId_externalId ON aspect_pairs(datasetId,externalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aspect_pairs_archived_studyStage_topic_id ON aspect_pairs(archived,studyStage,topic,id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aspect_answers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,pairId INTEGER NOT NULL,
            side TEXT NOT NULL,position INTEGER NOT NULL,text TEXT NOT NULL,normalizedKey TEXT NOT NULL,
            FOREIGN KEY(pairId) REFERENCES aspect_pairs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_answers_pairId_side_position ON aspect_answers(pairId,side,position)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_answers_pairId_side_normalizedKey ON aspect_answers(pairId,side,normalizedKey)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aspect_receipts (requestId TEXT NOT NULL PRIMARY KEY,previewId TEXT NOT NULL,
            datasetId INTEGER NOT NULL,requestHash TEXT NOT NULL,fileHash TEXT NOT NULL,metadata TEXT NOT NULL,summary TEXT NOT NULL,createdAt INTEGER NOT NULL,
            FOREIGN KEY(datasetId) REFERENCES aspect_datasets(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aspect_receipts_datasetId ON aspect_receipts(datasetId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_receipts_previewId ON aspect_receipts(previewId)")
    }
}
