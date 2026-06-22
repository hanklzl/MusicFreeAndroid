package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.ByteCacheStatusEntity
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
class ByteCacheStatusDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ByteCacheStatusDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.byteCacheStatusDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert replaces matching status`() = runTest {
        dao.upsert(entity(cachedBytes = 10L, updatedAt = 1L))
        dao.upsert(entity(cachedBytes = 20L, updatedAt = 2L))

        val row = dao.get("qq", "song-1", "STANDARD")

        assertNotNull(row)
        assertEquals(20L, row?.cachedBytes)
        assertEquals(2L, row?.updatedAt)
    }

    @Test
    fun `delete removes only matching quality`() = runTest {
        dao.upsert(entity(quality = "STANDARD"))
        dao.upsert(entity(quality = "HIGH"))

        dao.delete("qq", "song-1", "STANDARD")

        assertNull(dao.get("qq", "song-1", "STANDARD"))
        assertNotNull(dao.get("qq", "song-1", "HIGH"))
    }

    @Test
    fun `deleteBySong removes every quality for one song`() = runTest {
        dao.upsert(entity(quality = "STANDARD"))
        dao.upsert(entity(quality = "HIGH"))
        dao.upsert(entity(musicId = "song-2", quality = "STANDARD"))

        dao.deleteBySong("qq", "song-1")

        assertNull(dao.get("qq", "song-1", "STANDARD"))
        assertNull(dao.get("qq", "song-1", "HIGH"))
        assertNotNull(dao.get("qq", "song-2", "STANDARD"))
    }

    @Test
    fun `deleteByPlatform removes only matching platform`() = runTest {
        dao.upsert(entity(platform = "qq", musicId = "song-1"))
        dao.upsert(entity(platform = "kuwo", musicId = "song-2"))

        dao.deleteByPlatform("qq")

        assertNull(dao.get("qq", "song-1", "STANDARD"))
        assertNotNull(dao.get("kuwo", "song-2", "STANDARD"))
    }

    @Test
    fun `deleteAll removes every status`() = runTest {
        dao.upsert(entity(platform = "qq", musicId = "song-1"))
        dao.upsert(entity(platform = "kuwo", musicId = "song-2"))

        dao.deleteAll()

        assertNull(dao.get("qq", "song-1", "STANDARD"))
        assertNull(dao.get("kuwo", "song-2", "STANDARD"))
    }

    private fun entity(
        platform: String = "qq",
        musicId: String = "song-1",
        quality: String = "STANDARD",
        cachedBytes: Long = 100L,
        updatedAt: Long = 1L,
    ) = ByteCacheStatusEntity(
        platform = platform,
        musicId = musicId,
        quality = quality,
        status = "Partial",
        cachedBytes = cachedBytes,
        contentLength = 200L,
        validationMethod = "SpanInspection",
        sourceFingerprint = null,
        invalidReason = null,
        verifiedAt = null,
        updatedAt = updatedAt,
    )
}
