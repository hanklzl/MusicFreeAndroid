package com.zili.android.musicfreeandroid.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.data.db.migration.Migrations
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(AppDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_createsStarredSheetsTable() {
        helper.createDatabase(dbName, 1).close()

        val db = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='starred_sheets'")
            .use { cursor ->
                val exists = cursor.moveToFirst()
                assertTrue(exists)
            }

        db.query("PRAGMA table_info(`starred_sheets`)").use { cursor ->
            var idPkOrder = 0
            var platformPkOrder = 0
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val pkOrder = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                if (columnName == "id") idPkOrder = pkOrder
                if (columnName == "platform") platformPkOrder = pkOrder
            }
            assertTrue(idPkOrder == 1)
            assertTrue(platformPkOrder == 2)
        }
    }
}
