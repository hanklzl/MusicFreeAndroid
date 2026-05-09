package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
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
        val controller = PlayerController(context)

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
        val controller = PlayerController(context, resolver)

        try {
            controller.playQueue(
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
    ) : MediaSourceResolver {
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String,
        ): MediaSourceResolution? {
            requestedIds += item.id
            val source = MediaSourceResult(
                url = resolvedUrl,
                headers = null,
                userAgent = null,
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
}
