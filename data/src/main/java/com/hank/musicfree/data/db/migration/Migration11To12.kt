package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE play_queue ADD COLUMN rawJson TEXT")
        db.execSQL("ALTER TABLE play_queue ADD COLUMN localPath TEXT")
        db.execSQL("ALTER TABLE play_queue ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
    }
}
