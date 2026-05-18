package com.hank.musicfree.player.cache

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCacheStoreImpl @Inject constructor(
    private val holder: SimpleCacheHolder,
) : MediaCacheStore {

    override val usedBytesFlow: Flow<Long> = flow {
        while (true) {
            emit(holder.usedBytes())
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clear() = withContext(Dispatchers.IO) {
        val before = holder.usedBytes()
        val start = System.currentTimeMillis()
        holder.resetForClear()
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "media_cache_cleared",
            fields = mapOf(
                "bytesFreed" to before,
                "durationMs" to (System.currentTimeMillis() - start),
            ),
        )
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
    }
}
