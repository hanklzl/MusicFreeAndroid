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

    fun skipToPrevious(): Boolean {
        val activeControls = controls ?: return false
        activeControls.skipToPreviousFromNotification()
        return true
    }

    fun skipToNext(): Boolean {
        val activeControls = controls ?: return false
        activeControls.skipToNextFromNotification()
        return true
    }

    fun play(): Boolean {
        val activeControls = controls ?: return false
        activeControls.playFromNotification()
        return true
    }

    fun close(): Boolean {
        val activeControls = controls ?: return false
        activeControls.closeFromNotification()
        return true
    }

    fun diagnosticsSnapshot(): PlaybackNotificationQueueDiagnostics =
        controls?.notificationDiagnostics() ?: PlaybackNotificationQueueDiagnostics.EMPTY

    internal fun detachAllForTest() {
        controls = null
    }
}
