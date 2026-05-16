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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCacheRepositoryLruTest {
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

    @Test fun `second get hits memory after first get warms cache`() = runTest {
        repo.put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))

        // First get reads DB and seeds memory layer
        val first = repo.get(item, PlayQuality.STANDARD)
        assertNotNull(first)
        assertEquals("http://a", first?.url)
        assertFalse("first read should come from DB", repo.lastHitFromMemory)

        // Second get should hit memory
        val second = repo.get(item, PlayQuality.STANDARD)
        assertNotNull(second)
        assertEquals("http://a", second?.url)
        assertTrue("second read should come from memory", repo.lastHitFromMemory)
    }
}
