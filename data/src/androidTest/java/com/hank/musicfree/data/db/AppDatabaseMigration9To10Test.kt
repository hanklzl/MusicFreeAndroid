package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration9To10Test {

    @get:Rule
    val helper = appDatabaseMigrationHelper(TEST_DB)

    @Test
    fun migrate9To10_createsListenEventTables_andCascadeWorks() = runTest {
        helper.createDatabase(9).close()

        helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")

            db.execSQL(
                """
                INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                  album, artwork, durationMs, playedSeconds, completed, language, genre)
                VALUES(1000, 'm1', 'netease', 'Song', '周杰伦 & 林俊杰',
                  NULL, NULL, 240000, 60, 0, 'zh-CN', 'pop')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO listen_event_artist(eventId, artistName, artistOrder)
                VALUES(1, '周杰伦', 0), (1, '林俊杰', 1)
                """.trimIndent(),
            )

            db.query("SELECT COUNT(*) FROM listen_event_artist").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }

            // 验证 FK CASCADE：删除 listen_event 自动删除 listen_event_artist
            db.execSQL("DELETE FROM listen_event WHERE id = 1")
            db.query("SELECT COUNT(*) FROM listen_event_artist").use { c ->
                c.moveToFirst()
                assertEquals("cascade should delete artist rows", 0, c.getInt(0))
            }
        }

        // 跑完 migration 后让 Room 用最新 schema 打开一次，验证 entity 与 db 完全对齐
        openLatestAppDatabase(TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
