package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_14_15
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BYTE_CACHE_STATUS_TEST_DB = "byte-cache-status-migration-14-15"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration14To15Test {
    @get:Rule
    val helper = appDatabaseMigrationHelper(BYTE_CACHE_STATUS_TEST_DB)

    @Test
    fun migrate14To15CreatesByteCacheStatusTableAndIndex() = runTest {
        helper.createDatabase(14).close()

        helper.runMigrationsAndValidate(15, listOf(MIGRATION_14_15)).use { db ->
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

        openLatestAppDatabase(BYTE_CACHE_STATUS_TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
