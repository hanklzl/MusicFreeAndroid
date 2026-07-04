package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `title` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `artist` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `album` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `artwork` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `duration_ms` INTEGER")
    }
}
