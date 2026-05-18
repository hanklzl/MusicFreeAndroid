package com.hank.musicfree.player.cache

import kotlinx.coroutines.flow.Flow

interface MediaCacheStore {
    val usedBytesFlow: Flow<Long>
    suspend fun clear()
}
