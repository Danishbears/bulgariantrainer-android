package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add scheduling facts; do not rewrite historical attempts, counters, queues or rating sources. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flashcard_sessions ADD COLUMN study_scope TEXT NOT NULL DEFAULT 'MIXED'")
        db.execSQL("ALTER TABLE typing_sessions ADD COLUMN study_scope TEXT NOT NULL DEFAULT 'MIXED'")
        db.execSQL("ALTER TABLE sentence_sessions ADD COLUMN study_scope TEXT NOT NULL DEFAULT 'MIXED'")
        db.execSQL("""CREATE TABLE `word_review_schedules` (
            `word_id` INTEGER NOT NULL, `mode` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `successes` INTEGER NOT NULL,
            `streak` INTEGER NOT NULL, `interval_days` INTEGER NOT NULL, `last_review_at` INTEGER, `next_review_at` INTEGER,
            `inherited` INTEGER NOT NULL, PRIMARY KEY(`word_id`,`mode`),
            FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX `index_word_review_schedules_mode_next_review_at` ON `word_review_schedules` (`mode`, `next_review_at`)")
        db.execSQL("""CREATE TABLE `sentence_review_schedules` (
            `sentence_id` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `successes` INTEGER NOT NULL,
            `streak` INTEGER NOT NULL, `interval_days` INTEGER NOT NULL, `last_review_at` INTEGER, `next_review_at` INTEGER,
            `inherited` INTEGER NOT NULL, PRIMARY KEY(`sentence_id`),
            FOREIGN KEY(`sentence_id`) REFERENCES `sentences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX `index_sentence_review_schedules_next_review_at` ON `sentence_review_schedules` (`next_review_at`)")
        // Web V6 preserves recognition streaks; rebuilt typing directions and sentence schedules start at 0.
        // Android already has direction-specific counts. Keep ALL old counter fields intact; copy only
        // known counts/times. Unknown intervals stay 0; next=last makes historical reviews eligible again.
        db.execSQL("""INSERT INTO word_review_schedules
            SELECT word_id,'FLASHCARD',attempts,known,consecutive_known,0,last_rated_at,last_rated_at,1
            FROM recognition_progress WHERE attempts > 0""")
        db.execSQL("""INSERT INTO word_review_schedules
            SELECT word_id,CASE direction WHEN 'BG_TO_RU' THEN 'TYPING_BG_TO_RU' ELSE 'TYPING_RU_TO_BG' END,
            attempts,correct,0,0,last_submitted_at,last_submitted_at,1 FROM typing_progress WHERE attempts > 0 AND direction IN ('BG_TO_RU','RU_TO_BG')""")
        db.execSQL("""INSERT INTO sentence_review_schedules
            SELECT sentence_id,attempts,correct,0,0,last_evaluated_at,last_evaluated_at,1
            FROM sentence_progress WHERE attempts > 0""")
    }
}
