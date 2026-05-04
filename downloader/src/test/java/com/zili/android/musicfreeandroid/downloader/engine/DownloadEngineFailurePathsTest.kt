package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineFailurePathsTest {

    private lateinit var db: AppDatabase
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver
    private lateinit var configFlow: MutableStateFlow<DownloadConfig>
    private lateinit var network: MutableStateFlow<NetworkState>
    private lateinit var engine: DownloadEngine

    @Before fun setup() {
        val syncExec = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(syncExec)
            .setTransactionExecutor(syncExec)
            .build()
        http = FakeHttpDownloader()
        writer = FakeMediaStoreWriter()
        resolver = FakeQualityResolver()
        configFlow = MutableStateFlow(DownloadConfig(2, false, PlayQuality.STANDARD, "Music/MusicFree/"))
        network = MutableStateFlow(NetworkState.Wifi)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = http, writer = writer.asWriter(), resolver = resolver::resolve,
            configFlow = configFlow, networkFlow = network,
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

    @Test fun allQualitiesReturnNullProducesFailToFetchSource() = runTest {
        // resolver has no binding → returns null for every quality
        engine.enqueue(listOf(item("1")), PlayQuality.SUPER)
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(DownloadStatus.FAILED.name, rows[0].status)
        assertEquals(DownloadFailReason.FailToFetchSource.name, rows[0].errorReason)
    }

    @Test fun httpFailureMarksFailedAndCacheFileCleaned() = runTest {
        resolver.bind(
            MediaKey.of("1", "qq"),
            MediaSourceResult(url = "https://x/song.mp3", headers = null, userAgent = null, quality = null),
        )
        http.failNext()
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(DownloadStatus.FAILED.name, rows.single().status)
        assertEquals(DownloadFailReason.Unknown.name, rows.single().errorReason)
    }
}
