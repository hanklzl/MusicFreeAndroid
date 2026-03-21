package com.zili.android.musicfreeandroid

import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.HistoryRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PermissionsRoute
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoutesTest {

    @Test
    fun `HomeRoute is serializable`() {
        val json = Json.encodeToString(serializer(), HomeRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<HomeRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `PlayerRoute is serializable`() {
        val json = Json.encodeToString(serializer(), PlayerRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<PlayerRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `SearchRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SearchRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `HistoryRoute is serializable`() {
        val json = Json.encodeToString(serializer(), HistoryRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<HistoryRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `SearchMusicListRoute is serializable for playlist source`() {
        val route = SearchMusicListRoute.playlist(playlistId = "playlist-42")
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchMusicListRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `SearchMusicListRoute is serializable for history source`() {
        val route = SearchMusicListRoute.history()
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchMusicListRoute>(json)
        assertEquals(route, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects playlist source without playlist id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_PLAYLIST,
            playlistId = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects history source with playlist id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_HISTORY,
            playlistId = "playlist-42",
        )
    }

    @Test
    fun `SettingsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SettingsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SettingsRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `PermissionsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), PermissionsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<PermissionsRoute>(json)
        assertNotNull(decoded)
        assertEquals(PermissionsRoute, decoded)
    }

    @Test
    fun `TopListRoute is serializable`() {
        val json = Json.encodeToString(serializer(), TopListRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<TopListRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `TopListDetailRoute is serializable`() {
        val route = TopListDetailRoute(pluginPlatform = "demo", topListId = "sheet-1")
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<TopListDetailRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `RecommendSheetsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), RecommendSheetsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<RecommendSheetsRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `PluginSheetDetailRoute is serializable`() {
        val route = PluginSheetDetailRoute(
            pluginPlatform = "demo",
            sheetId = "sheet-9",
            title = "热门推荐",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<PluginSheetDetailRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `MusicDetailRoute is serializable`() {
        val route = MusicDetailRoute(
            pluginPlatform = "demo",
            musicId = "m-1",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            artwork = "https://example.com/a.jpg",
            durationMs = 180_000L,
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<MusicDetailRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `MusicListEditorLiteRoute is serializable`() {
        val route = MusicListEditorLiteRoute(playlistId = "playlist-42")
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<MusicListEditorLiteRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `AlbumDetailRoute is serializable`() {
        val route = AlbumDetailRoute(
            pluginPlatform = "demo",
            albumId = "album-1",
            title = "Album A",
            artist = "Artist A",
            artwork = "https://example.com/album.jpg",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<AlbumDetailRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `ArtistDetailRoute is serializable`() {
        val route = ArtistDetailRoute(
            pluginPlatform = "demo",
            artistId = "artist-1",
            name = "Artist A",
            avatar = "https://example.com/artist.jpg",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<ArtistDetailRoute>(json)
        assertNotNull(decoded)
    }
}
