package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive practice increment. Existing content and all earlier learning rows remain untouched. */
internal val MIGRATION_10_11=object:Migration(10,11) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,requestId TEXT NOT NULL,settings TEXT NOT NULL,
            allowShorter INTEGER NOT NULL,allowFewerOptions INTEGER NOT NULL,policyVersion INTEGER NOT NULL,total INTEGER NOT NULL,position INTEGER NOT NULL,
            revision INTEGER NOT NULL,status TEXT NOT NULL,startedAt INTEGER NOT NULL,completedAt INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_sessions_requestId ON aorist_sessions(requestId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_questions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,sessionId INTEGER NOT NULL,formId INTEGER NOT NULL,
            verbId INTEGER NOT NULL,person TEXT NOT NULL,exercise TEXT NOT NULL,position INTEGER NOT NULL,snapshot TEXT NOT NULL,contentHash TEXT NOT NULL,
            options TEXT NOT NULL,acknowledged INTEGER NOT NULL,revealedAt INTEGER,draft TEXT NOT NULL,selectedDraft TEXT,draftVersion INTEGER NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES aorist_sessions(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(formId) REFERENCES aorist_forms(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(verbId) REFERENCES aorist_verbs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_questions_sessionId_position ON aorist_questions(sessionId,position)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_questions_sessionId_formId ON aorist_questions(sessionId,formId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aorist_questions_formId ON aorist_questions(formId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aorist_questions_verbId ON aorist_questions(verbId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,questionId INTEGER NOT NULL,
            submitted TEXT,optionId TEXT,normalized TEXT,outcome TEXT NOT NULL,createdAt INTEGER NOT NULL,
            FOREIGN KEY(questionId) REFERENCES aorist_questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aorist_attempts_questionId ON aorist_attempts(questionId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_progress (formId INTEGER NOT NULL,exercise TEXT NOT NULL,attempts INTEGER NOT NULL,
            correct INTEGER NOT NULL,incorrect INTEGER NOT NULL,revealed INTEGER NOT NULL,lastReviewedAt INTEGER NOT NULL,PRIMARY KEY(formId,exercise),
            FOREIGN KEY(formId) REFERENCES aorist_forms(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS aorist_review_schedules (formId INTEGER NOT NULL,exercise TEXT NOT NULL,attempts INTEGER NOT NULL,
            successes INTEGER NOT NULL,streak INTEGER NOT NULL,intervalDays INTEGER NOT NULL,lastReview INTEGER NOT NULL,nextReview INTEGER NOT NULL,PRIMARY KEY(formId,exercise),
            FOREIGN KEY(formId,exercise) REFERENCES aorist_progress(formId,exercise) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE TABLE IF NOT EXISTS aorist_preferences (id INTEGER NOT NULL PRIMARY KEY,settings TEXT NOT NULL)")
    }
}
