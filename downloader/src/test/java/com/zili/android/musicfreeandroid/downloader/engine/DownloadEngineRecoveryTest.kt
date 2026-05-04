package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
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
class DownloadEngineRecoveryTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        val syncExec = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().setQueryExecutor(syncExec).setTransactionExecutor(syncExec).build()
    }

    @After fun teardown() { db.close() }

    @Test fun startResetsInflightToPendingAndClearsResolved() = runTest {
        val now = System.currentTimeMillis()
        db.downloadTaskDao().upsert(DownloadTaskEntity(
            id = "p", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
            durationMs = 0L, targetQuality = "standard", status = "PREPARING",
            errorReason = null, resolvedUrl = "https://x/p.mp3", resolvedHeadersJson = null,
            fileSize = null, downloadedSize = null, createdAt = now, updatedAt = now,
        ))
        db.downloadTaskDao().upsert(DownloadTaskEntity(
            id = "d", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
            durationMs = 0L, targetQuality = "standard", status = "DOWNLOADING",
            errorReason = null, resolvedUrl = "https://x/d.mp3", resolvedHeadersJson = null,
            fileSize = 100, downloadedSize = 50, createdAt = now, updatedAt = now,
        ))
        val engine = DownloadEngine(
            taskDao = db.downloadTaskDao(), downloadedDao = db.downloadedTrackDao(),
            http = FakeHttpDownloader(), writer = FakeMediaStoreWriter().asWriter(),
            resolver = FakeQualityResolver()::resolve,
            configFlow = MutableStateFlow(DownloadConfig(2, false, PlayQuality.STANDARD, "Music/MusicFree/")),
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
}
