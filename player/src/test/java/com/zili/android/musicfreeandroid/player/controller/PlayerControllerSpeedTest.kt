package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerSpeedTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
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
}
