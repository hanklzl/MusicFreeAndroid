package com.hank.musicfree.player.service

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceCommandRoutingTest {

    @Test
    fun `app controller empty session play does not route to notification fallback`() {
        assertFalse(
            PlaybackService.shouldRouteEmptySessionPlayToNotification(
                playerCommand = Player.COMMAND_PLAY_PAUSE,
                sessionMediaItemCount = 0,
                controllerPackage = "com.hank.musicfree",
                servicePackage = "com.hank.musicfree",
            ),
        )
    }

    @Test
    fun `external controller empty session play routes to notification fallback`() {
        assertTrue(
            PlaybackService.shouldRouteEmptySessionPlayToNotification(
                playerCommand = Player.COMMAND_PLAY_PAUSE,
                sessionMediaItemCount = 0,
                controllerPackage = "com.android.systemui",
                servicePackage = "com.hank.musicfree",
            ),
        )
    }

    @Test
    fun `non empty session play does not route to notification fallback`() {
        assertFalse(
            PlaybackService.shouldRouteEmptySessionPlayToNotification(
                playerCommand = Player.COMMAND_PLAY_PAUSE,
                sessionMediaItemCount = 1,
                controllerPackage = "com.android.systemui",
                servicePackage = "com.hank.musicfree",
            ),
        )
    }
}
