package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_13_14
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val RUNTIME_SNAPSHOT_TEST_DB = "runtime-snapshot-migration-13-14"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration13To14Test {
    @get:Rule
    val helper = appDatabaseMigrationHelper(RUNTIME_SNAPSHOT_TEST_DB)

    @Test
    fun migrate13To14CreatesRuntimeSnapshotsTableAndIndexes() = runTest {
        helper.createDatabase(13).close()

        helper.runMigrationsAndValidate(14, listOf(MIGRATION_13_14)).use { db ->
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

        openLatestAppDatabase(RUNTIME_SNAPSHOT_TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
