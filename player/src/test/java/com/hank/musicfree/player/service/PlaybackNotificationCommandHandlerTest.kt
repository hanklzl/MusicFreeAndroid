package com.hank.musicfree.player.service

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test

class PlaybackNotificationCommandHandlerTest {

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `skip commands are no-op without attached controls`() {
        val controls = RecordingControls()
        PlaybackNotificationCommandHandler.attach(controls)
        PlaybackNotificationCommandHandler.detachAllForTest()

        PlaybackNotificationCommandHandler.skipToPrevious()
        PlaybackNotificationCommandHandler.skipToNext()
        PlaybackNotificationCommandHandler.play()
        PlaybackNotificationCommandHandler.close()

        assertEquals(emptyList<String>(), controls.calls)
    }

    @Test
    fun `skip commands delegate to attached controls`() {
        val controls = RecordingControls()
        PlaybackNotificationCommandHandler.attach(controls)

        PlaybackNotificationCommandHandler.skipToPrevious()
        PlaybackNotificationCommandHandler.skipToNext()
        PlaybackNotificationCommandHandler.play()
        PlaybackNotificationCommandHandler.close()

        assertEquals(listOf("previous", "next", "play", "close"), controls.calls)
        PlaybackNotificationCommandHandler.detach(controls)
    }

    @Test
    fun `detach only clears matching controls`() {
        val first = RecordingControls()
        val second = RecordingControls()
        PlaybackNotificationCommandHandler.attach(first)
        PlaybackNotificationCommandHandler.attach(second)

        PlaybackNotificationCommandHandler.detach(first)
        PlaybackNotificationCommandHandler.skipToNext()

        assertEquals(emptyList<String>(), first.calls)
        assertEquals(listOf("next"), second.calls)
        PlaybackNotificationCommandHandler.detach(second)
    }
}

private class RecordingControls : PlaybackNotificationQueueControls {
    val calls = mutableListOf<String>()

    override fun skipToPreviousFromNotification() {
        calls += "previous"
    }

    override fun skipToNextFromNotification() {
        calls += "next"
    }

    override fun playFromNotification() {
        calls += "play"
    }

    override fun closeFromNotification() {
        calls += "close"
    }
}
