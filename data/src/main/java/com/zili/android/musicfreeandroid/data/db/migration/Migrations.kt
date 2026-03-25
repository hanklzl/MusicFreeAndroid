package com.zili.android.musicfreeandroid.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `starred_sheets` (
                    `id` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT,
                    `coverUri` TEXT,
                    `sourceUrl` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }
}
