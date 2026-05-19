package com.hank.musicfree

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.media.MediaSourceResolver
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Instrumented test verifying the local short-circuit path in [MediaSourceResolver]:
 * when a [MusicItem] has a readable `localPath`, the resolver must return that path
 * directly without consulting any plugin.
 *
 * Known limitation: this test requires READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE permission
 * on API 29+, but uses a file inside the app's private files directory (no permission needed)
 * to avoid external-storage setup. CI without a running emulator will need to skip this.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalShortCircuitInstrumentedTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var resolver: MediaSourceResolver

    private lateinit var ctx: Context

    @Before fun setup() {
        hilt.inject()
        ctx = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun localPathShortCircuitsPlugin() = runTest {
        // Write a tiny fake mp3 to app-private storage (no permission required).
        // 0xFF 0xFB = MPEG sync word for MP3 frame header.
        val file = File(ctx.filesDir, "short_circuit_test_${System.currentTimeMillis()}.mp3")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte()))

        val item = MusicItem(
            id = "sc-test-1",
            platform = "test_nonexistent_platform",
            title = "Test Short Circuit",
            artist = "Test",
            album = null,
            duration = 0L,
            url = "https://example.invalid/a.mp3",
            artwork = null,
            qualities = null,
            localPath = file.absolutePath,
        )

        val resolution = resolver.resolve(item, null, null)

        // The resolver must short-circuit to the local path without calling any plugin.
        // (No plugin for "test_nonexistent_platform" exists so any non-short-circuit
        // path would return null.)
        assertEquals(
            "Expected local path short-circuit, got: $resolution",
            file.absolutePath,
            resolution?.source?.url,
        )

        file.delete()
    }

    @Test
    fun missingLocalPathFallsThroughToPlugin() = runTest {
        // When localPath points to a non-existent file, resolver falls back to plugin lookup.
        // Since "test_nonexistent_platform" has no plugin, result should be null.
        val item = MusicItem(
            id = "sc-test-2",
            platform = "test_nonexistent_platform",
            title = "Test Missing Local",
            artist = "Test",
            album = null,
            duration = 0L,
            url = "https://example.invalid/b.mp3",
            artwork = null,
            qualities = null,
            localPath = "/nonexistent/path/missing.mp3",
        )

        val resolution = resolver.resolve(item, null, null)

        // Plugin doesn't exist → null after local probe fails
        assertEquals(
            "Expected null when local file is missing and no plugin exists",
            null,
            resolution,
        )
    }
}
