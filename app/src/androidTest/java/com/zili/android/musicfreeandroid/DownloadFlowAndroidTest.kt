package com.zili.android.musicfreeandroid

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadFlowAndroidTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var downloader: Downloader

    private lateinit var server: MockWebServer

    @Before fun setup() {
        hilt.inject()
        server = MockWebServer().apply { start() }
    }

    @After fun teardown() { server.shutdown() }

    @Test fun enqueueDownloadsAndAppearsInMediaStore() = runTest {
        val payload = "fake-mp3-bytes".repeat(4096).toByteArray()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().apply { write(payload) }),
        )
        val item = MusicItem(
            id = "e2e-${System.currentTimeMillis()}",
            platform = "test",
            title = "TestSong",
            artist = "TestArtist",
            album = null,
            duration = 0L,
            url = server.url("/song.mp3").toString(),
            artwork = null,
            qualities = null,
        )
        downloader.enqueue(listOf(item), PlayQuality.STANDARD)

        // Wait up to 30s for downloadedKeys to include our key.
        downloader.downloadedKeys.test(timeout = 30.seconds) {
            while (true) {
                val keys = awaitItem()
                if (MediaKey.of(item) in keys) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        // Verify MediaStore has an entry under Music/MusicFree/
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val resolver: ContentResolver = ctx.contentResolver
        val cursor = resolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Music/MusicFree/%"),
            null,
        )
        cursor.use {
            assertTrue("expected at least one row", it != null && it.moveToFirst())
        }
    }
}
