package com.hank.musicfree.data.repository

import com.hank.musicfree.core.cache.ByteCacheInvalidReason
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.ByteCacheValidationMethod
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.db.dao.ByteCacheStatusDao
import com.hank.musicfree.data.db.entity.ByteCacheStatusEntity
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomByteCacheStatusStore @Inject constructor(
    private val dao: ByteCacheStatusDao,
) : ByteCacheStatusStore {
    override suspend fun get(key: ByteCacheKey): ByteCacheStatus? =
        dao.get(key.platform, key.musicId, key.quality.name)?.toModel()

    override suspend fun upsert(status: ByteCacheStatus) {
        dao.upsert(status.toEntity())
        logWrite(status)
    }

    override suspend fun markInvalid(
        key: ByteCacheKey,
        reason: ByteCacheInvalidReason,
        updatedAt: Long,
    ) {
        upsert(
            ByteCacheStatus(
                key = key,
                validity = ByteCacheValidity.StaleOrInvalid,
                cachedBytes = 0L,
                contentLength = null,
                validationMethod = ByteCacheValidationMethod.StaleFailure,
                sourceFingerprint = null,
                invalidReason = reason,
                verifiedAt = null,
                updatedAt = updatedAt,
            ),
        )
    }

    override suspend fun delete(key: ByteCacheKey) {
        dao.delete(key.platform, key.musicId, key.quality.name)
    }

    override suspend fun deleteBySong(platform: String, musicId: String) {
        dao.deleteBySong(platform, musicId)
    }

    override suspend fun deleteByPlatform(platform: String) {
        dao.deleteByPlatform(platform)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun logWrite(status: ByteCacheStatus) {
        MfLog.detail(
            category = LogCategory.DATA,
            event = "byte_cache_status_write",
            fields = mapOf(
                "platform" to status.key.platform,
                "musicItemId" to status.key.musicId,
                "quality" to status.key.quality.name.lowercase(),
                "status" to status.validity.wire,
                "validationMethod" to status.validationMethod.wire,
                "cachedBytes" to status.cachedBytes,
                "contentLength" to status.contentLength,
                "invalidReason" to status.invalidReason?.wire,
            ),
        )
    }
}

private fun ByteCacheStatus.toEntity(): ByteCacheStatusEntity = ByteCacheStatusEntity(
    platform = key.platform,
    musicId = key.musicId,
    quality = key.quality.name,
    status = validity.name,
    cachedBytes = cachedBytes,
    contentLength = contentLength,
    validationMethod = validationMethod.name,
    sourceFingerprint = sourceFingerprint,
    invalidReason = invalidReason?.name,
    verifiedAt = verifiedAt,
    updatedAt = updatedAt,
)

private fun ByteCacheStatusEntity.toModel(): ByteCacheStatus = ByteCacheStatus(
    key = ByteCacheKey(
        platform = platform,
        musicId = musicId,
        quality = PlayQuality.valueOf(quality),
    ),
    validity = ByteCacheValidity.valueOf(status),
    cachedBytes = cachedBytes,
    contentLength = contentLength,
    validationMethod = ByteCacheValidationMethod.valueOf(validationMethod),
    sourceFingerprint = sourceFingerprint,
    invalidReason = invalidReason?.let(ByteCacheInvalidReason::valueOf),
    verifiedAt = verifiedAt,
    updatedAt = updatedAt,
)
