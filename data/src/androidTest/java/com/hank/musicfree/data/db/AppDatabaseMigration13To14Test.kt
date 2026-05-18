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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val RUNTIME_SNAPSHOT_TEST_DB = "runtime-snapshot-migration-13-14"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration13To14Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate13To14CreatesRuntimeSnapshotsTableAndIndexes() {
        helper.createDatabase(RUNTIME_SNAPSHOT_TEST_DB, 13).close()

        helper.runMigrationsAndValidate(
            RUNTIME_SNAPSHOT_TEST_DB,
            14,
            true,
            MIGRATION_13_14,
        ).use { db ->
            db.query("SELECT namespace, `key`, payloadJson FROM runtime_snapshots").use { cursor ->
                assertEquals(0, cursor.count)
            }

            db.query(
                """
                SELECT name FROM sqlite_master
                WHERE type = 'index' AND name IN
                  ('index_runtime_snapshots_namespace_updatedAtEpochMs',
                   'index_runtime_snapshots_namespace_expiresAtEpochMs')
                """.trimIndent(),
            ).use { c ->
                assertEquals(2, c.count)
            }
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            RUNTIME_SNAPSHOT_TEST_DB,
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
