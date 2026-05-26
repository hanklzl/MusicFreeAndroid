package com.hank.musicfree.player.service

interface PlaybackNotificationQueueControls {
    fun skipToPreviousFromNotification()
    fun skipToNextFromNotification()
    fun playFromNotification()
    fun closeFromNotification()
    fun notificationDiagnostics(): PlaybackNotificationQueueDiagnostics =
        PlaybackNotificationQueueDiagnostics.EMPTY
}

data class PlaybackNotificationQueueDiagnostics(
    val queueIndex: Int,
    val queueSize: Int,
    val currentItemId: String?,
) {
    companion object {
        val EMPTY = PlaybackNotificationQueueDiagnostics(
            queueIndex = -1,
            queueSize = 0,
            currentItemId = null,
        )
    }
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

    fun diagnosticsSnapshot(): PlaybackNotificationQueueDiagnostics =
        controls?.notificationDiagnostics() ?: PlaybackNotificationQueueDiagnostics.EMPTY

    internal fun detachAllForTest() {
        controls = null
    }
}
