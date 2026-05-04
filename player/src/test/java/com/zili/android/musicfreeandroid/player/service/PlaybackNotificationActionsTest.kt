package com.zili.android.musicfreeandroid.player.service

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(UnstableApi::class)
class PlaybackNotificationActionsTest {

    @Test
    fun `media button preferences expose previous and next custom commands`() {
        val buttons = PlaybackNotificationActions.mediaButtonPreferences()

        assertEquals(2, buttons.size)

        val previous = buttons[0]
        assertEquals(CommandButton.ICON_PREVIOUS, previous.icon)
        assertEquals(CommandButton.SLOT_BACK, previous.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, previous.playerCommand)
        assertNotNull(previous.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS,
            previous.sessionCommand?.customAction,
        )

        val next = buttons[1]
        assertEquals(CommandButton.ICON_NEXT, next.icon)
        assertEquals(CommandButton.SLOT_FORWARD, next.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, next.playerCommand)
        assertNotNull(next.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_NEXT,
            next.sessionCommand?.customAction,
        )
    }
}
