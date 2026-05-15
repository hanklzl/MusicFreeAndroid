package com.zili.android.musicfreeandroid.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.data.db.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration9To10Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate9To10_createsListenEventTables_andCascadeWorks() {
        helper.createDatabase(TEST_DB, 9).close()

        helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10).use { db ->
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
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_9_10).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
