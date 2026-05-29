package com.hank.musicfree.player.source

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.canWriteByteCache
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCacheKeyRegistrar @Inject constructor(
    private val trackHeaderRegistry: TrackHeaderRegistry,
) {
    sealed class Result {
        object SkippedBlankUrl : Result()
        data class SkippedNonHttp(val scheme: String?) : Result()
        data class Registered(val cacheKey: String) : Result()
    }

    enum class Trigger(val wire: String) {
        PLAYBACK("playback"),
        STALE_REFRESH("stale_refresh"),
        FAILURE_SOURCE_CHANGE("failure_source_change"),
        PREFETCH("prefetch"),
    }

    fun register(
        platform: String,
        itemId: String,
        url: String?,
        headers: Map<String, String>,
        userAgent: String?,
        quality: PlayQuality,
        cachePolicy: MediaSourceCachePolicy,
        trigger: Trigger,
    ): Result {
        if (url.isNullOrBlank()) return Result.SkippedBlankUrl

        val parsed = runCatching { URI(url) }.getOrElse { exception ->
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "media_cache_key_parse_failed",
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to itemId,
                    "url" to url,
                    "reason" to "invalid_url",
                    "errorClass" to exception::class.java.name,
                ),
            )
            return Result.SkippedNonHttp(null)
        }

        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.SkippedNonHttp(scheme)
        }

        val cacheKey = "$platform:$itemId"
        val byteCacheAllowed = cachePolicy.canWriteByteCache()
        trackHeaderRegistry.put(
            url = url,
            headers = headers,
            userAgent = userAgent,
            cacheKey = cacheKey,
            quality = quality,
            byteCacheAllowed = byteCacheAllowed,
            cachePolicy = cachePolicy,
        )
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "media_cache_key_registered",
            fields = mapOf(
                "platform" to platform,
                "itemId" to itemId,
                "quality" to quality.name.lowercase(),
                "trigger" to trigger.wire,
                "host" to (parsed.host ?: ""),
                "cachePolicy" to cachePolicy.wire,
                "byteCacheAllowed" to byteCacheAllowed,
                "hasHeaders" to headers.isNotEmpty(),
                "hasUserAgent" to !userAgent.isNullOrBlank(),
            ),
        )
        return Result.Registered(cacheKey)
    }
}
