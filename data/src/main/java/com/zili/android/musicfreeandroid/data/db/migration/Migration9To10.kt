package com.zili.android.musicfreeandroid.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listen_event` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `playedAtMs` INTEGER NOT NULL,
                `musicId` TEXT NOT NULL,
                `platform` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artistRaw` TEXT NOT NULL,
                `album` TEXT,
                `artwork` TEXT,
                `durationMs` INTEGER NOT NULL,
                `playedSeconds` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `language` TEXT,
                `genre` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_playedAtMs` ON `listen_event` (`playedAtMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_musicId_platform` ON `listen_event` (`musicId`, `platform`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listen_event_artist` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `eventId` INTEGER NOT NULL,
                `artistName` TEXT NOT NULL,
                `artistOrder` INTEGER NOT NULL,
                FOREIGN KEY(`eventId`) REFERENCES `listen_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_artist_eventId` ON `listen_event_artist` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_artist_artistName` ON `listen_event_artist` (`artistName`)")
    }
}
