package com.hank.musicfree.player.service

interface PlaybackNotificationQueueControls {
    fun skipToPreviousFromNotification()
    fun skipToNextFromNotification()
    fun playFromNotification()
    fun closeFromNotification()
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

    fun play() {
        controls?.playFromNotification()
    }

    fun close() {
        controls?.closeFromNotification()
    }

    internal fun detachAllForTest() {
        controls = null
    }
}
