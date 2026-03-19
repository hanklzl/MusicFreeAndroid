package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayQueueRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlayQueueRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = PlayQueueRepository(db.playQueueDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun musicItem(id: String) = MusicItem(
        id = id, platform = "local", title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun saveAndGetQueue() = runTest {
        val items = listOf(musicItem("1"), musicItem("2"), musicItem("3"))
        repo.saveQueue(items)
        val result = repo.getQueue()
        assertEquals(3, result.size)
        assertEquals("Song 1", result[0].title)
        assertEquals("Song 3", result[2].title)
    }

    @Test
    fun saveQueueReplacesExisting() = runTest {
        repo.saveQueue(listOf(musicItem("1"), musicItem("2")))
        repo.saveQueue(listOf(musicItem("3")))
        val result = repo.getQueue()
        assertEquals(1, result.size)
        assertEquals("Song 3", result[0].title)
    }

    @Test
    fun clearQueue() = runTest {
        repo.saveQueue(listOf(musicItem("1")))
        repo.clearQueue()
        assertEquals(0, repo.count())
    }

    @Test
    fun observeQueue_emitsOnChange() = runTest {
        repo.observeQueue().test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            repo.saveQueue(listOf(musicItem("1"), musicItem("2")))
            val updated = awaitItem()
            assertEquals(2, updated.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
