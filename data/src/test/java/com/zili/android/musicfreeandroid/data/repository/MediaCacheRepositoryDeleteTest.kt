package com.zili.android.musicfreeandroid.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun `deleteEntry deletes row when last quality is removed`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://std", null, null, PlayQuality.STANDARD))

        repo.deleteEntry("kuwo", "1", PlayQuality.STANDARD)

        // DB row gone entirely
        assertNull(dao.get("kuwo", "1"))
        // memory cleared
        assertNull(repo.get(item, PlayQuality.STANDARD))
        assertFalse("memory should be cleared when row deleted", repo.lastHitFromMemory)
    }
}
