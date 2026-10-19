package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite cannot relax NOT NULL in place. Preserve children before removing the old parent;
        // do not disable FKs or let ON DELETE CASCADE discard alternatives. Room runs this atomically.
        db.execSQL("CREATE TEMP TABLE sentence_alternatives_backup AS SELECT * FROM sentence_alternatives")
        db.execSQL("CREATE TEMP TABLE sentence_sequence_backup AS SELECT name, seq FROM sqlite_sequence WHERE name IN ('sentences','sentence_alternatives')")
        db.execSQL("DROP TABLE sentence_alternatives")
        db.execSQL("""CREATE TABLE `sentences_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source_id` TEXT NOT NULL,
            `source_filename` TEXT NOT NULL, `source_row` INTEGER NOT NULL,
            `russian_text` TEXT NOT NULL, `normalized_russian` TEXT NOT NULL, `bulgarian_text` TEXT NOT NULL,
            `explanation` TEXT, `difficulty` INTEGER, `topic` TEXT, `archived` INTEGER NOT NULL,
            `approved` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)""")
        db.execSQL("INSERT INTO sentences_new SELECT * FROM sentences")
        db.execSQL("DROP TABLE sentences")
        db.execSQL("ALTER TABLE sentences_new RENAME TO sentences")
        db.execSQL("CREATE UNIQUE INDEX `index_sentences_normalized_russian` ON `sentences` (`normalized_russian`)")
        db.execSQL("CREATE INDEX `index_sentences_archived_id` ON `sentences` (`archived`, `id`)")
        db.execSQL("""CREATE TABLE `sentence_alternatives` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sentence_id` INTEGER NOT NULL, `position` INTEGER NOT NULL,
            `bulgarian_text` TEXT NOT NULL, `normalized_text` TEXT NOT NULL,
            FOREIGN KEY(`sentence_id`) REFERENCES `sentences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE UNIQUE INDEX `index_sentence_alternatives_sentence_id_normalized_text` ON `sentence_alternatives` (`sentence_id`, `normalized_text`)")
        db.execSQL("INSERT INTO sentence_alternatives SELECT * FROM sentence_alternatives_backup")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) SELECT b.name, b.seq FROM sentence_sequence_backup b WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence s WHERE s.name = b.name)")
        db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM sentence_sequence_backup b WHERE b.name = sqlite_sequence.name), seq)) WHERE name IN ('sentences','sentence_alternatives')")
        db.execSQL("DROP TABLE sentence_alternatives_backup")
        db.execSQL("DROP TABLE sentence_sequence_backup")
        db.execSQL("""CREATE TABLE `sentence_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `request_id` TEXT NOT NULL,
            `requested_size` INTEGER NOT NULL, `status` TEXT NOT NULL, `total` INTEGER NOT NULL,
            `current_position` INTEGER NOT NULL, `revision` INTEGER NOT NULL,
            `started_at` INTEGER NOT NULL, `completed_at` INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX `index_sentence_sessions_request_id` ON `sentence_sessions` (`request_id`)")
        db.execSQL("CREATE INDEX `index_sentence_sessions_status_id` ON `sentence_sessions` (`status`, `id`)")
        db.execSQL("""CREATE TABLE `sentence_questions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `sentence_id` INTEGER NOT NULL,
            `position` INTEGER NOT NULL, `russian_text` TEXT NOT NULL, `accepted_json` TEXT NOT NULL,
            `explanation` TEXT, `difficulty` INTEGER, `topic` TEXT, `acknowledged` INTEGER NOT NULL,
            FOREIGN KEY(`session_id`) REFERENCES `sentence_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`sentence_id`) REFERENCES `sentences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX `index_sentence_questions_session_id_position` ON `sentence_questions` (`session_id`, `position`)")
        db.execSQL("CREATE UNIQUE INDEX `index_sentence_questions_session_id_sentence_id` ON `sentence_questions` (`session_id`, `sentence_id`)")
        db.execSQL("CREATE INDEX `index_sentence_questions_sentence_id` ON `sentence_questions` (`sentence_id`)")
        db.execSQL("""CREATE TABLE `sentence_attempts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_id` INTEGER NOT NULL,
            `submitted` TEXT NOT NULL, `normalized_answer` TEXT NOT NULL, `automatic_match` INTEGER NOT NULL,
            `self_rating` TEXT, `submitted_at` INTEGER NOT NULL, `evaluated_at` INTEGER,
            FOREIGN KEY(`question_id`) REFERENCES `sentence_questions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX `index_sentence_attempts_question_id` ON `sentence_attempts` (`question_id`)")
        db.execSQL("""CREATE TABLE `sentence_progress` (
            `sentence_id` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `correct` INTEGER NOT NULL,
            `partial` INTEGER NOT NULL, `incorrect` INTEGER NOT NULL, `consecutive_correct` INTEGER NOT NULL,
            `last_evaluated_at` INTEGER NOT NULL, PRIMARY KEY(`sentence_id`),
            FOREIGN KEY(`sentence_id`) REFERENCES `sentences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
    }
}
