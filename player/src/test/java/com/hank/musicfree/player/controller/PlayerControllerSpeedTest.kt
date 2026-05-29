package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import com.hank.musicfree.player.source.TrackHeaderRegistry
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.mockito.kotlin.mock
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
        try {
            assertEquals(PlaybackSpeeds.DEFAULT, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed updates state without connected controller`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
        try {
            controller.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed accepts edge values`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
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
            override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution? = null
        }
        val controller = PlayerController(context, nullResolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
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
            override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution? = null
        }
        val controller = PlayerController(context, nullResolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
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

    @Test
    fun `changeQuality registers cache key for resolved quality url`() {
        val newUrl = "https://cdn.example.test/track/high.mp3"
        val changedHeaders = mapOf("Referer" to "https://music.example.test")
        val userAgent = "MusicFreeAndroidTest/1.0"
        val cachePolicy = MediaSourceCachePolicy.NoStore
        val qualityResolver = object : MediaSourceResolver {
            override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution? {
                return if (quality == PlayQuality.HIGH.name.lowercase()) {
                    MediaSourceResolution(
                        item = item.copy(url = newUrl),
                        source = MediaSourceResult(
                            url = newUrl,
                            headers = changedHeaders,
                            userAgent = userAgent,
                            quality = PlayQuality.HIGH,
                        ),
                        requestedPlatform = item.platform,
                        resolverPlatform = item.platform,
                        redirected = false,
                        cachePolicy = cachePolicy,
                    )
                } else {
                    null
                }
            }
        }
        val registry = TrackHeaderRegistry()
        val controller = PlayerController(
            context,
            qualityResolver,
            trackHeaderRegistry = registry,
            playbackCacheKeyRegistrar = PlaybackCacheKeyRegistrar(registry),
            listenTracker = mock(),
            currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
            playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
        )
        val mockMediaController = mock<androidx.media3.session.MediaController> {}

        try {
            controller.setMediaControllerForTest(mockMediaController)
            controller.playQueue.setQueue(
                listOf(
                    MusicItem(
                        id = "song-1",
                        platform = "platform",
                        title = "Song 1",
                        artist = "Artist",
                        album = null,
                        duration = 1L,
                        url = "https://cdn.example.test/track/original.mp3",
                        artwork = null,
                        qualities = null,
                    ),
                ),
                startIndex = 0,
            )

            controller.changeQuality(PlayQuality.HIGH)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val entry = registry.get(newUrl)
            assertNotNull(entry)
            assertEquals("platform:song-1", entry!!.cacheKey)
            assertEquals(PlayQuality.HIGH, entry.quality)
            assertEquals(cachePolicy, entry.cachePolicy)
            assertEquals(false, entry.byteCacheAllowed)
            assertEquals(changedHeaders, entry.headers)
            assertEquals(userAgent, entry.userAgent)
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
