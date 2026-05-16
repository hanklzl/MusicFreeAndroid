package com.hank.musicfree.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.data.cover.PlaylistCoverStore
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.SeedFavoriteCallback
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryFavoriteRecoveryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val converters = Converters()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedFavoriteCallback)
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepository(
            db = db,
            playlistDao = db.playlistDao(),
            musicDao = db.musicDao(),
            coverStore = PlaylistCoverStore(context),
            converters = converters,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun observeAllPlaylists_restoresMissingDefaultFavoritePlaylist() = runTest {
        db.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)

        val favorite = repository.observeAllPlaylists()
            .first()
            .singleOrNull { it.id == Playlist.DEFAULT_FAVORITE_ID }

        assertNotNull(favorite)
        assertEquals(Playlist.DEFAULT_FAVORITE_ID, favorite!!.id)
        assertEquals(Playlist.DEFAULT_FAVORITE_NAME, favorite.name)
    }

    @Test
    fun observeFavorite_restoresMissingDefaultFavoritePlaylist() = runTest {
        db.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)

        val favorite = repository.observeFavorite().first()

        assertNotNull(favorite)
        assertEquals(Playlist.DEFAULT_FAVORITE_ID, favorite!!.id)
        assertEquals(Playlist.DEFAULT_FAVORITE_NAME, favorite.name)
    }

    @Test
    fun isFavorite_restoresMissingDefaultFavoritePlaylistWithoutAddingMembership() = runTest {
        val item = musicItem()
        db.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)

        val favoriteState = repository.isFavorite(item).first()

        val favorite = db.playlistDao().getPlaylistById(Playlist.DEFAULT_FAVORITE_ID)
        assertNotNull(favorite)
        assertEquals(Playlist.DEFAULT_FAVORITE_NAME, favorite!!.name)
        assertEquals(false, favoriteState)
    }

    @Test
    fun toggleFavorite_restoresMissingDefaultFavoritePlaylistBeforeAddingMusic() = runTest {
        val item = musicItem()
        db.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)

        repository.toggleFavorite(item)

        val favorite = db.playlistDao().getPlaylistById(Playlist.DEFAULT_FAVORITE_ID)
        assertNotNull(favorite)
        assertEquals(Playlist.DEFAULT_FAVORITE_NAME, favorite!!.name)
        assertTrue(repository.isFavorite(item).first())
    }

    @Test
    fun favoriteRecovery_doesNotOverwriteExistingFavoriteMetadata() = runTest {
        val existing = PlaylistEntity(
            id = Playlist.DEFAULT_FAVORITE_ID,
            name = "自定义喜欢",
            coverUri = "https://example.com/favorite.jpg",
            description = "kept description",
            sortMode = "Title",
            createdAt = 123L,
            updatedAt = 456L,
        )
        db.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)
        db.playlistDao().insertPlaylist(existing)

        repository.observeAllPlaylists().first()
        repository.observeFavorite().first()

        val favorite = requireNotNull(db.playlistDao().getPlaylistById(Playlist.DEFAULT_FAVORITE_ID))
        assertEquals(existing.name, favorite.name)
        assertEquals(existing.coverUri, favorite.coverUri)
        assertEquals(existing.description, favorite.description)
        assertEquals(existing.sortMode, favorite.sortMode)
        assertEquals(existing.createdAt, favorite.createdAt)
        assertEquals(existing.updatedAt, favorite.updatedAt)
    }

    @Test
    fun seedFavoriteCallback_onOpenRestoresMissingDefaultFavoritePlaylist() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "favorite-recovery-${UUID.randomUUID()}.db"
        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(SeedFavoriteCallback)
            .allowMainThreadQueries()
            .build()
        firstDb.playlistDao().deletePlaylistById(Playlist.DEFAULT_FAVORITE_ID)
        firstDb.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(SeedFavoriteCallback)
            .allowMainThreadQueries()
            .build()
        try {
            val favorite = reopenedDb.playlistDao().getPlaylistById(Playlist.DEFAULT_FAVORITE_ID)
            assertNotNull(favorite)
            assertEquals(Playlist.DEFAULT_FAVORITE_NAME, favorite!!.name)
        } finally {
            reopenedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun musicItem() = MusicItem(
        id = "song-1",
        platform = "fixture",
        title = "Song 1",
        artist = "Artist",
        album = null,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
