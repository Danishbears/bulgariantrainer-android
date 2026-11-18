package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Content only: never changes any pre-existing table, row or learning history. */
internal val MIGRATION_9_10=object:Migration(9,10) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aorist_datasets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,externalId TEXT NOT NULL,revision INTEGER NOT NULL,metadata TEXT NOT NULL,version INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_datasets_externalId ON aorist_datasets(externalId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_verbs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,datasetId INTEGER NOT NULL,externalId TEXT NOT NULL,
            lemma TEXT NOT NULL,russian TEXT NOT NULL,aspect TEXT NOT NULL,tense TEXT NOT NULL,reflexiveParticle TEXT,studyGroup TEXT NOT NULL,note TEXT NOT NULL,source TEXT NOT NULL,
            searchText TEXT NOT NULL,contentHash TEXT NOT NULL,archived INTEGER NOT NULL,version INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,
            FOREIGN KEY(datasetId) REFERENCES aorist_datasets(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_verbs_datasetId_externalId ON aorist_verbs(datasetId,externalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aorist_verbs_archived_aspect_studyGroup_id ON aorist_verbs(archived,aspect,studyGroup,id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aorist_forms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,verbId INTEGER NOT NULL,person TEXT NOT NULL,FOREIGN KEY(verbId) REFERENCES aorist_verbs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_forms_verbId_person ON aorist_forms(verbId,person)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aorist_answers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,formId INTEGER NOT NULL,position INTEGER NOT NULL,text TEXT NOT NULL,normalizedKey TEXT NOT NULL,FOREIGN KEY(formId) REFERENCES aorist_forms(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_answers_formId_position ON aorist_answers(formId,position)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_answers_formId_normalizedKey ON aorist_answers(formId,normalizedKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aorist_receipts (requestId TEXT NOT NULL PRIMARY KEY,previewId TEXT NOT NULL,datasetId INTEGER NOT NULL,requestHash TEXT NOT NULL,fileHash TEXT NOT NULL,summary TEXT NOT NULL,createdAt INTEGER NOT NULL,FOREIGN KEY(datasetId) REFERENCES aorist_datasets(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aorist_receipts_datasetId ON aorist_receipts(datasetId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_receipts_previewId ON aorist_receipts(previewId)")
    }
}
