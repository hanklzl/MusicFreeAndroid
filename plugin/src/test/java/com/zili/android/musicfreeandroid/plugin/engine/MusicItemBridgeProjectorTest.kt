package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.data.mapper.LyricCache
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MusicItemBridgeProjectorTest {

    private val downloadedTrackDao = mock<DownloadedTrackDao>()
    private val lyricRepository = mock<LyricRepository>()
    private val projector = MusicItemBridgeProjector(downloadedTrackDao, lyricRepository)

    @Test
    fun `injects localPath and downloaded flag when DownloadedTrack exists`() = runTest {
        whenever(downloadedTrackDao.get("1", "x")).thenReturn(
            DownloadedTrackEntity(
                id = "1",
                platform = "x",
                mediaStoreUri = "content://x/1",
                relativePath = "Music/1.mp3",
                mimeType = "audio/mpeg",
                quality = "STANDARD",
                sizeBytes = 100,
                downloadedAt = 1,
            ),
        )
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertEquals("Music/1.mp3", map["localPath"])
        assertEquals(true, map["downloaded"])
        assertFalse(map.containsKey("\$"))
    }

    @Test
    fun `falls back to mediaStoreUri when DownloadedTrack relativePath is blank`() = runTest {
        whenever(downloadedTrackDao.get("1", "x")).thenReturn(
            DownloadedTrackEntity(
                id = "1",
                platform = "x",
                mediaStoreUri = "content://x/1",
                relativePath = "",
                mimeType = "audio/mpeg",
                quality = "STANDARD",
                sizeBytes = 100,
                downloadedAt = 1,
            ),
        )
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertEquals("content://x/1", map["localPath"])
        assertEquals(true, map["downloaded"])
    }

    @Test
    fun `injects lyricOffset only when non-zero`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any())).thenReturn(
            lyricCache(userOffsetMs = 1500L),
        )

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertEquals(1500L, map["lyricOffset"])
    }

    @Test
    fun `omits lyricOffset when zero`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any())).thenReturn(
            lyricCache(userOffsetMs = 0L),
        )

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertFalse(map.containsKey("lyricOffset"))
    }

    @Test
    fun `omits localPath when no DownloadedTrack and MusicItem has no localPath`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertFalse(map.containsKey("localPath"))
        assertFalse(map.containsKey("downloaded"))
    }

    @Test
    fun `preserves MusicItem localPath when no DownloadedTrack row`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(
            musicItem(id = "1", platform = "本地", localPath = "/sdcard/song.mp3"),
        )

        assertEquals("/sdcard/song.mp3", map["localPath"])
        // No DownloadedTrack row means we shouldn't claim it was downloaded.
        assertNull(map["downloaded"])
    }

    @Test
    fun `swallows DAO failure and still produces base bridge map`() = runTest {
        whenever(downloadedTrackDao.get(any(), any()))
            .thenThrow(IllegalStateException("boom"))
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(musicItem(id = "1", platform = "x"))

        // Core fields must still come through even if the DAO blew up.
        assertEquals("1", map["id"])
        assertEquals("x", map["platform"])
        assertFalse(map.containsKey("localPath"))
        assertFalse(map.containsKey("downloaded"))
    }

    @Test
    fun `never emits reserved bridge keys`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any())).thenReturn(null)

        val map = projector.project(
            musicItem(id = "1", platform = "x").copy(
                raw = mapOf("\$" to "leak", "internal" to "leak"),
            ),
        )

        assertFalse(map.containsKey("\$"))
        assertFalse(map.containsKey("internal"))
    }

    @Test
    fun `swallows lyric repository failure`() = runTest {
        whenever(downloadedTrackDao.get(any(), any())).thenReturn(null)
        whenever(lyricRepository.getCache(any()))
            .thenThrow(IllegalStateException("lyric io down"))

        val map = projector.project(musicItem(id = "1", platform = "x"))

        assertTrue(map["id"] == "1")
        assertFalse(map.containsKey("lyricOffset"))
    }

    private fun musicItem(
        id: String,
        platform: String,
        localPath: String? = null,
    ): MusicItem = MusicItem(
        id = id,
        platform = platform,
        title = "title-$id",
        artist = "artist",
        album = null,
        duration = 1000L,
        url = null,
        artwork = null,
        qualities = null,
        localPath = localPath,
    )

    private fun lyricCache(userOffsetMs: Long): LyricCache = LyricCache(
        musicId = "1",
        musicPlatform = "x",
        remotePayload = null,
        remoteSourceType = null,
        remoteSourcePlatform = null,
        remoteSourceMusicId = null,
        remoteSourceTitle = null,
        localRawLrc = null,
        localTranslation = null,
        associatedMusic = null,
        userOffsetMs = userOffsetMs,
    )
}
