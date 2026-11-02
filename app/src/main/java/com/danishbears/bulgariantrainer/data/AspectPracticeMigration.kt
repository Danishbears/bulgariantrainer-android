package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_8_9=object:Migration(8,9) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,requestId TEXT NOT NULL,settings TEXT NOT NULL,allowShorter INTEGER NOT NULL,total INTEGER NOT NULL,position INTEGER NOT NULL,revision INTEGER NOT NULL,status TEXT NOT NULL,startedAt INTEGER NOT NULL,completedAt INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_sessions_requestId ON aspect_sessions(requestId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_questions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,sessionId INTEGER NOT NULL,pairId INTEGER NOT NULL,position INTEGER NOT NULL,direction TEXT NOT NULL,snapshot TEXT NOT NULL,contentHash TEXT NOT NULL,acknowledged INTEGER NOT NULL,revealedAt INTEGER,FOREIGN KEY(sessionId) REFERENCES aspect_sessions(id) ON UPDATE NO ACTION ON DELETE NO ACTION,FOREIGN KEY(pairId) REFERENCES aspect_pairs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_questions_sessionId_position ON aspect_questions(sessionId,position)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_questions_sessionId_pairId ON aspect_questions(sessionId,pairId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_aspect_questions_pairId ON aspect_questions(pairId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,questionId INTEGER NOT NULL,submitted TEXT,normalized TEXT,outcome TEXT NOT NULL,createdAt INTEGER NOT NULL,FOREIGN KEY(questionId) REFERENCES aspect_questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_aspect_attempts_questionId ON aspect_attempts(questionId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_progress (pairId INTEGER NOT NULL,direction TEXT NOT NULL,attempts INTEGER NOT NULL,correct INTEGER NOT NULL,revealed INTEGER NOT NULL,lastReviewedAt INTEGER NOT NULL,PRIMARY KEY(pairId,direction),FOREIGN KEY(pairId) REFERENCES aspect_pairs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE TABLE IF NOT EXISTS aspect_review_schedules (pairId INTEGER NOT NULL,direction TEXT NOT NULL,attempts INTEGER NOT NULL,successes INTEGER NOT NULL,streak INTEGER NOT NULL,intervalDays INTEGER NOT NULL,lastReview INTEGER NOT NULL,nextReview INTEGER NOT NULL,PRIMARY KEY(pairId,direction),FOREIGN KEY(pairId,direction) REFERENCES aspect_progress(pairId,direction) ON UPDATE NO ACTION ON DELETE NO ACTION)")
    }
}
