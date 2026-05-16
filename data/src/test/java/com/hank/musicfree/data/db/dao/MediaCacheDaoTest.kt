package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.MediaCacheEntity
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
class MediaCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.upsert(MediaCacheEntity("kugou", "3", "{}", 300))

        dao.deleteByPlatform("kuwo")

        assertNull(dao.get("kuwo", "1"))
        assertNull(dao.get("kuwo", "2"))
        assertNotNull(dao.get("kugou", "3"))
        assertEquals(1, dao.count())
    }

    @Test fun `delete removes only matching row`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.delete("kuwo", "1")
        assertNull(dao.get("kuwo", "1"))
        assertNotNull(dao.get("kuwo", "2"))
    }

    @Test fun `deleteAll removes every row`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kugou", "2", "{}", 200))

        dao.deleteAll()

        assertEquals(0, dao.count())
    }

    @Test fun `totalSizeBytes sums source json bytes and oldest entries are ordered`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "12345", 300))
        dao.upsert(MediaCacheEntity("kuwo", "2", "123", 100))
        dao.upsert(MediaCacheEntity("kuwo", "3", "1234", 200))

        assertEquals(12L, dao.totalSizeBytes())
        assertEquals(listOf("2", "3", "1"), dao.getOldestEntries().map { it.id })
    }
}
