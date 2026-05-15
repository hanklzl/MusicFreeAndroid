package com.zili.android.musicfreeandroid.player.service

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackNotificationActionsTest {

    @Test
    fun `media button preferences expose previous and next custom commands`() {
        val buttons = PlaybackNotificationActions.mediaButtonPreferences()

        assertEquals(2, buttons.size)

        val previous = buttons[0]
        assertEquals(CommandButton.ICON_PREVIOUS, previous.icon)
        assertEquals("上一首", previous.displayName)
        assertEquals(CommandButton.SLOT_BACK, previous.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, previous.playerCommand)
        assertNotNull(previous.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS,
            previous.sessionCommand?.customAction,
        )

        val next = buttons[1]
        assertEquals(CommandButton.ICON_NEXT, next.icon)
        assertEquals("下一首", next.displayName)
        assertEquals(CommandButton.SLOT_FORWARD, next.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, next.playerCommand)
        assertNotNull(next.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_NEXT,
            next.sessionCommand?.customAction,
        )
    }

    @Test
    fun `media button preferences include close command when enabled`() {
        val buttons = PlaybackNotificationActions.mediaButtonPreferences(showCloseButton = true)

        assertEquals(3, buttons.size)

        val close = buttons[2]
        assertEquals(CommandButton.ICON_STOP, close.icon)
        assertEquals("关闭", close.displayName)
        assertEquals(CommandButton.SLOT_OVERFLOW, close.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, close.playerCommand)
        assertNotNull(close.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_CLOSE,
            close.sessionCommand?.customAction,
        )
    }
}
