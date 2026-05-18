package com.hank.musicfree.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import com.hank.musicfree.data.db.migration.MIGRATION_11_12
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import com.hank.musicfree.data.db.migration.MIGRATION_13_14
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-11-12-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration11To12Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate11To12_addsReplayableQueueSnapshotColumns() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                """INSERT INTO play_queue(musicId, musicPlatform, title, artist, album,
                   duration, url, artwork, qualitiesJson, sortOrder)
                   VALUES('4930516', 'yuanliqq', 'Song', 'Artist', NULL,
                   180000, NULL, NULL, NULL, 0)""".trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12).use { db ->
            db.query("SELECT rawJson, localPath, addedAt FROM play_queue WHERE musicId = '4930516'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertTrue(c.isNull(1))
                assertEquals(0L, c.getLong(2))
            }
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
        ).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
