package com.hank.musicfree.core.cache

import com.hank.musicfree.core.model.PlayQuality

data class ByteCacheKey(
    val platform: String,
    val musicId: String,
    val quality: PlayQuality,
) {
    val stableKey: String = "${platform}:${musicId}:${quality.name.lowercase()}"
}

data class ByteCacheStatus(
    val key: ByteCacheKey,
    val validity: ByteCacheValidity,
    val cachedBytes: Long,
    val contentLength: Long?,
    val validationMethod: ByteCacheValidationMethod,
    val sourceFingerprint: String?,
    val invalidReason: ByteCacheInvalidReason?,
    val verifiedAt: Long?,
    val updatedAt: Long,
) {
    init {
        require(cachedBytes >= 0L) { "cachedBytes must be non-negative" }
        require(contentLength == null || contentLength > 0L) {
            "contentLength must be null or positive"
        }
        if (validity == ByteCacheValidity.PlayableVerified) {
            require(contentLength != null && contentLength > 0L) {
                "PlayableVerified byte cache must include a positive contentLength"
            }
        }
        if (validity == ByteCacheValidity.StaleOrInvalid) {
            require(invalidReason != null) {
                "StaleOrInvalid byte cache must include invalidReason"
            }
        }
    }
}

enum class ByteCacheValidity(val wire: String) {
    None("none"),
    Partial("partial"),
    Complete("complete"),
    PlayableVerified("playable_verified"),
    StaleOrInvalid("stale_or_invalid"),
}

enum class ByteCacheValidationMethod(val wire: String) {
    SpanInspection("span_inspection"),
    PlaybackCompleted("playback_completed"),
    ManualEvict("manual_evict"),
    StaleFailure("stale_failure"),
}

enum class ByteCacheInvalidReason(val wire: String) {
    StaleUrl("stale_url"),
    HttpBadStatus("http_bad_status"),
    InvalidContentType("invalid_content_type"),
    ContainerParseFailure("container_parse_failure"),
    BadByteCache("bad_byte_cache"),
    ManualClear("manual_clear"),
    LruEvict("lru_evict"),
    SchemaMigration("schema_migration"),
}

interface ByteCacheStatusStore {
    suspend fun get(key: ByteCacheKey): ByteCacheStatus?

    suspend fun upsert(status: ByteCacheStatus)

    suspend fun markInvalid(
        key: ByteCacheKey,
        reason: ByteCacheInvalidReason,
        updatedAt: Long,
    )

    suspend fun delete(key: ByteCacheKey)

    suspend fun deleteBySong(platform: String, musicId: String)

    suspend fun deleteByPlatform(platform: String) = Unit

    suspend fun deleteAll() = Unit
}

object EmptyByteCacheStatusStore : ByteCacheStatusStore {
    override suspend fun get(key: ByteCacheKey): ByteCacheStatus? = null

    override suspend fun upsert(status: ByteCacheStatus) = Unit

    override suspend fun markInvalid(
        key: ByteCacheKey,
        reason: ByteCacheInvalidReason,
        updatedAt: Long,
    ) = Unit

    override suspend fun delete(key: ByteCacheKey) = Unit

    override suspend fun deleteBySong(platform: String, musicId: String) = Unit

    override suspend fun deleteByPlatform(platform: String) = Unit

    override suspend fun deleteAll() = Unit
}
