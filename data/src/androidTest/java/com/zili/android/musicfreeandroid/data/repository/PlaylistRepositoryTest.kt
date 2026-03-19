package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var musicRepo: MusicRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val converters = Converters()
        playlistRepo = PlaylistRepository(db.playlistDao(), converters)
        musicRepo = MusicRepository(db.musicDao(), converters)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun playlist(id: String = "pl1", name: String = "Test") =
        Playlist(id = id, name = name, coverUri = null)

    private fun musicItem(id: String) = MusicItem(
        id = id, platform = "local", title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun createAndGetPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist())
        val result = playlistRepo.getPlaylistById("pl1")
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun observeAllPlaylists_emitsOnChange() = runTest {
        playlistRepo.observeAllPlaylists().test {
            assertEquals(emptyList<Playlist>(), awaitItem())
            playlistRepo.createPlaylist(playlist("pl1"))
            assertEquals(1, awaitItem().size)
            playlistRepo.createPlaylist(playlist("pl2"))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addAndObserveMusicInPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1"))
        val m1 = musicItem("m1")
        val m2 = musicItem("m2")
        musicRepo.insert(m1)
        musicRepo.insert(m2)

        playlistRepo.observeMusicInPlaylist("pl1").test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            playlistRepo.addMusicToPlaylist("pl1", m1)
            assertEquals(1, awaitItem().size)
            playlistRepo.addMusicToPlaylist("pl1", m2)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeMusicFromPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1"))
        val m1 = musicItem("m1")
        musicRepo.insert(m1)
        playlistRepo.addMusicToPlaylist("pl1", m1)
        playlistRepo.removeMusicFromPlaylist("pl1", m1)
        assertEquals(0, playlistRepo.countMusicInPlaylist("pl1"))
    }

    @Test
    fun deletePlaylistCascades() = runTest {
        val pl = playlist("pl1")
        playlistRepo.createPlaylist(pl)
        musicRepo.insert(musicItem("m1"))
        playlistRepo.addMusicToPlaylist("pl1", musicItem("m1"))
        playlistRepo.deletePlaylist(pl)
        assertNull(playlistRepo.getPlaylistById("pl1"))
        assertNotNull(musicRepo.getById("m1", "local"))
    }

    @Test
    fun updatePlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1", "Original"))
        playlistRepo.updatePlaylist(Playlist("pl1", "Renamed", "cover.jpg"))
        val updated = playlistRepo.getPlaylistById("pl1")
        assertEquals("Renamed", updated!!.name)
        assertEquals("cover.jpg", updated.coverUri)
    }
}
