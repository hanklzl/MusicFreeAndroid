package com.hank.musicfree.player.controller

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.source.TrackHeaderRegistry
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import kotlinx.coroutines.CompletableDeferred
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerNotificationControlsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `notification controls reattach when controller is reused after detach`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            PlaybackNotificationCommandHandler.detach(controller)
            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)
            assertEquals("1", controller.playQueue.currentItem?.id)

            PlaybackNotificationCommandHandler.skipToNext()

            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `notification next resolves item without url before playback`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/2.mp3",
        )
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            controller.playQueue.setQueue(
                listOf(
                    testItem("1"),
                    testItem("2").copy(url = null),
                ),
                startIndex = 0,
            )

            PlaybackNotificationCommandHandler.skipToNext()

            waitUntil("next item is resolved") {
                controller.playQueue.currentItem?.id == "2" &&
                    controller.playQueue.currentItem?.url == "https://cdn.example.test/2.mp3"
            }
            assertEquals(listOf("2"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `notification next keeps committed current item while target source is resolving`() {
        val resolver = BlockingResolver()
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            controller.restoreQueue(
                items = listOf(
                    testItem("1"),
                    testItem("2").copy(url = null),
                ),
                startIndex = 0,
                playWhenRestored = false,
            )

            PlaybackNotificationCommandHandler.skipToNext()

            waitUntil("next item resolution starts") {
                resolver.requestedIds == listOf("2")
            }
            assertEquals("1", controller.playQueue.currentItem?.id)
            assertEquals(0, controller.queueState.value.currentIndex)
            assertEquals("1", controller.playerState.value.currentItem?.id)
        } finally {
            resolver.complete(null)
            controller.release()
        }
    }

    @Test
    fun `notification next rolls queue back when media source cannot resolve`() {
        val resolver = UnresolvedResolver()
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            controller.playQueue.setQueue(
                listOf(
                    testItem("1"),
                    testItem("2").copy(url = null),
                ),
                startIndex = 0,
            )

            PlaybackNotificationCommandHandler.skipToNext()

            waitUntil("queue rolls back to previous item") {
                controller.playQueue.currentItem?.id == "1"
            }
            assertEquals(listOf("2"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `notification play reloads queue when controller cache is stale and session is empty`() {
        // Regression for v1.2.3 StackOverflowError: when MediaController.currentMediaItem
        // was non-null (stale cache) but PlaybackService.session.player was empty,
        // playFromNotification used to take the play() shortcut, which called
        // MediaController.play() and re-entered onPlayerCommandRequest → infinite
        // recursion. Verify we now always go through activateCurrentQueueItem.
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/1.mp3",
        )
        val controller = PlayerController(
            context,
            resolver,
            listenTracker = mock<ListenTracker>(),
            currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
            playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
        )

        val staleMediaItem = MediaItem.Builder().setMediaId("stale-cached").build()
        val mockMediaController = mock<MediaController> {
            on { currentMediaItem } doReturn staleMediaItem
        }
        controller.setMediaControllerForTest(mockMediaController)

        try {
            controller.playQueue.setQueue(
                listOf(testItem("1").copy(url = null)),
                startIndex = 0,
            )

            PlaybackNotificationCommandHandler.play()

            waitUntil("notification play resolves queue item instead of recursing") {
                resolver.requestedIds == listOf("1")
            }
            // Old buggy path would call controller.play() directly without invoking
            // the resolver. Asserting resolver activity proves we went through
            // activateCurrentQueueItem.
            assertEquals(listOf("1"), resolver.requestedIds)
            // The recursion came from calling controller.play() BEFORE any setMediaItem,
            // so when the session-side onPlayerCommandRequest re-fired it still saw an
            // empty player. Asserting that setMediaItem fires before play proves the
            // fix: session.player will have the item by the time play arrives.
            waitUntil("controller is driven via setMediaItem before play") {
                runCatching {
                    val ordered = inOrder(mockMediaController)
                    ordered.verify(mockMediaController).setMediaItem(any())
                    ordered.verify(mockMediaController).play()
                }.isSuccess
            }
        } finally {
            controller.setMediaControllerForTest(null)
            controller.release()
        }
    }

    @Test
    fun `notification play resolves restored current item when no media item is prepared`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/1.mp3",
        )
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            controller.restoreQueue(
                items = listOf(testItem("1").copy(url = null)),
                startIndex = 0,
                playWhenRestored = false,
            )

            PlaybackNotificationCommandHandler.play()

            waitUntil("restored item is resolved from notification play") {
                resolver.requestedIds == listOf("1") &&
                    controller.playQueue.currentItem?.url == "https://cdn.example.test/1.mp3"
            }
        } finally {
            controller.release()
        }
    }

    @Test
    fun `app play reloads restored queue when prepared media item is stale`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/1.mp3",
        )
        val controller = PlayerController(
            context,
            resolver,
            listenTracker = mock<ListenTracker>(),
            currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
            playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
        )
        val staleMediaItem = MediaItem.Builder().setMediaId("test:stale").build()
        val mockMediaController = mock<MediaController> {
            on { currentMediaItem } doReturn staleMediaItem
            on { playbackState } doReturn Player.STATE_READY
        }
        controller.setMediaControllerForTest(mockMediaController)

        try {
            controller.restoreQueue(
                items = listOf(testItem("1").copy(url = null)),
                startIndex = 0,
                playWhenRestored = false,
            )

            controller.play()

            waitUntil("app play resolves stale restored queue item") {
                resolver.requestedIds == listOf("1")
            }
            waitUntil("app play sets media item before play") {
                runCatching {
                    val ordered = inOrder(mockMediaController)
                    ordered.verify(mockMediaController).setMediaItem(any())
                    ordered.verify(mockMediaController).play()
                }.isSuccess
            }
        } finally {
            controller.setMediaControllerForTest(null)
            controller.release()
        }
    }

    @Test
    fun `app play reloads restored queue when controller is idle`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/1.mp3",
        )
        val controller = PlayerController(
            context,
            resolver,
            listenTracker = mock<ListenTracker>(),
            currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
            playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
        )
        val matchingMediaItem = MediaItem.Builder().setMediaId("test:1").build()
        val mockMediaController = mock<MediaController> {
            on { currentMediaItem } doReturn matchingMediaItem
            on { playbackState } doReturn Player.STATE_IDLE
        }
        controller.setMediaControllerForTest(mockMediaController)

        try {
            controller.restoreQueue(
                items = listOf(testItem("1").copy(url = null)),
                startIndex = 0,
                playWhenRestored = false,
            )

            controller.play()

            waitUntil("app play resolves idle restored queue item") {
                resolver.requestedIds == listOf("1")
            }
            waitUntil("idle app play sets media item before play") {
                runCatching {
                    val ordered = inOrder(mockMediaController)
                    ordered.verify(mockMediaController).setMediaItem(any())
                    ordered.verify(mockMediaController).play()
                }.isSuccess
            }
        } finally {
            controller.setMediaControllerForTest(null)
            controller.release()
        }
    }

    @Test
    fun `playItem refreshes remote item that already has stale url before playback`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))

        try {
            controller.playItem(testItem("1").copy(url = "https://cdn.example.test/stale.mp3"))

            waitUntil("current item is refreshed") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }
            assertEquals(listOf("1"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem refreshes queued item when matching item already has url`() {
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = PlayerController(context, resolver, listenTracker = mock<ListenTracker>(), currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(), playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog))
        val queued = testItem("1").copy(url = "https://queue.example.test/1.mp3")

        try {
            controller.playQueue.setQueue(listOf(queued), startIndex = 0)

            controller.playItem(testItem("1").copy(url = null))

            waitUntil("queued item is refreshed") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }
            assertEquals(listOf("1"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem registers resolver headers for refreshed source`() {
        val registry = TrackHeaderRegistry()
        val resolver = RecordingResolver(
            resolvedUrl = "https://cdn.example.test/fresh.mp3",
            headers = mapOf("Referer" to "https://music.example.test"),
            userAgent = "MusicFreeAndroidTest/1.0",
        )
        val controller = PlayerController(
            context = context,
            mediaSourceResolver = resolver,
            trackHeaderRegistry = registry,
            listenTracker = mock<ListenTracker>(),
            currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
            playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
        )

        try {
            controller.playItem(testItem("1").copy(url = "https://cdn.example.test/stale.mp3"))

            waitUntil("headers are registered") {
                registry.get("https://cdn.example.test/fresh.mp3") != null
            }
            val entry = registry.get("https://cdn.example.test/fresh.mp3")!!
            assertEquals("https://music.example.test", entry.headers["Referer"])
            assertEquals("MusicFreeAndroidTest/1.0", entry.userAgent)
        } finally {
            controller.release()
        }
    }

    private fun testItem(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "https://example.test/$id.mp3",
        artwork = null,
        qualities = null,
    )

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for $description")
    }

    private class RecordingResolver(
        private val resolvedUrl: String,
        private val headers: Map<String, String>? = null,
        private val userAgent: String? = null,
    ) : MediaSourceResolver {
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? {
            requestedIds += item.id
            val source = MediaSourceResult(
                url = resolvedUrl,
                headers = headers,
                userAgent = userAgent,
                quality = null,
            )
            return MediaSourceResolution(
                item = item.copy(url = resolvedUrl),
                source = source,
                requestedPlatform = item.platform,
                resolverPlatform = item.platform,
                redirected = false,
            )
        }
    }

    private class UnresolvedResolver : MediaSourceResolver {
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? {
            requestedIds += item.id
            return null
        }
    }

    private class BlockingResolver : MediaSourceResolver {
        private val result = CompletableDeferred<MediaSourceResolution?>()
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? {
            requestedIds += item.id
            return result.await()
        }

        fun complete(resolution: MediaSourceResolution?) {
            result.complete(resolution)
        }
    }
}
