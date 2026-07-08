package com.hank.musicfree.data.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.hank.musicfree.data.db.execSql

val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSql("ALTER TABLE play_queue ADD COLUMN rawJson TEXT")
        connection.execSql("ALTER TABLE play_queue ADD COLUMN localPath TEXT")
        connection.execSql("ALTER TABLE play_queue ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
    }
}
