package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaCacheRepositoryTest {

    private val item = MusicItem(
        id = "1", platform = "kg", title = "T", artist = "A",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test
    fun `get returns null when DAO has no row`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns null
        }
        val repo = MediaCacheRepository(dao)
        assertNull(repo.get(item, PlayQuality.STANDARD))
    }

    @Test
    fun `get returns CachedSource for matching quality`() = runTest {
        val json = """{"STANDARD":{"url":"http://a","headers":{"Referer":"r"},"userAgent":"ua"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
        }
        val repo = MediaCacheRepository(dao)
        val cached = repo.get(item, PlayQuality.STANDARD)
        assertNotNull(cached)
        assertEquals("http://a", cached?.url)
        assertEquals(mapOf("Referer" to "r"), cached?.headers)
        assertEquals("ua", cached?.userAgent)
    }

    @Test
    fun `get returns null when stored json lacks the requested quality`() = runTest {
        val json = """{"HIGH":{"url":"http://h"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
        }
        assertNull(MediaCacheRepository(dao).get(item, PlayQuality.STANDARD))
    }

    @Test
    fun `put merges new quality into existing json and bumps updatedAt`() = runTest {
        val existingJson = """{"STANDARD":{"url":"http://a"}}"""
        val capturedEntity = slot<MediaCacheEntity>()
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", existingJson, 100L)
            coEvery { upsert(capture(capturedEntity)) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 1
        }
        val repo = MediaCacheRepository(dao) { 999L }
        repo.put(item, PlayQuality.HIGH, MediaSourceResult("http://h", null, null, PlayQuality.HIGH))

        val saved = capturedEntity.captured
        assertEquals(999L, saved.updatedAt)
        val parsed = org.json.JSONObject(saved.sourcesJson)
        assertEquals("http://a", parsed.getJSONObject("STANDARD").getString("url"))
        assertEquals("http://h", parsed.getJSONObject("HIGH").getString("url"))
    }

    @Test
    fun `put triggers deleteOldest(400) when count reaches limit of 800`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 800
            coEvery { deleteOldest(400) } returns Unit
        }
        MediaCacheRepository(dao) { 1L }
            .put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        coVerify(exactly = 1) { dao.deleteOldest(400) }
    }

    @Test
    fun `put does not trigger deleteOldest when count below limit`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 799
        }
        MediaCacheRepository(dao) { 1L }
            .put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        coVerify(exactly = 0) { dao.deleteOldest(any()) }
    }

    @Test
    fun `put trims oldest entries when byte limit is exceeded`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 120L
            coEvery { getOldestEntries() } returns listOf(
                MediaCacheEntity("kg", "old", "12345", 100L),
                MediaCacheEntity("kg", "new", "12345", 200L),
            )
            coEvery { delete("kg", "old") } returns Unit
            coEvery { count() } returns 1
        }

        MediaCacheRepository(
            dao = dao,
            now = { 300L },
            limitProvider = { 115L },
        ).put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))

        coVerify(exactly = 1) { dao.delete("kg", "old") }
        coVerify(exactly = 0) { dao.delete("kg", "new") }
    }
}
