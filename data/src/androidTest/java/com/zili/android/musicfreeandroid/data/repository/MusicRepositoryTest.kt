package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MusicRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = MusicRepository(db.musicDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun musicItem(id: String, platform: String = "local") = MusicItem(
        id = id, platform = platform, title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun insertAndGetById() = runTest {
        val item = musicItem("1")
        repo.insert(item)
        val result = repo.getById("1", "local")
        assertEquals(item, result)
    }

    @Test
    fun observeAll_emitsOnChange() = runTest {
        repo.observeAll().test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            repo.insert(musicItem("1"))
            assertEquals(1, awaitItem().size)
            repo.insert(musicItem("2"))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRemovesItem() = runTest {
        val item = musicItem("1")
        repo.insert(item)
        repo.delete(item)
        assertNull(repo.getById("1", "local"))
    }

    @Test
    fun insertPreservesQualities() = runTest {
        val item = musicItem("1").copy(
            qualities = mapOf(PlayQuality.HIGH to QualityInfo("url", 5_000_000L))
        )
        repo.insert(item)
        val result = repo.getById("1", "local")
        assertEquals(1, result!!.qualities!!.size)
        assertEquals("url", result.qualities!![PlayQuality.HIGH]!!.url)
    }
}
