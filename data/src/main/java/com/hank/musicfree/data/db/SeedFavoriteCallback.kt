package com.hank.musicfree.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection

object SeedFavoriteCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        super.onCreate(connection)
        seedFavoriteRow(connection)
    }

    override suspend fun onOpen(connection: SQLiteConnection) {
        super.onOpen(connection)
        seedFavoriteRow(connection)
    }

    fun seedFavoriteRow(connection: SQLiteConnection) {
        val now = System.currentTimeMillis()
        connection.execSql(
            """
            INSERT OR IGNORE INTO playlists
                (id, name, coverUri, description, sortMode, createdAt, updatedAt)
            VALUES ('favorite', '我喜欢', NULL, NULL, 'Manual', $now, $now)
            """.trimIndent()
        )
    }
}
