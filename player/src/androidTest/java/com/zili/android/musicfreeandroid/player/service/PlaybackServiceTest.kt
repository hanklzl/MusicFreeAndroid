package com.zili.android.musicfreeandroid.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
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
    fun setUp() = runTest {
        controller = connectController()
    }

    @After
    fun tearDown() {
        controller?.release()
    }

    @Test
    fun serviceReturnsValidMediaSession() {
        assertNotNull(controller)
        assertTrue(controller!!.isConnected)
    }

    @Test
    fun playerStartsInIdleState() {
        assertEquals(Player.STATE_IDLE, controller!!.playbackState)
        assertFalse(controller!!.isPlaying)
    }

    @Test
    fun setMediaItemAndPrepareTransitionsToReady() {
        val latch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) latch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()

        assertTrue("等待 READY 状态超时", latch.await(5, TimeUnit.SECONDS))
        assertEquals(Player.STATE_READY, controller!!.playbackState)
    }

    @Test
    fun playAndPauseWork() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()
        controller!!.play()

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(controller!!.playWhenReady)

        controller!!.pause()
        assertFalse(controller!!.playWhenReady)
    }

    @Test
    fun mediaSessionMetadataReflectsCurrentTrack() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

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

        controller!!.setMediaItem(mediaItem)
        controller!!.prepare()

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))

        val metadata = controller!!.mediaMetadata
        assertEquals("Test Song", metadata.title?.toString())
        assertEquals("Test Artist", metadata.artist?.toString())
    }

    @Test
    fun audioFocusLossPausesPlayback() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()
        controller!!.play()
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(controller!!.playWhenReady)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val focusResult = audioManager.requestAudioFocus(
            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .build()
        )
        Thread.sleep(500)
        assertFalse(
            "音频焦点丢失后 playWhenReady 应为 false",
            controller!!.playWhenReady
        )
    }

    private suspend fun connectController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val token = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
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
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
}
