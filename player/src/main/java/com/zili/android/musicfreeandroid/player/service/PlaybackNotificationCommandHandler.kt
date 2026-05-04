package com.zili.android.musicfreeandroid.player.service

interface PlaybackNotificationQueueControls {
    fun skipToPreviousFromNotification()
    fun skipToNextFromNotification()
}

object PlaybackNotificationCommandHandler {
    @Volatile
    private var controls: PlaybackNotificationQueueControls? = null

    fun attach(nextControls: PlaybackNotificationQueueControls) {
        controls = nextControls
    }

    fun detach(previousControls: PlaybackNotificationQueueControls) {
        if (controls === previousControls) {
            controls = null
        }
    }

    fun skipToPrevious() {
        controls?.skipToPreviousFromNotification()
    }

    fun skipToNext() {
        controls?.skipToNextFromNotification()
    }

    internal fun detachAllForTest() {
        controls = null
    }
}
