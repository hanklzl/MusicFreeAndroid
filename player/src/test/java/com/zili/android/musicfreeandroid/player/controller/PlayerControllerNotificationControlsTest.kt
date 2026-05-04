package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
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
}
