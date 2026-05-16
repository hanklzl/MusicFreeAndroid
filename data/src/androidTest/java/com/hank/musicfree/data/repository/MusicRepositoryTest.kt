package com.hank.musicfree.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.first
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
        repo = MusicRepository(db, db.musicDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun musicItem(id: String, platform: String = "local") = MusicItem(
        id = id, platform = platform, title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    private fun downloadedRow(id: String, platform: String = "demo") = DownloadedTrackEntity(
        id = id,
        platform = platform,
        mediaStoreUri = "content://media/external/audio/media/$id",
        relativePath = "Music/MusicFree/",
        mimeType = "audio/mpeg",
        quality = "standard",
        sizeBytes = 1024L,
        downloadedAt = 123L,
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

    @Test
    fun observeLocalLibraryReturnsScannedLocalAndDownloadedPluginTracks() = runTest {
        val scanned = musicItem("local-1", platform = "local")
        val downloaded = musicItem("plugin-1", platform = "demo").copy(
            album = "Plugin Album",
            artwork = "https://example.test/cover.jpg",
            raw = mapOf("from" to "plugin"),
        )
        val remoteOnly = musicItem("plugin-2", platform = "demo")

        repo.insert(scanned)
        repo.insert(downloaded)
        repo.insert(remoteOnly)
        db.downloadedTrackDao().insert(downloadedRow("plugin-1", "demo"))
        db.downloadedTrackDao().insert(downloadedRow("orphan", "demo"))

        repo.observeLocalLibrary().test {
            val actual = awaitItem()
            assertEquals(listOf("local-1@local", "plugin-1@demo"), actual.map { "${it.id}@${it.platform}" }.sorted())
            assertEquals("https://example.test/cover.jpg", actual.first { it.id == "plugin-1" }.artwork)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeLocalLibraryReturnsDeterministicOrderWhenTitlesCollide() = runTest {
        val localA = musicItem("z", platform = "local").copy(title = "Same Title")
        val localB = musicItem("a", platform = "local").copy(title = "Same Title")
        val downloaded = musicItem("m", platform = "demo").copy(title = "Same Title")

        repo.insert(localA)
        repo.insert(localB)
        repo.insert(downloaded)
        db.downloadedTrackDao().insert(downloadedRow("m", "demo"))

        val actual = repo.observeLocalLibrary().first().map { "${it.platform}@${it.id}" }
        assertEquals(listOf("demo@m", "local@a", "local@z"), actual)
    }

    @Test
    fun commitDownloadedTrackWritesDownloadedRowAndFullMusicItem() = runTest {
        val item = musicItem("plugin-1", platform = "demo").copy(
            album = "Album",
            artwork = "https://example.test/art.jpg",
            raw = mapOf("source" to "plugin"),
            localPath = null,
        )
        val row = downloadedRow("plugin-1", "demo")

        repo.commitDownloadedTrack(item, row)

        assertTrue(db.downloadedTrackDao().exists("plugin-1", "demo"))
        val stored = repo.getById("plugin-1", "demo")!!
        assertEquals("Album", stored.album)
        assertEquals("https://example.test/art.jpg", stored.artwork)
        assertEquals(row.mediaStoreUri, stored.localPath)
        assertEquals("plugin", stored.raw["source"])
        assertEquals(true, stored.raw["downloaded"])
        assertEquals("standard", stored.raw["downloadQuality"])
        assertEquals(123L, (stored.raw["downloadedAt"] as Number).toLong())
        assertEquals(row.mediaStoreUri, stored.raw["mediaStoreUri"])
    }

    @Test
    fun commitDownloadedTrackMergesExistingMetadataWhenIncomingIsPartial() = runTest {
        val existing = musicItem("plugin-1", platform = "demo").copy(
            title = "Old Title",
            album = "Old Album",
            artwork = "https://example.test/old-cover.jpg",
            duration = 99_000,
            url = "https://example.test/old-url",
            qualities = mapOf(PlayQuality.HIGH to QualityInfo("old-url", 10_000L)),
            raw = mapOf("source" to "plugin", "kept" to "value"),
        )
        val partial = existing.copy(
            title = "   ",
            album = null,
            artwork = null,
            qualities = null,
            url = null,
            raw = mapOf("incoming" to "partial"),
            duration = 0L,
        )
        val row = downloadedRow("plugin-1", "demo")

        repo.insert(existing)
        repo.commitDownloadedTrack(partial, row)

        val stored = repo.getById("plugin-1", "demo")!!
        assertEquals("Old Title", stored.title)
        assertEquals("Old Album", stored.album)
        assertEquals("https://example.test/old-cover.jpg", stored.artwork)
        assertEquals("https://example.test/old-url", stored.url)
        assertEquals(99_000, stored.duration)
        assertEquals(existing.qualities, stored.qualities)
        assertEquals("value", stored.raw["kept"])
        assertEquals("plugin", stored.raw["source"])
        assertEquals("partial", stored.raw["incoming"])
        assertEquals(row.mediaStoreUri, stored.localPath)
        assertEquals(true, stored.raw["downloaded"])
        assertEquals("standard", stored.raw["downloadQuality"])
    }

    @Test
    fun removeFromLocalLibraryDeletesScannedLocalButOnlyClearsDownloadedStateForPluginTrack() = runTest {
        val scanned = musicItem("local-1", platform = "local")
        val plugin = musicItem("plugin-1", platform = "demo").copy(localPath = "content://media/external/audio/media/plugin-1")

        repo.insert(scanned)
        repo.commitDownloadedTrack(plugin, downloadedRow("plugin-1", "demo"))

        repo.removeFromLocalLibrary(scanned)
        repo.removeFromLocalLibrary(plugin)

        assertNull(repo.getById("local-1", "local"))
        assertFalse(db.downloadedTrackDao().exists("plugin-1", "demo"))
        val retained = repo.getById("plugin-1", "demo")!!
        assertNull(retained.localPath)
        assertFalse(retained.raw.containsKey("downloaded"))
        assertFalse(retained.raw.containsKey("downloadQuality"))
        assertFalse(retained.raw.containsKey("downloadedAt"))
        assertFalse(retained.raw.containsKey("mediaStoreUri"))
        assertEquals(emptyList<MusicItem>(), repo.observeLocalLibrary().first())
    }
}
