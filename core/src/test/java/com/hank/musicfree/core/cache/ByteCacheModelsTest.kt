package com.hank.musicfree.core.cache

import com.hank.musicfree.core.model.PlayQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ByteCacheModelsTest {

    @Test
    fun `stable key matches playback cache key format`() {
        assertEquals(
            "qq:123:standard",
            ByteCacheKey(
                platform = "qq",
                musicId = "123",
                quality = PlayQuality.STANDARD,
            ).stableKey,
        )
    }

    @Test
    fun `playable verified requires positive content length`() {
        assertThrows(IllegalArgumentException::class.java) {
            status(
                validity = ByteCacheValidity.PlayableVerified,
                contentLength = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            status(
                validity = ByteCacheValidity.PlayableVerified,
                contentLength = 0L,
            )
        }
    }

    @Test
    fun `stale or invalid requires invalid reason`() {
        assertThrows(IllegalArgumentException::class.java) {
            status(
                validity = ByteCacheValidity.StaleOrInvalid,
                invalidReason = null,
            )
        }
    }

    private fun status(
        validity: ByteCacheValidity,
        contentLength: Long? = 100L,
        invalidReason: ByteCacheInvalidReason? = if (validity == ByteCacheValidity.StaleOrInvalid) {
            ByteCacheInvalidReason.BadByteCache
        } else {
            null
        },
    ) = ByteCacheStatus(
        key = ByteCacheKey("qq", "123", PlayQuality.STANDARD),
        validity = validity,
        cachedBytes = 100L,
        contentLength = contentLength,
        validationMethod = ByteCacheValidationMethod.SpanInspection,
        sourceFingerprint = null,
        invalidReason = invalidReason,
        verifiedAt = null,
        updatedAt = 1L,
    )
}
