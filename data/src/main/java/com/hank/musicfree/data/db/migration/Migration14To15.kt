package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `byte_cache_status` (
                `platform` TEXT NOT NULL,
                `music_id` TEXT NOT NULL,
                `quality` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `cached_bytes` INTEGER NOT NULL DEFAULT 0,
                `content_length` INTEGER,
                `validation_method` TEXT NOT NULL,
                `source_fingerprint` TEXT,
                `invalid_reason` TEXT,
                `verified_at` INTEGER,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`platform`, `music_id`, `quality`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_byte_cache_status_updated_at`
            ON `byte_cache_status` (`updated_at`)
            """.trimIndent(),
        )
    }
}
