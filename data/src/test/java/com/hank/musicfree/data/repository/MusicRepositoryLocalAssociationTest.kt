package com.hank.musicfree.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicRepositoryLocalAssociationTest {

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

    @Test
    fun `clearLocalPlaybackAssociation removes downloaded state from plugin track only`() = runTest {
        val item = musicItem("302986918", platform = "元力QQ")
        repo.commitDownloadedTrack(item, downloadedRow("302986918", "元力QQ"))

        val cleared = repo.clearLocalPlaybackAssociation("元力QQ", "302986918")

        assertTrue(cleared)
        assertFalse(db.downloadedTrackDao().exists("302986918", "元力QQ"))
        val retained = repo.getById("302986918", "元力QQ")!!
        assertNull(retained.localPath)
        assertFalse(retained.raw.containsKey("downloaded"))
        assertFalse(retained.raw.containsKey("downloadQuality"))
        assertFalse(retained.raw.containsKey("downloadedAt"))
        assertFalse(retained.raw.containsKey("mediaStoreUri"))
        assertEquals(emptyList<MusicItem>(), repo.observeLocalLibrary().first())
    }

    @Test
    fun `clearLocalPlaybackAssociation keeps scanned local track`() = runTest {
        val local = musicItem("1000008551", platform = "local")
        repo.insert(local)

        val cleared = repo.clearLocalPlaybackAssociation("local", "1000008551")

        assertFalse(cleared)
        assertEquals(local, repo.getById("1000008551", "local"))
    }

    private fun musicItem(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 180_000,
        url = null,
        artwork = null,
        qualities = null,
    )

    private fun downloadedRow(id: String, platform: String) = DownloadedTrackEntity(
        id = id,
        platform = platform,
        mediaStoreUri = "content://media/external/audio/media/$id",
        relativePath = "Music/MusicFree/",
        mimeType = "audio/mpeg",
        quality = "standard",
        sizeBytes = 1024L,
        downloadedAt = 123L,
    )
}
