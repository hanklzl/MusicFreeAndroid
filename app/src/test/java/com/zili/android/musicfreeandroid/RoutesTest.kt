package com.zili.android.musicfreeandroid

import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.FileSelectorRoute
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.HistoryRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.LocalRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PermissionsRoute
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
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

    @Test
    fun `SearchMusicListRoute is serializable for local-library source`() {
        val route = SearchMusicListRoute.localLibrary()
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchMusicListRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `SearchMusicListRoute is serializable for transient source`() {
        val route = SearchMusicListRoute.transient(sourceId = "session-42")
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchMusicListRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `SearchMusicListRoute decodes legacy playlistId payload`() {
        val legacyJson = """{"sourceType":"playlist","playlistId":"playlist-42"}"""

        val decoded = Json.decodeFromString<SearchMusicListRoute>(legacyJson)

        assertEquals(SearchMusicListRoute.playlist("playlist-42"), decoded)
        assertEquals("playlist-42", decoded.playlistId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects playlist source without playlist id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_PLAYLIST,
            sourceId = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects history source with playlist id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_HISTORY,
            sourceId = "playlist-42",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects local-library source with source id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_LOCAL_LIBRARY,
            sourceId = "local-1",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SearchMusicListRoute rejects transient source without source id`() {
        SearchMusicListRoute(
            sourceType = SearchMusicListRoute.SOURCE_TYPE_TRANSIENT,
            sourceId = null,
        )
    }

    @Test
    fun `SettingsRoute defaults to basic type`() {
        val route = SettingsRoute()
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)

        val decoded = Json.decodeFromString<SettingsRoute>(json)

        assertEquals(SettingsType.Basic, decoded.type)
    }

    @Test
    fun `SettingsRoute serializes every supported type`() {
        SettingsType.entries.forEach { type ->
            val route = SettingsRoute(type = type)
            val json = Json.encodeToString(serializer(), route)
            assertNotNull(json)

            val decoded = Json.decodeFromString<SettingsRoute>(json)

            assertEquals(route, decoded)
        }
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
    fun `FileSelectorRoute is serializable`() {
        val json = Json.encodeToString(serializer(), FileSelectorRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<FileSelectorRoute>(json)
        assertNotNull(decoded)
        assertEquals(FileSelectorRoute, decoded)
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
        val route = TopListDetailRoute(
            pluginPlatform = "demo",
            topListId = "sheet-1",
            title = "飙升榜",
            artist = "官方",
            description = "每日更新",
            coverImg = "https://example.com/top.jpg",
            artwork = "https://example.com/top-art.jpg",
            worksNum = 100,
            seedToken = "seed-1",
        )
        val json = Json.encodeToString(serializer(), route)
        val decoded = Json.decodeFromString<TopListDetailRoute>(json)
        assertEquals(route, decoded)
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
            artist = "编辑精选",
            description = "适合通勤",
            coverImg = "https://example.com/sheet.jpg",
            artwork = "https://example.com/sheet-art.jpg",
            worksNum = 42,
            seedToken = "seed-9",
        )
        val json = Json.encodeToString(serializer(), route)
        val decoded = Json.decodeFromString<PluginSheetDetailRoute>(json)
        assertEquals(route, decoded)
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
            seedToken = "seed-123",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<MusicDetailRoute>(json)
        assertEquals(route, decoded)
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

    @Test
    fun `LocalRoute is serializable`() {
        val json = Json.encodeToString(LocalRoute)
        val decoded = Json.decodeFromString<LocalRoute>(json)
        assertEquals(LocalRoute, decoded)
    }
}
