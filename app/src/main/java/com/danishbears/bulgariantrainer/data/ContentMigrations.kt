package com.danishbears.bulgariantrainer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add content tables only; existing vocabulary and all identities remain intact. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `sentences` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `source_id` TEXT NOT NULL, `source_filename` TEXT NOT NULL, `source_row` INTEGER NOT NULL,
            `russian_text` TEXT NOT NULL, `normalized_russian` TEXT NOT NULL, `bulgarian_text` TEXT NOT NULL,
            `explanation` TEXT, `difficulty` INTEGER NOT NULL, `topic` TEXT,
            `archived` INTEGER NOT NULL, `approved` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sentences_normalized_russian` ON `sentences` (`normalized_russian`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sentences_archived_id` ON `sentences` (`archived`, `id`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `sentence_alternatives` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sentence_id` INTEGER NOT NULL, `position` INTEGER NOT NULL,
            `bulgarian_text` TEXT NOT NULL, `normalized_text` TEXT NOT NULL,
            FOREIGN KEY(`sentence_id`) REFERENCES `sentences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sentence_alternatives_sentence_id_normalized_text` ON `sentence_alternatives` (`sentence_id`, `normalized_text`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `import_receipts` (
            `id` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `created` INTEGER NOT NULL,
            `translations_added` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, `committed_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
    }
}
