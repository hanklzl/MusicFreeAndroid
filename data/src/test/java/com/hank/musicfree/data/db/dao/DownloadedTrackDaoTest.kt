package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadedTrackDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadedTrackDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.downloadedTrackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        dao.insert(makeEntity(id = "1", platform = "kuwo"))
        dao.insert(makeEntity(id = "2", platform = "kuwo"))
        dao.insert(makeEntity(id = "3", platform = "kugou"))

        dao.deleteByPlatform("kuwo")

        assertFalse(dao.exists("1", "kuwo"))
        assertFalse(dao.exists("2", "kuwo"))
        assertTrue(dao.exists("3", "kugou"))
    }

    @Test fun `deleteByPlatform on missing platform is no-op`() = runTest {
        dao.insert(makeEntity(id = "1", platform = "kuwo"))
        dao.deleteByPlatform("nonexistent")
        assertTrue(dao.exists("1", "kuwo"))
    }

    @Test fun `get returns full row for known id and platform`() = runTest {
        dao.insert(makeEntity(id = "1", platform = "kuwo"))

        val row = dao.get("1", "kuwo")

        assertNotNull(row)
        assertEquals("1", row!!.id)
        assertEquals("kuwo", row.platform)
        assertEquals("Music/kuwo/1", row.relativePath)
    }

    @Test fun `get returns null for unknown key`() = runTest {
        dao.insert(makeEntity(id = "1", platform = "kuwo"))

        assertNull(dao.get("1", "kugou"))
        assertNull(dao.get("missing", "kuwo"))
    }

    private fun makeEntity(id: String, platform: String): DownloadedTrackEntity = DownloadedTrackEntity(
        id = id,
        platform = platform,
        mediaStoreUri = "content://media/external/audio/media/$id",
        relativePath = "Music/$platform/$id",
        mimeType = "audio/mpeg",
        quality = "STANDARD",
        sizeBytes = 1024L,
        downloadedAt = 0L,
    )
}
