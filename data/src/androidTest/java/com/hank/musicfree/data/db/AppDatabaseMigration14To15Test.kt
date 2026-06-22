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
import com.hank.musicfree.data.db.migration.MIGRATION_14_15
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BYTE_CACHE_STATUS_TEST_DB = "byte-cache-status-migration-14-15"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration14To15Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate14To15CreatesByteCacheStatusTableAndIndex() {
        helper.createDatabase(BYTE_CACHE_STATUS_TEST_DB, 14).close()

        helper.runMigrationsAndValidate(
            BYTE_CACHE_STATUS_TEST_DB,
            15,
            true,
            MIGRATION_14_15,
        ).use { db ->
            db.query("SELECT platform, music_id, quality FROM byte_cache_status").use { cursor ->
                assertEquals(0, cursor.count)
            }
            db.query(
                """
                SELECT name FROM sqlite_master
                WHERE type = 'index' AND name = 'index_byte_cache_status_updated_at'
                """.trimIndent(),
            ).use { cursor ->
                assertEquals(1, cursor.count)
            }
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            BYTE_CACHE_STATUS_TEST_DB,
        ).addMigrations(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
        ).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
