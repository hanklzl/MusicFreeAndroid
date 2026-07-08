package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-10-11-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration10To11Test {

    @get:Rule
    val helper = appDatabaseMigrationHelper(TEST_DB)

    @Test
    fun migrate10To11_addsMergeKey_andBackfillsByTitlePlusPrimaryArtist() = runTest {
        helper.createDatabase(10).use { db ->
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(1000, 'A', 'qq', '情人知己', '叶蒨文',
                   NULL, NULL, 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(2000, 'B', 'netease', '情人知己', '叶蒨文 & 张学友',
                   NULL, 'https://x/cover.jpg', 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(3000, 'C', 'qq', '情人知己', '张学友',
                   NULL, NULL, 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(11, listOf(MIGRATION_10_11)).use { db ->
            val keyA = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'A'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            val keyB = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'B'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            val keyC = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'C'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            assertEquals("情人知己|叶蒨文", keyA)
            assertEquals(keyA, keyB)
            assertEquals("情人知己|张学友", keyC)
            assertTrue("A/B should merge", keyA == keyB)
            assertTrue("C should NOT merge with A/B", keyC != keyA)

            db.query("SELECT COUNT(DISTINCT mergeKey) FROM listen_event").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }

            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_listen_event_mergeKey'",
            ).use { c -> assertTrue("mergeKey index should exist", c.moveToFirst()) }
        }

        openLatestAppDatabase(TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
