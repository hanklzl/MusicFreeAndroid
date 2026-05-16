package com.hank.musicfree.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.DownloadTaskEntity
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.downloader.io.NetworkState
import com.hank.musicfree.downloader.model.DownloadStatus
import com.hank.musicfree.downloader.prefs.DownloadConfig
import java.util.concurrent.Executor
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineRecoveryTest {

    private lateinit var db: AppDatabase
    private lateinit var converters: Converters
    private lateinit var musicRepository: MusicRepository

    @Before fun setup() {
        val syncExec = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().setQueryExecutor(syncExec).setTransactionExecutor(syncExec).build()
        converters = Converters()
        musicRepository = MusicRepository(db, db.musicDao(), converters)
    }

    @After fun teardown() { db.close() }

    @Test fun startResetsInflightToPendingAndClearsResolved() = runTest {
        val now = System.currentTimeMillis()
        db.downloadTaskDao().upsert(
            DownloadTaskEntity(
                id = "p", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
                durationMs = 0L, targetQuality = "standard", status = "PREPARING",
                errorReason = null, seedUrl = null, resolvedUrl = "https://x/p.mp3", resolvedHeadersJson = null,
                fileSize = null, downloadedSize = null, createdAt = now, updatedAt = now,
            ),
        )
        db.downloadTaskDao().upsert(
            DownloadTaskEntity(
                id = "d", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
                durationMs = 0L, targetQuality = "standard", status = "DOWNLOADING",
                errorReason = null, seedUrl = null, resolvedUrl = "https://x/d.mp3", resolvedHeadersJson = null,
                fileSize = 100, downloadedSize = 50, createdAt = now, updatedAt = now,
            ),
        )
        val engine = DownloadEngine(
            taskDao = db.downloadTaskDao(), downloadedDao = db.downloadedTrackDao(),
            http = FakeHttpDownloader(), writer = FakeMediaStoreWriter().asWriter(),
            resolver = FakeQualityResolver()::resolve,
            converters = converters, musicRepository = musicRepository,
            configFlow = MutableStateFlow(
                DownloadConfig(2, false, PlayQuality.STANDARD, QualityFallbackOrder.Asc, "Music/MusicFree/"),
            ),
            networkFlow = MutableStateFlow(NetworkState.Offline),  // keep scheduler from picking up the rows after reset
            cacheDir = createTempDirectory("dlrec").toFile(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        engine.start()
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first().associateBy { it.id }
        assertEquals(DownloadStatus.PENDING.name, rows["p"]!!.status)
        assertEquals(DownloadStatus.PENDING.name, rows["d"]!!.status)
        assertEquals(null, rows["p"]!!.resolvedUrl)
        assertEquals(null, rows["d"]!!.resolvedUrl)
        engine.stop()
    }

    @Test fun corruptedTaskSeedFallsBackToLegacyFieldsAndCanCompleteDownload() = runTest {
        val now = System.currentTimeMillis()
        db.downloadTaskDao().upsert(
            DownloadTaskEntity(
                id = "legacy", platform = "qq", title = "legacy-title", artist = "legacy-artist",
                album = "legacy-album", artwork = null, durationMs = 1L, targetQuality = "standard",
                status = "PENDING", errorReason = null, seedUrl = "https://x/legacy.mp3", resolvedUrl = null,
                resolvedHeadersJson = null, fileSize = null, downloadedSize = null, createdAt = now, updatedAt = now,
                musicItemJson = "{bad",
            ),
        )

        val writer = FakeMediaStoreWriter()
        val engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = FakeHttpDownloader(),
            writer = writer.asWriter(),
            resolver = FakeQualityResolver()::resolve,
            converters = converters,
            musicRepository = musicRepository,
            configFlow = MutableStateFlow(
                DownloadConfig(2, false, PlayQuality.STANDARD, QualityFallbackOrder.Asc, "Music/MusicFree/"),
            ),
            networkFlow = MutableStateFlow(NetworkState.Wifi),
            cacheDir = createTempDirectory("dlrec-legacy").toFile(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        engine.start()
        advanceUntilIdle()

        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(0, rows.size)
        assertTrue(db.downloadedTrackDao().exists("legacy", "qq"))
        val local = musicRepository.getById("legacy", "qq")
        assertNotNull(local)
        assertEquals("legacy", local!!.id)
        assertEquals("qq", local.platform)
    }
}
