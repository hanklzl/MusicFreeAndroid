package com.hank.musicfree.core.telemetry

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * Process-wide carrier of the current playback session id (sid).
 *
 * Written at PlayerController.setMediaItemAndPlay entry; read (weakly) by
 * HeaderInjectingDataSourceFactory + CacheDataSourceEventBridge for log correlation.
 *
 * Format: "ps_" + 6 lowercase hex chars (e.g. "ps_a3f1b2").
 */
@Singleton
class CurrentSidProvider @Inject constructor() {
    private val _currentSid = MutableStateFlow<String?>(null)
    val currentSid: StateFlow<String?> = _currentSid

    fun newSession(): String {
        // Use toULong() to avoid sign-flip negatives that would emit '-' in hex.
        val hex = Random.nextLong().toULong().toString(16).padStart(16, '0').takeLast(6)
        val sid = "ps_$hex"
        _currentSid.value = sid
        return sid
    }

    fun peek(): String? = _currentSid.value
}
