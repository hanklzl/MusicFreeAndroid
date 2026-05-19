package com.hank.musicfree.player.controller

import android.content.Context
import androidx.media3.common.PlaybackException
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import com.hank.musicfree.player.source.TrackHeaderRegistry
import org.mockito.kotlin.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerPlaybackFailurePolicyTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `play failure changes source when setting is enabled`() {
        val resolver = RecordingResolver("https://cdn.example.test/fresh.mp3")
        val controller = controller(
            resolver = resolver,
            settings = FakeRuntimeSettings(tryChangeSourceWhenPlayFail = true),
        )
        try {
            controller.playQueue.setQueue(
                listOf(item("1", "https://cdn.example.test/stale.mp3")),
                startIndex = 0,
            )

            controller.handlePlaybackErrorForTest(decoderError())

            waitUntil("current source changed") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }
            assertEquals(listOf("1"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `play failure skips next when auto stop is disabled`() {
        val controller = controller(
            resolver = RecordingResolver(null),
            settings = FakeRuntimeSettings(autoStopWhenError = false),
        )
        try {
            controller.playQueue.setQueue(
                listOf(
                    item("1", "https://cdn.example.test/1.mp3"),
                    item("2", "https://cdn.example.test/2.mp3"),
                ),
                startIndex = 0,
            )

            controller.handlePlaybackErrorForTest(decoderError())

            waitUntil("queue advances to next item") {
                controller.playQueue.currentItem?.id == "2"
            }
        } finally {
            controller.release()
        }
    }

    @Test
    fun `play failure keeps current item when auto stop is enabled`() {
        val controller = controller(
            resolver = RecordingResolver(null),
            settings = FakeRuntimeSettings(autoStopWhenError = true),
        )
        try {
            controller.playQueue.setQueue(
                listOf(
                    item("1", "https://cdn.example.test/1.mp3"),
                    item("2", "https://cdn.example.test/2.mp3"),
                ),
                startIndex = 0,
            )

            controller.handlePlaybackErrorForTest(decoderError())
            Thread.sleep(120)

            assertEquals("1", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `registry entry after source-change-on-failure carries quality`() {
        val registry = TrackHeaderRegistry()
        val resolver = ResolverWithHeaders("https://cdn.example.test/changed.mp3")
        val controller = controller(
            resolver = resolver,
            settings = FakeRuntimeSettings(tryChangeSourceWhenPlayFail = true),
            registry = registry,
        )
        try {
            controller.playQueue.setQueue(
                listOf(item("1", "https://cdn.example.test/original.mp3")),
                startIndex = 0,
            )

            controller.handlePlaybackErrorForTest(decoderError())

            waitUntil("source changed") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/changed.mp3"
            }
            val entry = registry.get("https://cdn.example.test/changed.mp3")
            assertEquals(
                "registry entry after source-change-on-failure must carry quality (not null/unknown)",
                PlayQuality.STANDARD,
                entry?.quality,
            )
        } finally {
            controller.release()
        }
    }

    private fun controller(
        resolver: MediaSourceResolver,
        settings: PlaybackRuntimeSettings,
        registry: TrackHeaderRegistry = TrackHeaderRegistry(),
    ) = PlayerController(
        context = context,
        mediaSourceResolver = resolver,
        playbackRuntimeSettings = settings,
        trackHeaderRegistry = registry,
        listenTracker = mock<ListenTracker>(),
        currentSidProvider = com.hank.musicfree.core.telemetry.CurrentSidProvider(),
        playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
    )

    private fun decoderError(): PlaybackException = PlaybackException(
        "decoder",
        null,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
    )

    private fun item(id: String, url: String?) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = url,
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
            Thread.sleep(20)
        }
        org.junit.Assert.fail("Timed out waiting for $description")
    }

    private class RecordingResolver(
        private val resolvedUrl: String?,
    ) : MediaSourceResolver {
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution? {
            requestedIds += item.id
            val url = resolvedUrl ?: return null
            return MediaSourceResolution(
                item = item.copy(url = url),
                source = MediaSourceResult(
                    url = url,
                    headers = null,
                    userAgent = null,
                    quality = null,
                ),
                requestedPlatform = item.platform,
                resolverPlatform = item.platform,
                redirected = false,
            )
        }
    }

    /** Resolver that returns headers so trackHeaderRegistry.put is triggered. */
    private class ResolverWithHeaders(
        private val resolvedUrl: String,
    ) : MediaSourceResolver {
        override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution? {
            return MediaSourceResolution(
                item = item.copy(url = resolvedUrl),
                source = MediaSourceResult(
                    url = resolvedUrl,
                    headers = mapOf("Referer" to "https://music.example.test"),
                    userAgent = "MusicFreeAndroidTest/1.0",
                    quality = null,
                ),
                requestedPlatform = item.platform,
                resolverPlatform = item.platform,
                redirected = false,
            )
        }
    }

    private class FakeRuntimeSettings(
        private val tryChangeSourceWhenPlayFail: Boolean = false,
        private val autoStopWhenError: Boolean = false,
    ) : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.STANDARD

        override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc

        override suspend fun useCellularPlay(): Boolean = true

        override suspend fun allowConcurrentPlayback(): Boolean = false

        override suspend fun autoPlayWhenAppStart(): Boolean = false

        override suspend fun tryChangeSourceWhenPlayFail(): Boolean = tryChangeSourceWhenPlayFail

        override suspend fun autoStopWhenError(): Boolean = autoStopWhenError

        override suspend fun audioInterruptionAction(): AudioInterruptionAction =
            AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume(): Float = 0.5f

        override suspend fun showExitOnNotification(): Boolean = false
    }
}
