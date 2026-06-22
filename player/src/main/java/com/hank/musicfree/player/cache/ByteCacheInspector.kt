package com.hank.musicfree.player.cache

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.datasource.cache.ContentMetadata
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class ByteCacheInspection(
    val key: ByteCacheKey,
    val validity: ByteCacheValidity,
    val cachedBytes: Long,
    val contentLength: Long?,
    val holeCount: Int,
)

@Singleton
open class ByteCacheInspector @Inject constructor() {
    @Inject
    lateinit var injectedSimpleCacheHolder: SimpleCacheHolder

    private var overrideSimpleCacheHolder: SimpleCacheHolder? = null

    internal constructor(simpleCacheHolder: SimpleCacheHolder?) : this() {
        overrideSimpleCacheHolder = simpleCacheHolder
    }

    @SuppressLint("UnsafeOptInUsageError")
    open fun inspect(key: ByteCacheKey): ByteCacheInspection {
        val cacheKey = key.stableKey
        val cache = simpleCacheHolderOrNull()?.current
        if (cache == null) {
            return ByteCacheInspection(
                key = key,
                validity = ByteCacheValidity.None,
                cachedBytes = 0L,
                contentLength = null,
                holeCount = 0,
            ).also(::logInspection)
        }

        val spans = cache.getCachedSpans(cacheKey).sortedBy { it.position }
        if (spans.isEmpty()) {
            return ByteCacheInspection(
                key = key,
                validity = ByteCacheValidity.None,
                cachedBytes = 0L,
                contentLength = null,
                holeCount = 0,
            ).also(::logInspection)
        }

        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
            .takeIf { it != C.LENGTH_UNSET.toLong() && it > 0L }
        val cachedBytes = spans.sumOf { it.length }

        var holeCount = 0
        var contiguousEnd = 0L
        spans.forEachIndexed { index, span ->
            if (index == 0 && span.position > 0L) {
                holeCount += 1
            }
            if (index > 0 && span.position > contiguousEnd) {
                holeCount += 1
            }
            contiguousEnd = max(contiguousEnd, span.position + span.length)
        }

        val validity = when {
            holeCount > 0 -> ByteCacheValidity.Partial
            contentLength == null -> ByteCacheValidity.Partial
            contiguousEnd >= contentLength -> ByteCacheValidity.Complete
            else -> ByteCacheValidity.Partial
        }

        return ByteCacheInspection(
            key = key,
            validity = validity,
            cachedBytes = cachedBytes,
            contentLength = contentLength,
            holeCount = holeCount,
        ).also(::logInspection)
    }

    private fun logInspection(inspection: ByteCacheInspection) {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "byte_cache_inspect",
            fields = mapOf(
                "platform" to inspection.key.platform,
                "itemId" to inspection.key.musicId,
                "quality" to inspection.key.quality.name.lowercase(),
                "cacheKey" to inspection.key.stableKey,
                "status" to inspection.validity.logValue(),
                "cachedBytes" to inspection.cachedBytes,
                "contentLength" to inspection.contentLength,
                "holeCount" to inspection.holeCount,
            ),
        )
    }

    private fun simpleCacheHolderOrNull(): SimpleCacheHolder? =
        overrideSimpleCacheHolder ?: if (::injectedSimpleCacheHolder.isInitialized) {
            injectedSimpleCacheHolder
        } else {
            null
        }

    private fun ByteCacheValidity.logValue(): String = when (this) {
        ByteCacheValidity.None -> "none"
        ByteCacheValidity.Partial -> "partial"
        ByteCacheValidity.Complete -> "complete"
        ByteCacheValidity.PlayableVerified -> "playable_verified"
        ByteCacheValidity.StaleOrInvalid -> "stale_or_invalid"
    }
}
