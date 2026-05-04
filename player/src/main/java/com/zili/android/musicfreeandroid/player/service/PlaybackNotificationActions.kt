package com.zili.android.musicfreeandroid.player.service

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

object PlaybackNotificationActions {
    const val ACTION_SKIP_TO_PREVIOUS =
        "com.zili.android.musicfreeandroid.player.action.SKIP_TO_PREVIOUS"
    const val ACTION_SKIP_TO_NEXT =
        "com.zili.android.musicfreeandroid.player.action.SKIP_TO_NEXT"

    val SkipToPreviousCommand = SessionCommand(ACTION_SKIP_TO_PREVIOUS, emptyCommandExtras())
    val SkipToNextCommand = SessionCommand(ACTION_SKIP_TO_NEXT, emptyCommandExtras())

    fun mediaButtonPreferences(): List<CommandButton> {
        return listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(SkipToPreviousCommand)
                .setDisplayName("上一首")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(SkipToNextCommand)
                .setDisplayName("下一首")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }

    private fun emptyCommandExtras(): Bundle = Bundle.EMPTY ?: Bundle()
}
