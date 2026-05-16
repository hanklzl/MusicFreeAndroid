package com.hank.musicfree.player.controller

import android.content.Context
import androidx.media3.common.PlaybackException
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.media.StaleUrlRefresher
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import com.hank.musicfree.player.source.TrackHeaderRegistry
import org.mockito.kotlin.mock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerStaleUrlRefreshTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `HTTP 4xx triggers evict and resolveFresh and updates current item`() {
        val refresher = RecordingStaleUrlRefresher(
            freshUrl = "https://cdn.example.test/fresh.mp3",
        )
        val registry = TrackHeaderRegistry()
        val controller = controller(refresher = refresher, registry = registry)
        try {
            val stale = remoteItem("1", url = "https://cdn.example.test/stale.mp3")
            controller.playQueue.setQueue(listOf(stale), startIndex = 0)

            controller.handleStaleUrlErrorForTest(httpError())

            waitUntil("current item is refreshed") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }
            assertEquals(
                listOf("test" to "1"),
                refresher.evictCalls.map { it.first to it.second },
            )
            assertEquals(PlayQuality.STANDARD, refresher.evictCalls.single().third)
            assertEquals(listOf("1"), refresher.resolveFreshIds)
            assertEquals(
                "MusicFreeAndroidTest/1.0",
                registry.get("https://cdn.example.test/fresh.mp3")?.userAgent,
            )
        } finally {
            controller.release()
        }
    }

    @Test
    fun `same item second HTTP 4xx does not trigger a second refresh`() {
        val refresher = RecordingStaleUrlRefresher(
            freshUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = controller(refresher = refresher)
        try {
            val stale = remoteItem("1", url = "https://cdn.example.test/stale.mp3")
            controller.playQueue.setQueue(listOf(stale), startIndex = 0)

            controller.handleStaleUrlErrorForTest(httpError())
            waitUntil("current item is refreshed once") {
                controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }

            controller.handleStaleUrlErrorForTest(httpError())
            // give the coroutine scope a chance to no-op
            Thread.sleep(120)

            assertEquals(1, refresher.evictCalls.size)
            assertEquals(1, refresher.resolveFreshIds.size)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `non-HTTP error does not trigger refresh`() {
        val refresher = RecordingStaleUrlRefresher(
            freshUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = controller(refresher = refresher)
        try {
            controller.playQueue.setQueue(
                listOf(remoteItem("1", url = "https://cdn.example.test/x.mp3")),
                startIndex = 0,
            )

            controller.handleStaleUrlErrorForTest(
                PlaybackException(
                    "decoder",
                    null,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                ),
            )
            Thread.sleep(120)

            assertTrue(refresher.evictCalls.isEmpty())
            assertTrue(refresher.resolveFreshIds.isEmpty())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `retryState resets on queue advance so same item can refresh again after returning`() {
        val refresher = RecordingStaleUrlRefresher(
            freshUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = controller(refresher = refresher)
        try {
            controller.playQueue.setQueue(
                listOf(
                    remoteItem("1", url = "https://cdn.example.test/1.mp3"),
                    remoteItem("2", url = "https://cdn.example.test/2.mp3"),
                ),
                startIndex = 0,
            )

            // First failure on item 1 → refreshes
            controller.handleStaleUrlErrorForTest(httpError())
            waitUntil("item 1 refreshed") {
                controller.playQueue.currentItem?.id == "1" &&
                    controller.playQueue.currentItem?.url == "https://cdn.example.test/fresh.mp3"
            }
            assertEquals(1, refresher.resolveFreshIds.size)

            // Simulate user advancing to item 2 then coming back. The retry-state
            // clear is gated on `setMediaItemAndPlay`, which fires whenever queue
            // navigation enters a new current item — exercise that path directly.
            controller.playItem(remoteItem("2", url = "https://cdn.example.test/2.mp3"))
            waitUntil("advanced to item 2") {
                controller.playQueue.currentItem?.id == "2"
            }
            controller.playItem(remoteItem("1", url = "https://cdn.example.test/1.mp3"))
            waitUntil("returned to item 1") {
                controller.playQueue.currentItem?.id == "1"
            }

            // Second failure on item 1 → should refresh again (state cleared by setMediaItemAndPlay)
            controller.handleStaleUrlErrorForTest(httpError())
            waitUntil("item 1 refreshed a second time") {
                refresher.resolveFreshIds.size == 2
            }
            assertEquals(2, refresher.evictCalls.size)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `local playback source skips refresh entirely`() {
        val refresher = RecordingStaleUrlRefresher(
            freshUrl = "https://cdn.example.test/fresh.mp3",
        )
        val controller = controller(refresher = refresher)
        try {
            controller.playQueue.setQueue(
                listOf(
                    MusicItem(
                        id = "local-1",
                        platform = "local",
                        title = "Local",
                        artist = "Me",
                        album = null,
                        duration = 0L,
                        url = "content://media/external/audio/123",
                        artwork = null,
                        qualities = null,
                    ),
                ),
                startIndex = 0,
            )

            controller.handleStaleUrlErrorForTest(httpError())
            Thread.sleep(120)

            assertTrue(refresher.evictCalls.isEmpty())
            assertTrue(refresher.resolveFreshIds.isEmpty())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `refresh that returns null still clears retry slot so user-driven retry could try again`() {
        val refresher = RecordingStaleUrlRefresher(freshUrl = null)
        val controller = controller(refresher = refresher)
        try {
            controller.playQueue.setQueue(
                listOf(remoteItem("1", url = "https://cdn.example.test/stale.mp3")),
                startIndex = 0,
            )

            controller.handleStaleUrlErrorForTest(httpError())
            waitUntil("resolveFresh has been called") {
                refresher.resolveFreshIds.isNotEmpty()
            }
            // Second error keeps the retry guard in place (no infinite loop)
            controller.handleStaleUrlErrorForTest(httpError())
            Thread.sleep(120)
            assertEquals(1, refresher.resolveFreshIds.size)
        } finally {
            controller.release()
        }
    }

    private fun controller(
        refresher: StaleUrlRefresher,
        resolver: MediaSourceResolver = RecordingResolver(),
        registry: TrackHeaderRegistry = TrackHeaderRegistry(),
    ) = PlayerController(
        context = context,
        mediaSourceResolver = resolver,
        trackHeaderRegistry = registry,
        staleUrlRefresher = refresher,
        listenTracker = mock<ListenTracker>(),
    )

    private fun httpError(): PlaybackException = PlaybackException(
        "http 403",
        null,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

    private fun remoteItem(id: String, url: String?): MusicItem = MusicItem(
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

    private class RecordingStaleUrlRefresher(
        private val freshUrl: String?,
    ) : StaleUrlRefresher {
        val evictCalls = mutableListOf<Triple<String, String, PlayQuality>>()
        val resolveFreshIds = mutableListOf<String>()

        override suspend fun evictCacheEntry(platform: String, id: String, quality: PlayQuality) {
            evictCalls += Triple(platform, id, quality)
        }

        override suspend fun resolveFresh(
            item: MusicItem,
            quality: String?,
        ): MediaSourceResolution? {
            resolveFreshIds += item.id
            val url = freshUrl ?: return null
            return MediaSourceResolution(
                item = item.copy(url = url),
                source = MediaSourceResult(
                    url = url,
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

    private class RecordingResolver(
        private val resolvedUrl: String? = null,
    ) : MediaSourceResolver {
        override suspend fun resolve(item: MusicItem, quality: String?): MediaSourceResolution? {
            val url = resolvedUrl ?: return null
            return MediaSourceResolution(
                item = item.copy(url = url),
                source = MediaSourceResult(url = url, headers = null, userAgent = null, quality = null),
                requestedPlatform = item.platform,
                resolverPlatform = item.platform,
                redirected = false,
            )
        }
    }
}
