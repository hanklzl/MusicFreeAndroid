package com.zili.android.musicfreeandroid.player.service

import android.os.Bundle
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

object PlaybackNotificationActions {
    const val ACTION_SKIP_TO_PREVIOUS =
        "com.zili.android.musicfreeandroid.player.action.SKIP_TO_PREVIOUS"
    const val ACTION_SKIP_TO_NEXT =
        "com.zili.android.musicfreeandroid.player.action.SKIP_TO_NEXT"
    const val ACTION_CLOSE =
        "com.zili.android.musicfreeandroid.player.action.CLOSE"

    val SkipToPreviousCommand = SessionCommand(ACTION_SKIP_TO_PREVIOUS, emptyCommandExtras())
    val SkipToNextCommand = SessionCommand(ACTION_SKIP_TO_NEXT, emptyCommandExtras())
    val CloseCommand = SessionCommand(ACTION_CLOSE, emptyCommandExtras())

    @AndroidXOptIn(markerClass = [UnstableApi::class])
    fun mediaButtonPreferences(showCloseButton: Boolean = false): List<CommandButton> {
        return buildList {
            add(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(SkipToPreviousCommand)
                .setDisplayName("上一首")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            )
            add(
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(SkipToNextCommand)
                .setDisplayName("下一首")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            )
            if (showCloseButton) {
                add(
                    CommandButton.Builder(CommandButton.ICON_STOP)
                        .setSessionCommand(CloseCommand)
                        .setDisplayName("关闭")
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build(),
                )
            }
        }
    }

    private fun emptyCommandExtras(): Bundle {
        // Android's JVM test stub can expose Bundle.EMPTY as null; use a real empty Bundle there.
        return Bundle.EMPTY ?: Bundle()
    }
}
