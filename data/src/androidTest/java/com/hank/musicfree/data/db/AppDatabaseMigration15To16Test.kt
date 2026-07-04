package com.hank.musicfree.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_15_16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration15To16Test {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAddsNullableMediaCacheDisplayColumnsAndKeepsRows() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO media_cache(platform, id, sourcesJson, updated_at)
                VALUES ('kg', '1', '{"STANDARD":{"url":"https://example.test/a.mp3"}}', 100)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16).use { db ->
            db.query("PRAGMA table_info(media_cache)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertTrue("title column missing", "title" in columns)
                assertTrue("artist column missing", "artist" in columns)
                assertTrue("album column missing", "album" in columns)
                assertTrue("artwork column missing", "artwork" in columns)
                assertTrue("duration_ms column missing", "duration_ms" in columns)
            }
            db.query(
                "SELECT platform, id, title, artist, album, artwork, duration_ms FROM media_cache",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kg", cursor.getString(0))
                assertEquals("1", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-15-16-display-cache.db"
    }
}
