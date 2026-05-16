package com.hank.musicfree.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.dao.MediaCacheDao
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
class MediaCacheRepositoryDeleteTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao
    private lateinit var repo: MediaCacheRepository

    private val item = MusicItem(
        id = "1", platform = "kuwo", title = "T", artist = "A",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
        repo = MediaCacheRepository(dao)
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform clears DB and memory`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        // warm memory
        assertNotNull(repo.get(item, PlayQuality.STANDARD))
        repo.get(item, PlayQuality.STANDARD)
        // sanity: memory hit verifies seeding works
        assertEquals(true, repo.lastHitFromMemory)

        repo.deleteByPlatform("kuwo")

        // DB row gone
        assertNull(dao.get("kuwo", "1"))
        // memory cleared: next get returns null and does NOT report memory hit
        assertNull(repo.get(item, PlayQuality.STANDARD))
        assertFalse("memory should be cleared after deleteByPlatform", repo.lastHitFromMemory)
    }

    @Test fun `deleteByPlatform only clears given platform's memory entries`() = runTest {
        val other = item.copy(id = "9", platform = "kugou")
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        repo.put(other, PlayQuality.STANDARD, MediaSourceResult("http://b", null, null, PlayQuality.STANDARD))

        // warm memory for both
        repo.get(item, PlayQuality.STANDARD)
        repo.get(other, PlayQuality.STANDARD)

        repo.deleteByPlatform("kuwo")

        // kugou survived in both DB and memory
        val kg = repo.get(other, PlayQuality.STANDARD)
        assertNotNull(kg)
        assertEquals("http://b", kg?.url)
    }

    @Test fun `deleteEntry strips single quality key but keeps row when others remain`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://std", null, null, PlayQuality.STANDARD))
        repo.put(item, PlayQuality.HIGH, MediaSourceResult("http://hi", null, null, PlayQuality.HIGH))

        repo.deleteEntry("kuwo", "1", PlayQuality.STANDARD)

        // STANDARD gone, HIGH still there
        assertNull(repo.get(item, PlayQuality.STANDARD))
        val hi = repo.get(item, PlayQuality.HIGH)
        assertNotNull(hi)
        assertEquals("http://hi", hi?.url)
        // DB row still exists
        assertNotNull(dao.get("kuwo", "1"))
    }

    @Test fun `deleteItem removes all quality keys and clears memory`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://std", null, null, PlayQuality.STANDARD))
        repo.put(item, PlayQuality.HIGH, MediaSourceResult("http://hi", null, null, PlayQuality.HIGH))

        // Warm memory with the full row so the delete must clear both DB and LRU.
        assertNotNull(repo.get(item, PlayQuality.STANDARD))
        assertNotNull(repo.get(item, PlayQuality.HIGH))
        repo.get(item, PlayQuality.HIGH)
        assertEquals(true, repo.lastHitFromMemory)

        repo.deleteItem("kuwo", "1")

        assertNull(dao.get("kuwo", "1"))
        assertNull(repo.get(item, PlayQuality.STANDARD))
        assertNull(repo.get(item, PlayQuality.HIGH))
        assertFalse("memory should be cleared after deleteItem", repo.lastHitFromMemory)
    }

    @Test fun `deleteEntry deletes row when last quality is removed`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://std", null, null, PlayQuality.STANDARD))

        repo.deleteEntry("kuwo", "1", PlayQuality.STANDARD)

        // DB row gone entirely
        assertNull(dao.get("kuwo", "1"))
        // memory cleared
        assertNull(repo.get(item, PlayQuality.STANDARD))
        assertFalse("memory should be cleared when row deleted", repo.lastHitFromMemory)
    }

    @Test fun `clearAll clears DB and memory`() = runTest {
        val other = item.copy(id = "9", platform = "kugou")
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        repo.put(other, PlayQuality.STANDARD, MediaSourceResult("http://b", null, null, PlayQuality.STANDARD))

        // warm memory for both entries
        repo.get(item, PlayQuality.STANDARD)
        repo.get(other, PlayQuality.STANDARD)

        repo.clearAll()

        assertEquals(0, dao.count())
        assertNull(repo.get(item, PlayQuality.STANDARD))
        assertNull(repo.get(other, PlayQuality.STANDARD))
        assertFalse("memory should be cleared after clearAll", repo.lastHitFromMemory)
    }

    @Test fun `put trims oldest rows after max byte limit changes`() = runTest {
        var now = 100L
        var limit = Long.MAX_VALUE
        repo = MediaCacheRepository(
            dao = dao,
            now = { now },
            limitProvider = { limit },
        )
        val old = item.copy(id = "old")
        val middle = item.copy(id = "middle")
        val newest = item.copy(id = "newest")

        repo.put(old, PlayQuality.STANDARD, MediaSourceResult("http://old", null, null, PlayQuality.STANDARD))
        now = 200L
        repo.put(middle, PlayQuality.STANDARD, MediaSourceResult("http://mid", null, null, PlayQuality.STANDARD))
        limit = dao.totalSizeBytes() + 1L
        now = 300L

        repo.put(newest, PlayQuality.STANDARD, MediaSourceResult("http://new", null, null, PlayQuality.STANDARD))

        assertNull(dao.get("kuwo", "old"))
        assertNotNull(dao.get("kuwo", "middle"))
        assertNotNull(dao.get("kuwo", "newest"))
        assertTrue(dao.totalSizeBytes() <= limit)
    }
}
