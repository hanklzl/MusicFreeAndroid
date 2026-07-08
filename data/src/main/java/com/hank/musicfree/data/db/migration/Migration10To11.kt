package com.hank.musicfree.data.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.hank.musicfree.core.util.splitArtists
import com.hank.musicfree.data.db.execSql
import com.hank.musicfree.data.db.getTextOrNull
import com.hank.musicfree.data.db.useStatement
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog

val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val started = System.currentTimeMillis()
        connection.execSql("ALTER TABLE listen_event ADD COLUMN mergeKey TEXT NOT NULL DEFAULT ''")
        connection.execSql("CREATE INDEX IF NOT EXISTS index_listen_event_mergeKey ON listen_event(mergeKey)")

        var rowsUpdated = 0
        connection.useStatement("UPDATE listen_event SET mergeKey = ? WHERE id = ?") { update ->
            connection.useStatement("SELECT id, title, artistRaw FROM listen_event") { query ->
                while (query.step()) {
                    val id = query.getLong(0)
                    val title = query.getTextOrNull(1) ?: ""
                    val artistRaw = query.getTextOrNull(2) ?: ""
                    val primary = splitArtists(artistRaw).firstOrNull().orEmpty()
                    val key = "${title.trim().lowercase()}|${primary.trim().lowercase()}"
                    update.bindText(1, key)
                    update.bindLong(2, id)
                    update.step()
                    update.reset()
                    update.clearBindings()
                    rowsUpdated++
                }
            }
        }

        MfLog.detail(
            LogCategory.DATA,
            "listen_event_migration_10_11_backfilled",
            mapOf(
                "rows" to rowsUpdated,
                "durationMs" to (System.currentTimeMillis() - started),
            ),
        )
    }
}
