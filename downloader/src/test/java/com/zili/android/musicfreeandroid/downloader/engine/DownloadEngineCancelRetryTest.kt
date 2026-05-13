package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineCancelRetryTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: DownloadEngine
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver
    private lateinit var converters: Converters
    private lateinit var musicRepository: MusicRepository

    @Before fun setup() {
        val syncExec = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().setQueryExecutor(syncExec).setTransactionExecutor(syncExec).build()
        http = FakeHttpDownloader(); writer = FakeMediaStoreWriter(); resolver = FakeQualityResolver()
        converters = Converters()
        musicRepository = MusicRepository(db, db.musicDao(), converters)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(), downloadedDao = db.downloadedTrackDao(),
            http = http, writer = writer.asWriter(), resolver = resolver::resolve,
            converters = converters, musicRepository = musicRepository,
            configFlow = MutableStateFlow(
                DownloadConfig(1, false, PlayQuality.STANDARD, QualityFallbackOrder.Asc, "Music/MusicFree/"),
            ),
            networkFlow = MutableStateFlow(NetworkState.Wifi),
            cacheDir = createTempDirectory("dlcache").toFile(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close() }

    private fun item(id: String) = MusicItem(
        id = id, platform = "qq", title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun cancelInflightRemovesRow() = runTest {
        resolver.bind(MediaKey.of("1", "qq"),
            MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null, quality = null))
        http.holdNextN(1)            // suspend the inflight job at the gate
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        engine.cancel(MediaKey.of("1", "qq"))
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
    }

    @Test fun retryFailedResetsToPendingAndRunsAgain() = runTest {
        resolver.bind(MediaKey.of("1", "qq"),
            MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null, quality = null))
        http.failNext()
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED.name, db.downloadTaskDao().observeAll().first().single().status)
        engine.retry(MediaKey.of("1", "qq"))
        advanceUntilIdle()
        // Now should succeed: row removed, downloaded_tracks present
        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
    }

    @Test fun clearFailedDeletesOnlyFailedRows() = runTest {
        // null binding → resolver returns null for every quality → FailToFetchSource
        resolver.bind(MediaKey.of("1", "qq"), null)
        engine.enqueue(listOf(item("1")), PlayQuality.LOW)
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED.name, db.downloadTaskDao().observeAll().first().single().status)
        engine.clearFailed()
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
    }
}
