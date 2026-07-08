package com.hank.musicfree.data.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.hank.musicfree.data.db.execSql

val MIGRATION_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSql("ALTER TABLE `media_cache` ADD COLUMN `title` TEXT")
        connection.execSql("ALTER TABLE `media_cache` ADD COLUMN `artist` TEXT")
        connection.execSql("ALTER TABLE `media_cache` ADD COLUMN `album` TEXT")
        connection.execSql("ALTER TABLE `media_cache` ADD COLUMN `artwork` TEXT")
        connection.execSql("ALTER TABLE `media_cache` ADD COLUMN `duration_ms` INTEGER")
    }
}
