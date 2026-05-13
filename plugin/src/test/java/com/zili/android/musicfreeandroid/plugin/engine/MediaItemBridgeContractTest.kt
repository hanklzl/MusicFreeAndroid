package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * dev-harness contract: the MusicItem ↔ JS bridge is owned by JsBridge +
 * MusicItemBridgeProjector. Any new bridge consumer MUST go through these
 * helpers; ad-hoc map construction risks leaking reserved keys or skipping the
 * DownloadedTrack / LyricCache projection.
 *
 * Failure of this test indicates a Phase F regression: either the `"$"` defense
 * has been removed, the projector is no longer wiring DownloadedTrack into the
 * bridge map, or `MediaSourceResult.contentType` parsing is gone.
 */
class MediaItemBridgeContractTest {

    @Test
    fun `bridge round-trip never preserves dollar key`() {
        val raw: Map<String, Any?> = mapOf(
            "id" to "1",
            "platform" to "x",
            "title" to "t",
            "artist" to "a",
            "\$" to mapOf("secret" to "leak"),
        )
        val item = JsBridge.toMusicItem(raw)
        val back = JsBridge.musicItemToMap(item)
        assertFalse("bridge map must not contain reserved \$", back.containsKey("\$"))
        assertFalse("bridge map must not contain reserved internal", back.containsKey("internal"))
    }

    @Test
    fun `projector emits localPath when DownloadedTrack exists`() = runTest {
        val downloadedTrackDao = mock<DownloadedTrackDao>()
        val lyricRepository = mock<LyricRepository>()
        whenever(downloadedTrackDao.get("1", "kuwo")).thenReturn(
            DownloadedTrackEntity(
                id = "1",
                platform = "kuwo",
                mediaStoreUri = "content://kuwo/1",
                relativePath = "Music/kuwo-1.mp3",
                mimeType = "audio/mpeg",
                quality = "STANDARD",
                sizeBytes = 200,
                downloadedAt = 1,
            ),
        )
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val projector = MusicItemBridgeProjector(downloadedTrackDao, lyricRepository)
        val map = projector.project(
            MusicItem(
                id = "1",
                platform = "kuwo",
                title = "t",
                artist = "a",
                album = null,
                duration = 1000L,
                url = null,
                artwork = null,
                qualities = null,
            ),
        )

        assertEquals("Music/kuwo-1.mp3", map["localPath"])
        assertEquals(true, map["downloaded"])
    }

    @Test
    fun `parseMediaSourceResult reads contentType when plugin returns it`() {
        val map: Map<String, Any?> = mapOf(
            "url" to "https://cdn.example.com/song.flac",
            "contentType" to "audio/flac",
        )
        val parsed = JsBridge.parseMediaSourceResult(map)
        assertNotNull(parsed)
        assertEquals("audio/flac", parsed!!.contentType)
    }

    @Test
    fun `parseMediaSourceResult treats blank contentType as absent`() {
        val map: Map<String, Any?> = mapOf(
            "url" to "https://cdn.example.com/song.mp3",
            "contentType" to "   ",
        )
        val parsed = JsBridge.parseMediaSourceResult(map)
        assertNotNull(parsed)
        assertNull(parsed!!.contentType)
    }

    @Test
    fun `parseMediaSourceResult contentType defaults to null when absent`() {
        val map: Map<String, Any?> = mapOf(
            "url" to "https://cdn.example.com/song.mp3",
        )
        val parsed = JsBridge.parseMediaSourceResult(map)
        assertNotNull(parsed)
        assertNull(parsed!!.contentType)
    }
}
