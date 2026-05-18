package com.hank.musicfree.core.runtime

sealed interface RuntimeRestoreResult {
    data object Restored : RuntimeRestoreResult
    data class Skipped(val reason: String) : RuntimeRestoreResult
    data class Stale(val reason: String) : RuntimeRestoreResult
    data class Failed(val reason: String, val error: Throwable? = null) : RuntimeRestoreResult
}
