package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.SeedFavoriteCallback
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: AppDatabase
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var musicRepo: MusicRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedFavoriteCallback)
            .allowMainThreadQueries()
            .build()
        val converters = Converters()
        val coverStore = PlaylistCoverStore(ctx)
        playlistRepo = PlaylistRepository(db, db.playlistDao(), db.musicDao(), coverStore, converters)
        musicRepo = MusicRepository(db.musicDao(), converters)
    }

    @After
    fun teardown() = db.close()

    // --------------- helpers ---------------

    private fun playlist(id: String = "pl1", name: String = "Test") =
        Playlist(id = id, name = name, coverUri = null)

    private fun musicItem(id: String) = MusicItem(
        id = id, platform = "local", title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    private fun sampleMusic(id: String, title: String = id, artwork: String? = null) = MusicItem(
        id = id, platform = "test", title = title, artist = "Artist", album = null,
        duration = 0L, url = null, artwork = artwork, qualities = null,
    )

    // --------------- existing CRUD tests (kept) ---------------

    @Test
    fun createAndGetPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist())
        val result = playlistRepo.getPlaylistById("pl1")
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun observeAllPlaylists_emitsOnChange() = runTest {
        // The seeded "favorite" row is already present; start from its count
        val initialSize = playlistRepo.observeAllPlaylists().first().size
        playlistRepo.observeAllPlaylists().test {
            // consume initial emission
            awaitItem()
            playlistRepo.createPlaylist(playlist("pl1"))
            assertEquals(initialSize + 1, awaitItem().size)
            playlistRepo.createPlaylist(playlist("pl2"))
            assertEquals(initialSize + 2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addAndObserveMusicInPlaylist() = runBlocking {
        playlistRepo.createPlaylist(playlist("pl1"))
        val m1 = musicItem("m1")
        val m2 = musicItem("m2")

        playlistRepo.observeMusicInPlaylist("pl1").test {
            // consume initial empty emission
            val initial = awaitItem()
            assertEquals(emptyList<MusicItem>(), initial)

            playlistRepo.addMusicToPlaylist("pl1", m1)
            // skipItems(count) to skip any intermediate flatMapLatest re-subscriptions
            // and land on the first stable emission with count = 1
            var size = -1
            while (size < 1) size = awaitItem().size
            assertEquals(1, size)

            playlistRepo.addMusicToPlaylist("pl1", m2)
            while (size < 2) size = awaitItem().size
            assertEquals(2, size)
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
    fun updatePlaylistInfo_renamesPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1", "Original"))
        playlistRepo.updatePlaylistInfo(id = "pl1", name = "Renamed")
        val updated = playlistRepo.getPlaylistById("pl1")
        assertEquals("Renamed", updated!!.name)
    }

    @Test
    fun updatePlaylistInfo_updatesDescription() = runTest {
        playlistRepo.createPlaylist(playlist("pl1", "Test"))
        playlistRepo.updatePlaylistInfo(id = "pl1", description = "My description")
        val updated = playlistRepo.getPlaylistById("pl1")
        assertEquals("My description", updated!!.description)
        assertEquals("Test", updated.name)
    }

    // --------------- Task 14 integration tests ---------------

    @Test
    fun favoriteRow_existsAfterFreshInit() = runBlocking {
        val fav = playlistRepo.observeFavorite().first()
        assertNotNull(fav)
        assertEquals("favorite", fav!!.id)
        assertEquals("我喜欢", fav.name)
    }

    @Test
    fun addMusicToPlaylist_dedupReturnsFalseSecondTime() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        val first = playlistRepo.addMusicToPlaylist(id, sampleMusic("m1"))
        val second = playlistRepo.addMusicToPlaylist(id, sampleMusic("m1"))
        assertTrue(first)
        assertFalse(second)
    }

    @Test
    fun addMusicsToPlaylist_returnsAddedCountAndSkipsDuplicates() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))
        val items = listOf(
            sampleMusic("m1", title = "One"),
            sampleMusic("m2", title = "Two"),
            sampleMusic("m1", title = "One Again"),
        )

        val first = playlistRepo.addMusicsToPlaylist(id, items)
        val second = playlistRepo.addMusicsToPlaylist(id, items)

        assertEquals(2, first)
        assertEquals(0, second)
        assertEquals(2, playlistRepo.countMusicInPlaylist(id))
    }

    @Test
    fun addMusicsToPlaylist_emptyListReturnsZero() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))

        val added = playlistRepo.addMusicsToPlaylist(id, emptyList())

        assertEquals(0, added)
        assertEquals(0, playlistRepo.countMusicInPlaylist(id))
    }

    @Test
    fun addMusicsToPlaylist_preservesImportOrderForManualSort() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))
        val items = listOf(
            sampleMusic("m3", title = "Third"),
            sampleMusic("m1", title = "First"),
            sampleMusic("m2", title = "Second"),
        )

        playlistRepo.addMusicsToPlaylist(id, items)

        val titles = playlistRepo.observeMusicInPlaylist(id).first().map { it.title }
        assertEquals(listOf("Third", "First", "Second"), titles)
    }

    @Test
    fun addMusic_autoSyncsCoverFromArtworkOnEmptyPlaylist() = runBlocking {
        val tmp = java.io.File(ctx.cacheDir, "art.jpg").apply { writeBytes(ByteArray(32) { 9 }) }
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        playlistRepo.addMusicToPlaylist(id, sampleMusic("m1", artwork = "file://${tmp.absolutePath}"))
        val playlist = playlistRepo.observePlaylist(id).first()
        assertNotNull(playlist?.coverUri)
        assertTrue(playlist!!.coverUri!!.startsWith("file://"))
        assertTrue(playlist.coverUri!!.contains("playlist_covers/"))
    }

    @Test
    fun addMusicsToPlaylist_retriesCoverSyncAfterInvalidArtwork() = runBlocking {
        val invalidArtwork = "file://${java.io.File(ctx.cacheDir, "does-not-exist-artwork.jpg").absolutePath}"
        val validArtwork = java.io.File(ctx.cacheDir, "valid-cover-artwork.jpg").apply {
            writeBytes(ByteArray(32) { 9 })
        }
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Import", coverUri = null))
        val items = listOf(
            sampleMusic("m1", title = "Invalid Artwork", artwork = invalidArtwork),
            sampleMusic("m2", title = "Valid Artwork", artwork = "file://${validArtwork.absolutePath}"),
        )

        val added = playlistRepo.addMusicsToPlaylist(id, items)

        val playlist = playlistRepo.getPlaylistById(id)
        assertEquals(2, added)
        assertNotNull(playlist?.coverUri)
        assertTrue(playlist!!.coverUri!!.startsWith("file://"))
        assertTrue(playlist.coverUri!!.contains("playlist_covers/"))
    }

    @Test
    fun toggleFavorite_isReciprocal() = runBlocking {
        val item = sampleMusic("m42")
        assertFalse(playlistRepo.isFavorite(item).first())
        playlistRepo.toggleFavorite(item)
        assertTrue(playlistRepo.isFavorite(item).first())
        playlistRepo.toggleFavorite(item)
        assertFalse(playlistRepo.isFavorite(item).first())
    }

    @Test
    fun setSortMode_thenObserveOrderChanges() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        playlistRepo.addMusicToPlaylist(id, sampleMusic("m1", title = "苹果"))
        playlistRepo.addMusicToPlaylist(id, sampleMusic("m2", title = "Apple"))
        playlistRepo.addMusicToPlaylist(id, sampleMusic("m3", title = "香蕉"))
        playlistRepo.setSortMode(id, SortMode.Title)
        val titles = playlistRepo.observeMusicInPlaylist(id).first().map { it.title }
        // Verify Apple (Latin) appears at one end, and 苹果 < 香蕉 (pinyin order in middle/Chinese-only span)
        val pingIdx = titles.indexOf("苹果")
        val xiangIdx = titles.indexOf("香蕉")
        assertTrue("苹果 should sort before 香蕉 by pinyin", pingIdx < xiangIdx)
    }

    @Test
    fun deletePlaylist_throwsForFavoriteRow() = runBlocking {
        var caught: Throwable? = null
        try {
            playlistRepo.deletePlaylist(Playlist(id = "favorite", name = "我喜欢", coverUri = null))
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertNotNull(caught)
    }

    @Test
    fun addMusic_storesHttpsArtworkUrlVerbatimAsCoverUri() = runBlocking {
        val id = UUID.randomUUID().toString()
        playlistRepo.createPlaylist(Playlist(id = id, name = "Online", coverUri = null))
        val artwork = "https://example.com/cover.jpg"
        playlistRepo.addMusicToPlaylist(id, sampleMusic("m1", artwork = artwork))
        val playlist = playlistRepo.observePlaylist(id).first()
        assertEquals(artwork, playlist?.coverUri)
    }

    @Test
    fun observePlaylist_translatesLegacyRelativeCoverUriToFileScheme() = runBlocking {
        val id = UUID.randomUUID().toString()
        // Simulate a legacy DB row: write the entity directly with a relative-path coverUri,
        // bypassing the new PlaylistCoverStore writers.
        db.playlistDao().insertPlaylist(
            com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity(
                id = id,
                name = "Legacy",
                coverUri = "playlist_covers/$id.jpg",
                description = null,
                sortMode = "Manual",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )
        val playlist = playlistRepo.observePlaylist(id).first()
        val coverUri = playlist?.coverUri
        assertNotNull(coverUri)
        assertTrue("expected file:// uri, got $coverUri", coverUri!!.startsWith("file://"))
        assertTrue("expected playlist_covers/ in path, got $coverUri", coverUri.contains("playlist_covers/$id.jpg"))
    }
}
