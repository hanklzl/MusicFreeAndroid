package com.hank.musicfree.player.service

import androidx.media3.common.Player
import com.hank.musicfree.player.service.PlaybackService.ExternalQueueCommandRoute.NEXT
import com.hank.musicfree.player.service.PlaybackService.ExternalQueueCommandRoute.PREVIOUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `standard previous commands route to queue previous`() {
        assertEquals(
            PREVIOUS,
            PlaybackService.externalQueueCommandRoute(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ),
        )
        assertEquals(
            PREVIOUS,
            PlaybackService.externalQueueCommandRoute(
                Player.COMMAND_SEEK_TO_PREVIOUS,
            ),
        )
    }

    @Test
    fun `standard next commands route to queue next`() {
        assertEquals(
            NEXT,
            PlaybackService.externalQueueCommandRoute(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            ),
        )
        assertEquals(
            NEXT,
            PlaybackService.externalQueueCommandRoute(
                Player.COMMAND_SEEK_TO_NEXT,
            ),
        )
    }

    @Test
    fun `non skip player commands do not route to queue controls`() {
        assertNull(PlaybackService.externalQueueCommandRoute(Player.COMMAND_PLAY_PAUSE))
        assertNull(PlaybackService.externalQueueCommandRoute(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }
}
