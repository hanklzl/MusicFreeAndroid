package com.hank.musicfree.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.PlayQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayQueueDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PlayQueueDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playQueueDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun queueEntity(musicId: String, sortOrder: Int) = PlayQueueEntity(
        musicId = musicId,
        musicPlatform = "local",
        title = "Song $musicId",
        artist = "Artist",
        album = null,
        duration = 180_000,
        url = null,
        artwork = null,
        qualitiesJson = null,
        sortOrder = sortOrder,
    )

    @Test
    fun insertAndGetAll() = runTest {
        dao.insertAll(listOf(queueEntity("1", 0), queueEntity("2", 1)))
        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals("Song 1", all[0].title)
        assertEquals("Song 2", all[1].title)
    }

    @Test
    fun clearAll() = runTest {
        dao.insertAll(listOf(queueEntity("1", 0)))
        dao.clearAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun observeAll() = runTest {
        dao.insertAll(listOf(queueEntity("b", 1), queueEntity("a", 0)))
        val items = dao.observeAll().first()
        assertEquals(2, items.size)
        assertEquals("Song a", items[0].title)
    }

    @Test
    fun count() = runTest {
        assertEquals(0, dao.count())
        dao.insertAll(listOf(queueEntity("1", 0), queueEntity("2", 1), queueEntity("3", 2)))
        assertEquals(3, dao.count())
    }
}
