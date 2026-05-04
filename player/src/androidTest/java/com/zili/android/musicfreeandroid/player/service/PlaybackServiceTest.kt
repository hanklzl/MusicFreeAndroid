package com.zili.android.musicfreeandroid.player.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask
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
        controller?.let { mediaController ->
            runOnAppThread {
                mediaController.stop()
                mediaController.clearMediaItems()
                mediaController.release()
            }
        }
        controller = null
    }

    @Test
    fun serviceReturnsValidMediaSession() {
        assertNotNull(controller)
        assertTrue(runOnAppThread { controller!!.isConnected })
    }

    @Test
    fun playerStartsInIdleState() {
        val playbackState = runOnAppThread { controller!!.playbackState }
        val isPlaying = runOnAppThread { controller!!.isPlaying }

        assertEquals(Player.STATE_IDLE, playbackState)
        assertFalse(isPlaying)
    }

    @Test
    fun mediaSessionExposesSessionActivity() {
        val sessionActivity = runOnAppThread {
            controller!!.sessionActivity
        }

        assertNotNull(sessionActivity)
    }

    @Test
    fun mediaButtonPreferencesExposePreviousAndNextCommands() {
        val customActions = runOnAppThread {
            controller!!.mediaButtonPreferences
                .mapNotNull { it.sessionCommand?.customAction }
                .toSet()
        }

        assertTrue(customActions.contains(PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS))
        assertTrue(customActions.contains(PlaybackNotificationActions.ACTION_SKIP_TO_NEXT))
    }

    @Test
    fun notificationSkipNextCommandAdvancesPlayerControllerQueue() {
        val playerController = PlayerController(context)

        try {
            connectPlayerController(playerController)
            runOnAppThread {
                playerController.playQueue(
                    listOf(testItem("1"), testItem("2")),
                    startIndex = 0,
                )
            }
            waitUntil("player controller starts first queued item") {
                playerController.playerState.value.currentItem?.id == "1"
            }

            val result = runOnAppThread {
                controller!!.sendCustomCommand(
                    PlaybackNotificationActions.SkipToNextCommand,
                    Bundle.EMPTY,
                )
            }.get(5, TimeUnit.SECONDS)

            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            waitUntil("notification next command advances queue") {
                playerController.playerState.value.currentItem?.id == "2"
            }
        } finally {
            runOnAppThread {
                playerController.reset()
                playerController.release()
            }
        }
    }

    @Test
    fun notificationSkipPreviousCommandMovesPlayerControllerQueueBack() {
        val playerController = PlayerController(context)

        try {
            connectPlayerController(playerController)
            runOnAppThread {
                playerController.playQueue(
                    listOf(testItem("1"), testItem("2")),
                    startIndex = 1,
                )
            }
            waitUntil("player controller starts second queued item") {
                playerController.playerState.value.currentItem?.id == "2"
            }

            val result = runOnAppThread {
                controller!!.sendCustomCommand(
                    PlaybackNotificationActions.SkipToPreviousCommand,
                    Bundle.EMPTY,
                )
            }.get(5, TimeUnit.SECONDS)

            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            waitUntil("notification previous command moves queue back") {
                playerController.playerState.value.currentItem?.id == "1"
            }
        } finally {
            runOnAppThread {
                playerController.reset()
                playerController.release()
            }
        }
    }

    @Test
    fun setMediaItemAndPrepareTransitionsToReady() {
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"

        runOnAppThread {
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
        }

        waitUntil("controller reaches READY state") {
            runOnAppThread { controller!!.playbackState } == Player.STATE_READY
        }
        assertEquals(Player.STATE_READY, runOnAppThread { controller!!.playbackState })
    }

    @Test
    fun playAndPauseWork() {
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"

        runOnAppThread {
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
            controller!!.play()
        }

        waitUntil("controller reaches READY state") {
            runOnAppThread { controller!!.playbackState } == Player.STATE_READY
        }
        assertTrue(runOnAppThread { controller!!.playWhenReady })

        runOnAppThread {
            controller!!.pause()
        }
        assertFalse(runOnAppThread { controller!!.playWhenReady })
    }

    @Test
    fun mediaSessionMetadataReflectsCurrentTrack() {
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
            controller!!.setMediaItem(mediaItem)
            controller!!.prepare()
        }

        waitUntil("controller reaches READY state") {
            runOnAppThread { controller!!.playbackState } == Player.STATE_READY
        }

        val metadata = runOnAppThread { controller!!.mediaMetadata }
        assertEquals("Test Song", metadata.title?.toString())
        assertEquals("Test Artist", metadata.artist?.toString())
    }

    @Test
    fun audioFocusLossPausesPlayback() {
        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"

        runOnAppThread {
            controller!!.setMediaItem(MediaItem.fromUri(uri))
            controller!!.prepare()
            controller!!.play()
        }
        waitUntil("controller reaches READY state") {
            runOnAppThread { controller!!.playbackState } == Player.STATE_READY
        }
        assertTrue(runOnAppThread { controller!!.playWhenReady })

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val focusRequest = android.media.AudioFocusRequest.Builder(
            android.media.AudioManager.AUDIOFOCUS_GAIN,
        )
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()

        val focusResult = audioManager.requestAudioFocus(focusRequest)
        try {
            assumeTrue(
                "测试设备未授予竞争音频焦点",
                focusResult == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            )
            val pausedForFocusLoss = waitUntilOrFalse {
                !runOnAppThread { controller!!.playWhenReady }
            }
            assertTrue("音频焦点丢失后 playWhenReady 应为 false", pausedForFocusLoss)
            assertFalse(runOnAppThread { controller!!.playWhenReady })
        } finally {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private suspend fun connectController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val token = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java),
            )
            val future = runOnAppThread {
                MediaController.Builder(context, token).buildAsync()
            }
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

    private fun testItem(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }",
        artwork = null,
        qualities = null,
    )

    private fun connectPlayerController(playerController: PlayerController) {
        runBlocking {
            withContext(Dispatchers.Main) {
                playerController.connect()
            }
        }
    }

    private fun <T> runOnAppThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val task = FutureTask<T> { block() }
        context.mainExecutor.execute(task)
        return task.get(5, TimeUnit.SECONDS)
    }

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        if (waitUntilOrFalse(timeoutMs, condition)) return
        fail("Timed out waiting for $description")
    }

    private fun waitUntilOrFalse(
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
}
