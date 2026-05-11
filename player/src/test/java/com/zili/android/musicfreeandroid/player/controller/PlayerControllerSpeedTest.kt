package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerSpeedTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        MfLog.resetForTest()
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `default playbackSpeed is 1_0`() {
        val controller = PlayerController(context)
        try {
            assertEquals(PlaybackSpeeds.DEFAULT, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed updates state without connected controller`() {
        val controller = PlayerController(context)
        try {
            controller.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed accepts edge values`() {
        val controller = PlayerController(context)
        try {
            controller.setPlaybackSpeed(0.5f)
            assertEquals(0.5f, controller.playerState.value.playbackSpeed)
            controller.setPlaybackSpeed(2.0f)
            assertEquals(2.0f, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `changeQuality is no-op when no current item`() {
        val controller = PlayerController(context)
        try {
            // No queue items — currentItem is null
            controller.changeQuality(PlayQuality.HIGH)
            // No crash, state unchanged
            assertEquals(PlaybackSpeeds.DEFAULT, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `changeQuality emits error event when resolver returns null`() {
        val nullResolver = object : MediaSourceResolver {
            override suspend fun resolve(item: MusicItem, quality: String?): MediaSourceResolution? = null
        }
        val controller = PlayerController(context, nullResolver)
        try {
            val item = MusicItem(
                id = "x",
                platform = "p",
                title = "T",
                artist = "A",
                album = null,
                duration = 1L,
                url = null,
                artwork = null,
                qualities = null,
            )
            controller.playQueue.setQueue(listOf(item), startIndex = 0)

            val subscribed = CountDownLatch(1)
            val received = CountDownLatch(1)
            val errorRef = AtomicReference<String?>(null)
            val collectJob = CoroutineScope(Dispatchers.Default).launch {
                // onSubscription fires immediately after the upstream subscription is registered,
                // before any events are delivered — this is the correct synchronization point.
                controller.errorEvents
                    .onSubscription { subscribed.countDown() }
                    .collect {
                        errorRef.set(it)
                        received.countDown()
                    }
            }
            // Wait until the collector is registered before calling changeQuality,
            // so the SharedFlow does not emit to zero subscribers.
            subscribed.await(2, TimeUnit.SECONDS)
            try {
                controller.changeQuality(PlayQuality.HIGH)
                // Drive the Main looper so the coroutine launched by changeQuality can run.
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                received.await(2, TimeUnit.SECONDS)
                val errorMessage = errorRef.get()
                assertNotNull("Expected error event for unresolvable quality", errorMessage)
                assertTrue(errorMessage!!.contains("不支持") || errorMessage!!.contains("音质"))
            } finally {
                collectJob.cancel()
            }
        } finally {
            controller.release()
        }
    }

    @Test
    fun `changeQuality logs failure with quality and item fields when resolver returns null`() {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val nullResolver = object : MediaSourceResolver {
            override suspend fun resolve(item: MusicItem, quality: String?): MediaSourceResolution? = null
        }
        val controller = PlayerController(context, nullResolver)
        try {
            val item = MusicItem(
                id = "quality-log",
                platform = "demo",
                title = "Quality Track",
                artist = "A",
                album = null,
                duration = 1L,
                url = null,
                artwork = null,
                qualities = null,
            )
            controller.playQueue.setQueue(listOf(item), startIndex = 0)

            controller.changeQuality(PlayQuality.HIGH)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val event = logger.events.single { it.event == "player_quality_change_failed" }
            assertEquals(LogCategory.PLAYER, event.category)
            assertEquals("change_quality", event.fields["operation"])
            assertEquals(LogFields.Result.FAILURE, event.fields["result"])
            assertEquals("high", event.fields["quality"])
            assertEquals("quality-log", event.fields["itemId"])
            assertEquals("Quality Track", event.fields["itemName"])
            assertEquals("demo", event.fields["platform"])
            assertEquals("no_source", event.fields["reason"])
        } finally {
            controller.release()
        }
    }
}

private data class RecordedLogEvent(
    val level: String,
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
    val throwable: Throwable? = null,
)

private class RecordingLogger : MfLogger {
    val events = mutableListOf<RecordedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent("trace", category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent("detail", category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += RecordedLogEvent("error", category, event, fields, throwable)
    }

    override fun flush() = Unit
}
