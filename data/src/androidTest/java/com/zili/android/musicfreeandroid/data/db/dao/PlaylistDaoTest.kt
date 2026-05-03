package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var musicDao: MusicDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        playlistDao = db.playlistDao()
        musicDao = db.musicDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun playlist(id: String = "pl1", name: String = "Test") =
        PlaylistEntity(id = id, name = name, coverUri = null, createdAt = 1000L, updatedAt = 2000L)

    private fun music(id: String, platform: String = "local") =
        MusicItemEntity(id, platform, "Song $id", "Artist", null, 180_000, null, null, null)

    @Test
    fun insertAndGetPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist())
        val result = playlistDao.getPlaylistById("pl1")
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun deletePlaylist() = runTest {
        val pl = playlist()
        playlistDao.insertPlaylist(pl)
        playlistDao.deletePlaylist(pl)
        assertNull(playlistDao.getPlaylistById("pl1"))
    }

    @Test
    fun observeAllPlaylists() = runTest {
        playlistDao.insertPlaylist(playlist("pl1", "First"))
        playlistDao.insertPlaylist(playlist("pl2", "Second"))
        val all = playlistDao.observeAllPlaylists().first()
        assertEquals(2, all.size)
    }

    @Test
    fun addAndObserveMusicInPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        musicDao.insert(music("m2"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m2", "local", 1))

        val items = playlistDao.observeMusicInPlaylist("pl1").first()
        assertEquals(2, items.size)
        assertEquals("Song m1", items[0].title)
    }

    @Test
    fun removeMusicFromPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.removeMusicFromPlaylist("pl1", "m1", "local")
        assertEquals(0, playlistDao.countMusicInPlaylist("pl1"))
    }

    @Test
    fun cascadeDeletePlaylist_removesCrossRefs() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.deletePlaylist(playlist("pl1"))
        assertNotNull(musicDao.getById("m1", "local"))
        assertEquals(0, playlistDao.countMusicInPlaylist("pl1"))
    }

    @Test
    fun maxSortOrderInPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        assertEquals(-1, playlistDao.maxSortOrderInPlaylist("pl1"))
        musicDao.insert(music("m1"))
        musicDao.insert(music("m2"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m2", "local", 5))
        assertEquals(5, playlistDao.maxSortOrderInPlaylist("pl1"))
    }
}
