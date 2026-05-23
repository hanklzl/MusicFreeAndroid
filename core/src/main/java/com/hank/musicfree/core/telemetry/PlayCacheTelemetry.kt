package com.hank.musicfree.core.telemetry

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class CacheHitSource(val wire: String) {
    LOCAL("local"), REPO_MEM("repo_mem"), REPO_DB("repo_db"), MEDIA3("media3");
}

enum class CacheMissReason(val wire: String) {
    COLD("cold"), STALE("stale"), DISABLED("disabled"),
    NO_CACHE_POLICY("no_cache_policy"), OFFLINE_ONLY("offline_only");
}

/**
 * Emit helper for the 9-event playback-cache diagnostic trace + 5 metric events.
 * All events go through MfLogger so existing Logan persistence + feedback export still work.
 *
 * Category convention:
 * - PLAYER: playback runtime decisions (hit/miss outcomes, play_session_*, media3_*)
 * - DATA: cache lifecycle (write/evict/lowspace) — visible from the data layer perspective
 */
@Singleton
class PlayCacheTelemetry @Inject constructor(
    private val logger: MfLogger,
) {
    // --- metric events ---
    fun cacheHit(sid: String?, source: CacheHitSource, platform: String, id: String, quality: String, sizeBytes: Long?) =
        logger.detail(LogCategory.PLAYER, "media_cache_hit", baseFields(sid, platform, id, quality) + mapOf(
            "source" to source.wire, "sizeBytes" to sizeBytes,
        ))

    fun cacheMiss(sid: String?, reason: CacheMissReason, platform: String, id: String, quality: String) =
        logger.detail(LogCategory.PLAYER, "media_cache_miss", baseFields(sid, platform, id, quality) + mapOf(
            "reason" to reason.wire,
        ))

    fun cacheWrite(sid: String?, layer: String, sizeBytes: Long?, durationMs: Long?) =
        logger.detail(LogCategory.DATA, "media_cache_write", mapOf(
            "sid" to sid, "layer" to layer, "sizeBytes" to sizeBytes, "durationMs" to durationMs,
        ))

    fun cacheEvict(scope: String, count: Int, freedBytes: Long) =
        logger.detail(LogCategory.DATA, "media_cache_evict", mapOf(
            "scope" to scope, "count" to count, "freedBytes" to freedBytes,
        ))

    fun cacheLowspace(availableBytes: Long, configuredBytes: Long, fallbackBytes: Long) =
        logger.detail(LogCategory.DATA, "media_cache_lowspace", mapOf(
            "availableBytes" to availableBytes,
            "configuredBytes" to configuredBytes,
            "fallbackBytes" to fallbackBytes,
        ))

    // --- 9 diagnostic events ---
    fun playSessionStart(sid: String, platform: String, id: String, requestedQuality: String, networkType: String, isOnline: Boolean) =
        logger.detail(LogCategory.PLAYER, "play_session_start", mapOf(
            "sid" to sid, "platform" to platform, "id" to id,
            "requestedQuality" to requestedQuality,
            "networkType" to networkType, "isOnline" to isOnline,
        ))

    fun resolveLocalCheck(sid: String, hasLocalPath: Boolean, localPathReadable: Boolean?) =
        logger.detail(LogCategory.PLAYER, "resolve_local_check", mapOf(
            "sid" to sid, "hasLocalPath" to hasLocalPath, "localPathReadable" to localPathReadable,
        ))

    /**
     * Emitted when the incoming MusicItem had no localPath but the resolver
     * reverse-queried `music_items` to discover one. `found=true` means the user
     * really had downloaded this track and we recovered the local path — without
     * this lookup the song would have gone over the network (the bug fixed in
     * v1.2.5). `found=false` simply means not downloaded; plugin path proceeds.
     */
    fun resolveLocalDbLookup(sid: String?, platform: String, id: String, found: Boolean) =
        logger.detail(LogCategory.PLAYER, "resolve_local_db_lookup", mapOf(
            "sid" to sid, "platform" to platform, "musicItemId" to id, "found" to found,
        ))

    fun resolveCacheLookup(sid: String?, layer: String, hit: Boolean, qualityMatched: Boolean?, ageSeconds: Long?) =
        logger.detail(LogCategory.PLAYER, "resolve_cache_lookup", mapOf(
            "sid" to sid, "layer" to layer, "hit" to hit,
            "qualityMatched" to qualityMatched, "ageSeconds" to ageSeconds,
        ))

    fun resolvePluginCallStart(sid: String?, pluginName: String) =
        logger.detail(LogCategory.PLAYER, "resolve_plugin_call_start", mapOf(
            "sid" to sid, "pluginName" to pluginName,
        ))

    fun resolvePluginCallEnd(sid: String?, durationMs: Long, returnedQuality: String?, hasUrl: Boolean, hasHeaders: Boolean, urlHash: String?) =
        logger.detail(LogCategory.PLAYER, "resolve_plugin_call_end", mapOf(
            "sid" to sid, "durationMs" to durationMs,
            "returnedQuality" to returnedQuality,
            "hasUrl" to hasUrl, "hasHeaders" to hasHeaders, "urlHash" to urlHash,
        ))

    fun cacheWriteEvent(sid: String?, layer: String, sizeBytes: Long?) =
        logger.detail(LogCategory.DATA, "cache_write", mapOf(
            "sid" to sid, "layer" to layer, "sizeBytes" to sizeBytes,
        ))

    fun media3DataSourceOpen(sid: String?, cacheKey: String, cacheHit: Boolean, bytesFromCache: Long, bytesFromUpstream: Long) =
        logger.detail(LogCategory.PLAYER, "media3_datasource_open", mapOf(
            "sid" to sid, "cacheKey" to cacheKey,
            "cacheHit" to cacheHit,
            "bytesFromCache" to bytesFromCache,
            "bytesFromUpstream" to bytesFromUpstream,
        ))

    fun media3DataSourceError(sid: String?, errorCode: Int, httpStatus: Int?, position: Long, cacheKey: String?, retryCount: Int) =
        logger.error(LogCategory.PLAYER, "media3_datasource_error", null, mapOf(
            "sid" to sid, "errorCode" to errorCode, "httpStatus" to httpStatus,
            "position" to position, "cacheKey" to cacheKey, "retryCount" to retryCount,
        ))

    fun playSessionEnd(sid: String, totalDurationMs: Long, bytesFromCache: Long, bytesFromUpstream: Long, hitClassification: String) =
        logger.detail(LogCategory.PLAYER, "play_session_end", mapOf(
            "sid" to sid, "totalDurationMs" to totalDurationMs,
            "bytesFromCache" to bytesFromCache, "bytesFromUpstream" to bytesFromUpstream,
            "hitClassification" to hitClassification,
        ))

    /** First 8 hex chars of sha1(url) — enough to distinguish, avoids leaking plugin signature tokens. */
    fun urlHash(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 8)
    }

    private fun baseFields(sid: String?, platform: String, id: String, quality: String): Map<String, Any?> =
        mapOf("sid" to sid, "platform" to platform, "musicItemId" to id, "quality" to quality)
}
