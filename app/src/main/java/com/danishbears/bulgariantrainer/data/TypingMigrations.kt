package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add only typing tables: existing content, flashcard attempts and recognition are untouched. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `typing_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `request_id` TEXT NOT NULL,
            `allow_shorter` INTEGER NOT NULL, `status` TEXT NOT NULL, `total` INTEGER NOT NULL,
            `current_position` INTEGER NOT NULL, `revision` INTEGER NOT NULL,
            `started_at` INTEGER NOT NULL, `completed_at` INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_typing_sessions_request_id` ON `typing_sessions` (`request_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_sessions_status_id` ON `typing_sessions` (`status`, `id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `typing_questions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `word_id` INTEGER NOT NULL,
            `position` INTEGER NOT NULL, `direction` TEXT NOT NULL, `bulgarian_text` TEXT NOT NULL,
            `russian_json` TEXT NOT NULL, `acknowledged` INTEGER NOT NULL,
            FOREIGN KEY(`session_id`) REFERENCES `typing_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_typing_questions_session_id_position` ON `typing_questions` (`session_id`, `position`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_typing_questions_session_id_word_id` ON `typing_questions` (`session_id`, `word_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_questions_word_id` ON `typing_questions` (`word_id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `typing_attempts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_id` INTEGER NOT NULL,
            `submitted` TEXT NOT NULL, `normalized_answer` TEXT NOT NULL, `correct` INTEGER NOT NULL, `submitted_at` INTEGER NOT NULL,
            FOREIGN KEY(`question_id`) REFERENCES `typing_questions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_typing_attempts_question_id` ON `typing_attempts` (`question_id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `typing_progress` (
            `word_id` INTEGER NOT NULL, `direction` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `correct` INTEGER NOT NULL,
            `consecutive_correct` INTEGER NOT NULL, `last_submitted_at` INTEGER NOT NULL, PRIMARY KEY(`word_id`, `direction`),
            FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
    }
}
