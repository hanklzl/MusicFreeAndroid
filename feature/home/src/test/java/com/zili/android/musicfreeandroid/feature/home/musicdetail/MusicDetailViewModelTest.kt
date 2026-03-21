package com.zili.android.musicfreeandroid.feature.home.musicdetail

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.MusicDetailSeedStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MusicDetailViewModelTest {

    @Before
    fun setup() {
        MusicDetailSeedStore.clear()
    }

    @After
    fun tearDown() {
        MusicDetailSeedStore.clear()
    }

    @Test
    fun `base music item prefers rich navigation seed`() {
        val richSeed = track(
            id = "music-1",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            raw = mapOf(
                "songmid" to "song-mid-1",
                "albumId" to "album-42",
                "artistId" to "artist-9",
            ),
        )
        val token = MusicDetailSeedStore.put(richSeed)
        val route = MusicDetailRoute(
            pluginPlatform = "demo",
            musicId = "music-1",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L,
            seedToken = token,
        )
        val resolved = MusicDetailSeedResolver.baseMusicItem(route)

        assertEquals("song-mid-1", resolved.raw["songmid"])
        assertEquals("album-42", resolved.raw["albumId"])
        assertEquals("artist-9", resolved.raw["artistId"])
    }

    @Test
    fun `preview seeds reuse plugin ids from rich raw fields`() {
        val musicItem = track(
            id = "music-1",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            raw = mapOf(
                "songmid" to "song-mid-1",
                "albumId" to "album-42",
                "artistId" to "artist-9",
            ),
        )

        val albumSeed = MusicDetailSeedResolver.albumPreviewSeed(musicItem)
        val artistSeed = MusicDetailSeedResolver.artistPreviewSeed(musicItem)

        assertEquals("album-42", albumSeed?.id)
        assertEquals("song-mid-1", albumSeed?.raw?.get("songmid"))
        assertEquals("artist-9", artistSeed?.id)
        assertEquals("song-mid-1", artistSeed?.raw?.get("songmid"))
    }

    private fun track(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        raw: Map<String, Any?> = emptyMap(),
    ): MusicItem = MusicItem(
        id = id,
        platform = "demo",
        title = title,
        artist = artist,
        album = album,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
        raw = raw,
    )
}
