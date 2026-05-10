package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun upsertAndGet() = runTest {
        dao.upsert(entity("kg", "1", "{\"STANDARD\":{\"url\":\"http://a\"}}", 100L))
        val row = dao.get("kg", "1")
        assertEquals(100L, row?.updatedAt)
        assertEquals("{\"STANDARD\":{\"url\":\"http://a\"}}", row?.sourcesJson)
    }

    @Test
    fun upsertReplacesExisting() = runTest {
        dao.upsert(entity("kg", "1", "{}", 100L))
        dao.upsert(entity("kg", "1", "{\"HIGH\":{\"url\":\"http://b\"}}", 200L))
        val row = dao.get("kg", "1")
        assertEquals(200L, row?.updatedAt)
        assertEquals("{\"HIGH\":{\"url\":\"http://b\"}}", row?.sourcesJson)
    }

    @Test
    fun differentPlatformsCoexist() = runTest {
        dao.upsert(entity("kg", "1", "{}", 100L))
        dao.upsert(entity("wy", "1", "{}", 100L))
        assertEquals(2, dao.count())
    }

    @Test
    fun deleteOldestRemovesByAscUpdatedAt() = runTest {
        dao.upsert(entity("kg", "1", "{}", 100L))
        dao.upsert(entity("kg", "2", "{}", 200L))
        dao.upsert(entity("kg", "3", "{}", 300L))
        dao.deleteOldest(2)
        assertEquals(1, dao.count())
        assertNull(dao.get("kg", "1"))
        assertNull(dao.get("kg", "2"))
        assertEquals(300L, dao.get("kg", "3")?.updatedAt)
    }

    private fun entity(p: String, id: String, json: String, updated: Long) =
        MediaCacheEntity(platform = p, id = id, sourcesJson = json, updatedAt = updated)
}
