package com.hank.musicfree.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.downloader.io.NetworkState
import com.hank.musicfree.downloader.model.DownloadStatus
import com.hank.musicfree.downloader.model.MediaKey
import com.hank.musicfree.downloader.prefs.DownloadConfig
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.Executor
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private lateinit var converters: Converters
    private lateinit var musicRepository: MusicRepository
    private lateinit var logger: CapturingLogger

    @Before fun setup() {
        val directExecutor = Executor { it.run() }
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
        converters = Converters()
        musicRepository = MusicRepository(db, db.musicDao(), converters)
        logger = CapturingLogger()
        MfLog.install(logger)
        configFlow = MutableStateFlow(
            DownloadConfig(
                maxDownload = 2,
                useCellularDownload = false,
                defaultDownloadQuality = PlayQuality.STANDARD,
                downloadQualityOrder = QualityFallbackOrder.Asc,
                downloadDirRelative = "Music/MusicFree/",
            ),
        )
        network = MutableStateFlow(NetworkState.Wifi)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = http,
            writer = writer.asWriter(),
            resolver = resolver::resolve,
            converters = converters,
            musicRepository = musicRepository,
            configFlow = configFlow,
            networkFlow = network,
            cacheDir = createTempDirectory("dlcache").toFile(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close(); MfLog.resetForTest() }

    private fun item(id: String, platform: String = "qq") = MusicItem(
        id = id, platform = platform, title = "t-$id", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    private fun pluginItem(id: String, platform: String = "qq") = MusicItem(
        id = id,
        platform = platform,
        title = "plugin-$id",
        artist = "artist-$id",
        album = "album-$id",
        duration = 30000L,
        url = "https://plugin.example/song/$id.mp3",
        artwork = "https://plugin.example/art/$id.png",
        qualities = mapOf(
            PlayQuality.STANDARD to QualityInfo(url = "https://plugin.example/standard/$id", size = 128000L),
        ),
        raw = mapOf(
            "platformId" to "raw-id-$id",
            "downloaded" to false,
        ),
        localPath = "content://local/$id",
    )

    @Test fun singleEnqueueRunsToCompletion() = runTest {
        resolver.bind(
            MediaKey.of("1", "qq"),
            MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null, quality = null),
        )
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
        assertEquals(1, writer.commitCount)
    }

    @Test fun concurrencyCapNeverExceeded() = runTest {
        configFlow.value = configFlow.value.copy(maxDownload = 2)
        repeat(5) { i ->
            resolver.bind(
                MediaKey.of("$i", "qq"),
                MediaSourceResult(url = "https://x/$i.mp3", headers = null, userAgent = null, quality = null),
            )
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
            com.hank.musicfree.data.db.entity.DownloadedTrackEntity(
                id = "1", platform = "qq",
                mediaStoreUri = "content://media/external/audio/media/1",
                relativePath = "Music/MusicFree/",
                mimeType = "audio/mpeg",
                quality = "standard",
                sizeBytes = 1L,
                downloadedAt = 1L,
            ),
        )
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertEquals(0, writer.commitCount)
    }

    @Test fun configuredDownloadQualityOrderControlsFallback() = runTest {
        configFlow.value = configFlow.value.copy(downloadQualityOrder = QualityFallbackOrder.Asc)
        resolver.bind(
            MediaKey.of("1", "qq"),
            "super",
            MediaSourceResult(url = "https://x/1-super.mp3", headers = null, userAgent = null, quality = null),
        )

        engine.enqueue(listOf(item("1")), PlayQuality.HIGH)
        advanceUntilIdle()

        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
        assertEquals(listOf("high", "super"), resolver.callOrder)
    }

    @Test fun enqueuePersistsFullMusicItemSeedWhenSchedulerIsBlocked() = runTest {
        network.value = NetworkState.Offline
        val target = pluginItem("blocked", platform = "wy")
        engine.enqueue(listOf(target), PlayQuality.STANDARD)
        advanceUntilIdle()

        val row = db.downloadTaskDao().observeAll().first().single()
        val restored = converters.jsonToMusicItem(row.musicItemJson)
        assertNotNull(restored)
        assertEquals(target.id, restored!!.id)
        assertEquals(target.platform, restored.platform)
        assertEquals(target.title, restored.title)
        assertEquals(target.artist, restored.artist)
        assertEquals(target.album, restored.album)
        assertEquals(target.duration, restored.duration)
        assertEquals(target.url, restored.url)
        assertEquals(target.artwork, restored.artwork)
        assertEquals(target.localPath, restored.localPath)
        assertEquals(target.qualities, restored.qualities)
        assertEquals(target.raw, restored.raw)
    }

    @Test fun completionWritesFullPluginTrackIntoLocalLibrary() = runTest {
        val target = pluginItem("complete", platform = "wy")
        resolver.bind(
            MediaKey.of(target.id, target.platform),
            MediaSourceResult(
                url = "https://plugin.example/download/${target.id}.mp3",
                headers = null,
                userAgent = null,
                quality = null,
            ),
        )
        engine.enqueue(listOf(target), PlayQuality.STANDARD)
        advanceUntilIdle()

        val localItem = musicRepository.getById(target.id, target.platform)
        assertNotNull(localItem)
        assertEquals(target.id, localItem!!.id)
        assertEquals(target.platform, localItem.platform)
        assertEquals(target.album, localItem.album)
        assertEquals(target.artwork, localItem.artwork)
        assertEquals(target.qualities, localItem.qualities)
        assertEquals(target.raw["platformId"], localItem.raw["platformId"])
        assertEquals(writer.lastCommittedUri!!.toString(), localItem.localPath)
        assertEquals(true, localItem.raw["downloaded"])
        assertEquals(PlayQuality.STANDARD.name.lowercase(), localItem.raw["downloadQuality"])
        assertEquals(writer.lastCommittedUri!!.toString(), localItem.raw["mediaStoreUri"])
        assertNotNull(localItem.raw["downloadedAt"])

        val success = logger.events.single { it.event == "download_local_library_write_success" }
        assertEquals(LogCategory.DOWNLOAD, success.category)
        assertEquals("download_local_library_write", success.fields["operation"])
        assertEquals(target.id, success.fields["itemId"])
        assertEquals(target.platform, success.fields["platform"])
        assertEquals(PlayQuality.STANDARD.name.lowercase(), success.fields["quality"])
        assertEquals("mediastore", success.fields["pathType"])
        assertEquals(LogFields.Result.SUCCESS, success.fields["result"])
    }

    @Test fun failedDownloadDoesNotWriteLocalLibrary() = runTest {
        val target = pluginItem("failed", platform = "wy")
        resolver.bind(
            MediaKey.of(target.id, target.platform),
            MediaSourceResult(
                url = "https://plugin.example/download/${target.id}.mp3",
                headers = null,
                userAgent = null,
                quality = null,
            ),
        )
        http.failNext()
        engine.enqueue(listOf(target), PlayQuality.STANDARD)
        advanceUntilIdle()

        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(DownloadStatus.FAILED.name, rows.single().status)
        assertFalse(db.downloadedTrackDao().exists(target.id, target.platform))
        assertNull(musicRepository.getById(target.id, target.platform))
        assertFalse(logger.events.any { it.event == "download_local_library_write_success" })
    }
}

private data class CapturedLogEvent(
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
)

private class CapturingLogger : MfLogger {
    val events = mutableListOf<CapturedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += CapturedLogEvent(category, event, fields + ("throwable" to throwable))
    }

    override fun flush() = Unit
}
