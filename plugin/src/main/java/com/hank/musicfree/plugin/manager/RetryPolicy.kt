package com.hank.musicfree.plugin.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Run [block]; if it throws (other than CancellationException), wait [delayMs] and run once more.
 * Returns null when both attempts throw, or when [block] returns null on the first attempt.
 *
 * RN parity: only exceptions trigger a retry. A null return is "definitely no source" and is
 * propagated immediately — caller decides what to do (e.g., fall back to musicItem.qualities).
 *
 * See: MusicFree/src/core/pluginManager/plugin.ts — getMediaSource retry logic.
 */
internal suspend inline fun <T> retryOnceOnException(
    delayMs: Long,
    crossinline block: suspend () -> T?,
): T? {
    try {
        return block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // first attempt threw; fall through to retry below
    }
    delay(delayMs)
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
