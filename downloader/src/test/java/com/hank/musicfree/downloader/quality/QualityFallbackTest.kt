package com.hank.musicfree.downloader.quality

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityFallbackTest {
    private fun item() = MusicItem(
        id = "1", platform = "qq", title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    private class FakeResolver(private val table: Map<String, MediaSourceResult?>) {
        val callOrder = mutableListOf<String>()
        suspend fun resolve(it: MusicItem, q: String): MediaSourceResult? {
            callOrder += q
            return table[q]
        }
    }

    @Test fun defaultAscStartsAtRequestedQualityThenTriesHigherBeforeLower() = runTest {
        val fr = FakeResolver(mapOf(
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null, quality = null),
            "super" to MediaSourceResult(url = "u-super", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(item(), PlayQuality.HIGH) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.SUPER, q)
        assertEquals("u-super", src.url)
        assertEquals(listOf("high", "super"), fr.callOrder)
    }

    @Test fun ascFallsBackToLowerAfterHigherQualitiesFail() = runTest {
        val fr = FakeResolver(mapOf(
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(
            item(),
            PlayQuality.HIGH,
            QualityFallbackOrder.Asc,
        ) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.STANDARD, q)
        assertEquals("u-std", src.url)
        assertEquals(listOf("high", "super", "standard"), fr.callOrder)
    }

    @Test fun descStartsAtRequestedQualityThenTriesLowerBeforeHigher() = runTest {
        val fr = FakeResolver(mapOf(
            "low" to MediaSourceResult(url = "u-low", headers = null, userAgent = null, quality = null),
            "super" to MediaSourceResult(url = "u-super", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(
            item(),
            PlayQuality.HIGH,
            QualityFallbackOrder.Desc,
        ) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.LOW, q)
        assertEquals("u-low", src.url)
        assertEquals(listOf("high", "standard", "low"), fr.callOrder)
    }

    @Test fun blankUrlIsSkippedSameAsNullResult() = runTest {
        val fr = FakeResolver(mapOf(
            "high" to MediaSourceResult(url = "", headers = null, userAgent = null, quality = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(
            item(),
            PlayQuality.HIGH,
            QualityFallbackOrder.Desc,
        ) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.STANDARD, q)
        assertEquals("u-std", src.url)
        assertEquals(listOf("high", "standard"), fr.callOrder)
    }

    @Test fun returnsNullWhenAllFail() = runTest {
        val fr = FakeResolver(emptyMap())
        val result = QualityFallback.resolve(item(), PlayQuality.LOW) { it, ql -> fr.resolve(it, ql) }
        assertNull(result)
        assertEquals(listOf("low", "standard", "high", "super"), fr.callOrder)
    }
}
