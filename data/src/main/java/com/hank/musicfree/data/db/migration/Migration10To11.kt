package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hank.musicfree.core.util.splitArtists
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val started = System.currentTimeMillis()
        db.execSQL("ALTER TABLE listen_event ADD COLUMN mergeKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_mergeKey ON listen_event(mergeKey)")

        var rowsUpdated = 0
        val stmt = db.compileStatement("UPDATE listen_event SET mergeKey = ? WHERE id = ?")
        try {
            db.query("SELECT id, title, artistRaw FROM listen_event").use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val title = c.getString(1) ?: ""
                    val artistRaw = c.getString(2) ?: ""
                    val primary = splitArtists(artistRaw).firstOrNull().orEmpty()
                    val key = "${title.trim().lowercase()}|${primary.trim().lowercase()}"
                    stmt.bindString(1, key)
                    stmt.bindLong(2, id)
                    stmt.executeUpdateDelete()
                    stmt.clearBindings()
                    rowsUpdated++
                }
            }
        } finally {
            stmt.close()
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
