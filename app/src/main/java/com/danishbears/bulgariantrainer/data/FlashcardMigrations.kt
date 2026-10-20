package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcard_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `request_id` TEXT NOT NULL,
            `requested_size` INTEGER NOT NULL, `direction` TEXT NOT NULL, `status` TEXT NOT NULL,
            `total` INTEGER NOT NULL, `current_position` INTEGER NOT NULL,
            `started_at` INTEGER NOT NULL, `completed_at` INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flashcard_sessions_request_id` ON `flashcard_sessions` (`request_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_sessions_status_id` ON `flashcard_sessions` (`status`, `id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcard_cards` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `word_id` INTEGER NOT NULL,
            `position` INTEGER NOT NULL, `direction` TEXT NOT NULL, `bulgarian_text` TEXT NOT NULL,
            `russian_json` TEXT NOT NULL, `revealed_at` INTEGER,
            FOREIGN KEY(`session_id`) REFERENCES `flashcard_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flashcard_cards_session_id_position` ON `flashcard_cards` (`session_id`, `position`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flashcard_cards_session_id_word_id` ON `flashcard_cards` (`session_id`, `word_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_cards_word_id` ON `flashcard_cards` (`word_id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcard_attempts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `card_id` INTEGER NOT NULL,
            `known` INTEGER NOT NULL, `rated_at` INTEGER NOT NULL,
            FOREIGN KEY(`card_id`) REFERENCES `flashcard_cards`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flashcard_attempts_card_id` ON `flashcard_attempts` (`card_id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `recognition_progress` (
            `word_id` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `known` INTEGER NOT NULL,
            `consecutive_known` INTEGER NOT NULL, `last_rated_at` INTEGER NOT NULL, PRIMARY KEY(`word_id`),
            FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
    }
}
