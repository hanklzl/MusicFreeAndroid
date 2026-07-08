package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_11_12
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-11-12-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration11To12Test {

    @get:Rule
    val helper = appDatabaseMigrationHelper(TEST_DB)

    @Test
    fun migrate11To12_addsReplayableQueueSnapshotColumns() = runTest {
        helper.createDatabase(11).use { db ->
            db.execSQL(
                """INSERT INTO play_queue(musicId, musicPlatform, title, artist, album,
                   duration, url, artwork, qualitiesJson, sortOrder)
                   VALUES('4930516', 'yuanliqq', 'Song', 'Artist', NULL,
                   180000, NULL, NULL, NULL, 0)""".trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(12, listOf(MIGRATION_11_12)).use { db ->
            db.query("SELECT rawJson, localPath, addedAt FROM play_queue WHERE musicId = '4930516'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertTrue(c.isNull(1))
                assertEquals(0L, c.getLong(2))
            }
        }

        openLatestAppDatabase(TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
