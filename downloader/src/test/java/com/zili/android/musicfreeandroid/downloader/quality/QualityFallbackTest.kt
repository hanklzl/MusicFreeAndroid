package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
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

    @Test fun startsAtRequestedQualityAndStepsDown() = runTest {
        val fr = FakeResolver(mapOf(
            "super" to null,
            "high" to MediaSourceResult(url = "u-high", headers = null, userAgent = null, quality = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(item(), PlayQuality.SUPER) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.HIGH, q)
        assertEquals("u-high", src.url)
        assertEquals(listOf("super", "high"), fr.callOrder)
    }

    @Test fun blankUrlIsSkippedSameAsNullResult() = runTest {
        val fr = FakeResolver(mapOf(
            "high" to MediaSourceResult(url = "", headers = null, userAgent = null, quality = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null, quality = null),
        ))
        val (q, src) = QualityFallback.resolve(item(), PlayQuality.HIGH) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.STANDARD, q)
        assertEquals("u-std", src.url)
        assertEquals(listOf("high", "standard"), fr.callOrder)
    }

    @Test fun returnsNullWhenAllFail() = runTest {
        val fr = FakeResolver(emptyMap())
        val result = QualityFallback.resolve(item(), PlayQuality.LOW) { it, ql -> fr.resolve(it, ql) }
        assertNull(result)
        assertEquals(listOf("low"), fr.callOrder)
    }
}
