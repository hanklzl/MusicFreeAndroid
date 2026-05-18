package com.hank.musicfree.core.runtime

import kotlinx.coroutines.flow.StateFlow

interface RuntimeStore<S> {
    val storeName: String
    val restoreOnStartup: Boolean
        get() = true
    val state: StateFlow<S>
    suspend fun restore(): RuntimeRestoreResult
    suspend fun persist()
    suspend fun prune(nowEpochMs: Long)
}
