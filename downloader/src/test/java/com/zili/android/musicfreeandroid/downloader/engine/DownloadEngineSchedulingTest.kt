package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineSchedulingTest {

    private lateinit var db: AppDatabase
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver
    private lateinit var configFlow: MutableStateFlow<DownloadConfig>
    private lateinit var network: MutableStateFlow<NetworkState>
    private lateinit var engine: DownloadEngine

    @Before fun setup() {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        http = FakeHttpDownloader()
        writer = FakeMediaStoreWriter()
        resolver = FakeQualityResolver()
        configFlow = MutableStateFlow(DownloadConfig(maxDownload = 2, useCellularDownload = false,
            defaultDownloadQuality = PlayQuality.STANDARD, downloadDirRelative = "Music/MusicFree/"))
        network = MutableStateFlow(NetworkState.Wifi)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = http,
            writer = writer.asWriter(),
            resolver = resolver::resolve,
            configFlow = configFlow,
            networkFlow = network,
            cacheDir = createTempDirectory("dlcache").toFile(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close() }

    private fun item(id: String, platform: String = "qq") = MusicItem(
        id = id, platform = platform, title = "t-$id", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun singleEnqueueRunsToCompletion() = runTest {
        resolver.bind(MediaKey.of("1", "qq"),
            MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null, quality = null))
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
        assertEquals(1, writer.commitCount)
    }

    @Test fun concurrencyCapNeverExceeded() = runTest {
        configFlow.value = configFlow.value.copy(maxDownload = 2)
        repeat(5) { i ->
            resolver.bind(MediaKey.of("$i", "qq"),
                MediaSourceResult(url = "https://x/$i.mp3", headers = null, userAgent = null, quality = null))
        }
        http.holdNextN(5)
        engine.enqueue((0..4).map { item("$it") }, PlayQuality.STANDARD)
        advanceUntilIdle()
        assertTrue("inflight=${http.inflight}", http.inflight <= 2)
        http.releaseAll()
        advanceUntilIdle()
        assertEquals(5, writer.commitCount)
    }

    @Test fun alreadyDownloadedDeduplicated() = runTest {
        db.downloadedTrackDao().insert(
            com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity(
                id = "1", platform = "qq",
                mediaStoreUri = "content://media/external/audio/media/1",
                relativePath = "Music/MusicFree/", mimeType = "audio/mpeg",
                quality = "standard", sizeBytes = 1L, downloadedAt = 1L,
            )
        )
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertEquals(0, writer.commitCount)
    }
}
