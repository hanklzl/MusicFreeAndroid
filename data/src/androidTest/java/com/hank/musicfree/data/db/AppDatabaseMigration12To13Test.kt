package com.hank.musicfree.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-12-13-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration12To13Test {

    @get:Rule
    val helper = appDatabaseMigrationHelper(TEST_DB)

    @Test
    fun migrate12To13_createsTrafficDailyTable() = runTest {
        helper.createDatabase(12).close()

        helper.runMigrationsAndValidate(13, listOf(MIGRATION_12_13)).use { db ->
            db.query("SELECT COUNT(*) FROM traffic_daily").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.execSQL(
                """INSERT INTO traffic_daily(local_date, network_type, bytes_received, bytes_sent, updated_at)
                   VALUES('2026-05-19', 'WIFI', 100, 10, 1)""".trimIndent(),
            )
            db.query("SELECT bytes_received, bytes_sent FROM traffic_daily WHERE local_date = '2026-05-19'").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals(100L, c.getLong(0))
                assertEquals(10L, c.getLong(1))
            }
        }

        openLatestAppDatabase(TEST_DB, *APP_DATABASE_MIGRATIONS)
    }
}
