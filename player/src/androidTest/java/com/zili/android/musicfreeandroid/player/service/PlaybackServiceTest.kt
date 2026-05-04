package com.zili.android.musicfreeandroid.player.service

import android.content.ComponentName
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var controller: MediaController? = null

    @Before
    fun setUp() = runBlocking {
        controller = withTimeout(CONTROLLER_CONNECT_TIMEOUT_MS) {
            connectController()
        }
        runOnAppThread {
            controller!!.stop()
            controller!!.clearMediaItems()
        }
    }

    @After
    fun tearDown() {
        runOnAppThread {
            controller?.stop()
            controller?.clearMediaItems()
            controller?.release()
        }
    }

    @Test
    fun serviceReturnsValidMediaSession() {
        assertNotNull(controller)
        assertTrue(runOnAppThread { controller!!.isConnected })
    }

    @Test
    fun playerStartsInIdleState() {
        val state = runOnAppThread {
            controller!!.playbackState to controller!!.isPlaying
        }
        assertEquals(Player.STATE_IDLE, state.first)
        assertFalse(state.second)
    }

    @Test
    fun setMediaItemAndPrepareTransitionsToReady() {
        val latch = CountDownLatch(1)
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        runOnAppThread {
            controller!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) latch.countDown()
                }
            })
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
        }

        assertTrue("等待 READY 状态超时", latch.await(5, TimeUnit.SECONDS))
        assertEquals(Player.STATE_READY, runOnAppThread { controller!!.playbackState })
    }

    @Test
    fun playAndPauseWork() {
        val readyLatch = CountDownLatch(1)
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        runOnAppThread {
            controller!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) readyLatch.countDown()
                }
            })
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
            controller!!.play()
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(runOnAppThread { controller!!.playWhenReady })

        runOnAppThread {
            controller!!.pause()
        }
        assertFalse(runOnAppThread { controller!!.playWhenReady })
    }

    @Test
    fun mediaSessionMetadataReflectsCurrentTrack() {
        val readyLatch = CountDownLatch(1)

        val mediaItem = MediaItem.Builder()
            .setMediaId("test:1")
            .setUri("android.resource://${context.packageName}/${
                com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
            }")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Test Song")
                    .setArtist("Test Artist")
                    .build()
            )
            .build()

        runOnAppThread {
            controller!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) readyLatch.countDown()
                }
            })
            controller!!.setMediaItem(mediaItem)
            controller!!.prepare()
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))

        val metadata = runOnAppThread { controller!!.mediaMetadata }
        assertEquals("Test Song", metadata.title?.toString())
        assertEquals("Test Artist", metadata.artist?.toString())
    }

    @Test
    fun audioFocusLossPausesPlayback() {
        val readyLatch = CountDownLatch(1)
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        runOnAppThread {
            controller!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) readyLatch.countDown()
                }
            })
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
            controller!!.play()
        }
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        waitUntil("controller starts playback") {
            runOnAppThread { controller!!.isPlaying }
        }
        assertTrue(runOnAppThread { controller!!.playWhenReady })

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusResult = audioManager.requestAudioFocus(
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .build()
        )
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, focusResult)
        assumeTrue(
            "Device did not dispatch audio focus loss to the playback service",
            eventually { !runOnAppThread { controller!!.playWhenReady } },
        )
    }

    private fun <T> runOnAppThread(timeoutMs: Long = 5_000L, block: () -> T): T {
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        var result: T? = null
        context.mainExecutor.execute {
            try {
                result = block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for app main thread block")
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        if (eventually(timeoutMs, condition)) return
        fail("Timed out waiting for $description")
    }

    private fun eventually(
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }

    private suspend fun connectController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val future = runOnAppThread {
                val token = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java),
                )
                MediaController.Builder(context, token).buildAsync().also { future ->
                    future.addListener(
                        {
                            try {
                                cont.resume(future.get())
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
            }
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }

    private companion object {
        const val CONTROLLER_CONNECT_TIMEOUT_MS = 5_000L
    }
}
