package com.zili.android.musicfreeandroid.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object SeedFavoriteCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedFavoriteRow(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seedFavoriteRow(db)
    }

    fun seedFavoriteRow(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO playlists
                (id, name, coverUri, description, sortMode, createdAt, updatedAt)
            VALUES ('favorite', '我喜欢', NULL, NULL, 'Manual', $now, $now)
            """.trimIndent()
        )
    }
}
