package com.zili.android.musicfreeandroid.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.PluginMetadataCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PluginMetadataCacheDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PluginMetadataCacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pluginMetadataCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getByPath returns the row`() = runTest {
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "platform-a"))
        val row = dao.getByPath("/plugins/a.js")
        assertNotNull(row)
        assertEquals("platform-a", row!!.platform)
    }

    @Test
    fun `getByPath returns null for unknown path`() = runTest {
        assertNull(dao.getByPath("/plugins/missing.js"))
    }

    @Test
    fun `upsert replaces existing row for same primary key`() = runTest {
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "old", version = "1.0.0"))
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "new", version = "2.0.0"))
        val row = dao.getByPath("/plugins/a.js")
        assertNotNull(row)
        assertEquals("new", row!!.platform)
        assertEquals("2.0.0", row.version)
    }

    @Test
    fun `getAll returns all rows`() = runTest {
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "a"))
        dao.upsert(sample(filePath = "/plugins/b.js", platform = "b"))
        dao.upsert(sample(filePath = "/plugins/c.js", platform = "c"))
        val rows = dao.getAll()
        assertEquals(3, rows.size)
    }

    @Test
    fun `deleteByPath removes only the targeted row`() = runTest {
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "a"))
        dao.upsert(sample(filePath = "/plugins/b.js", platform = "b"))
        dao.deleteByPath("/plugins/a.js")
        assertNull(dao.getByPath("/plugins/a.js"))
        assertNotNull(dao.getByPath("/plugins/b.js"))
    }

    @Test
    fun `deleteAll clears the table`() = runTest {
        dao.upsert(sample(filePath = "/plugins/a.js", platform = "a"))
        dao.upsert(sample(filePath = "/plugins/b.js", platform = "b"))
        dao.deleteAll()
        assertEquals(0, dao.getAll().size)
    }

    private fun sample(
        filePath: String,
        platform: String,
        version: String? = "1.0.0",
    ): PluginMetadataCacheEntity = PluginMetadataCacheEntity(
        filePath = filePath,
        platform = platform,
        version = version,
        hash = "abc",
        srcUrl = null,
        appVersion = null,
        supportedMethodsJson = """["search","getMediaSource"]""",
        supportedSearchTypesJson = """["music"]""",
        userVariableKeysJson = """["cookie"]""",
        sourceMtimeMs = 100L,
        cachedAtAppVersion = "1.0.0",
    )
}
