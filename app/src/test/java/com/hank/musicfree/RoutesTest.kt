package com.hank.musicfree

import com.hank.musicfree.core.navigation.AlbumDetailRoute
import com.hank.musicfree.core.navigation.ArtistDetailRoute
import com.hank.musicfree.core.navigation.FileSelectorRoute
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.HistoryRoute
import com.hank.musicfree.core.navigation.MusicDetailRoute
import com.hank.musicfree.core.navigation.MusicListEditorLiteRoute
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.core.navigation.LocalRoute
import com.hank.musicfree.core.navigation.PluginSheetDetailRoute
import com.hank.musicfree.core.navigation.PermissionsRoute
import com.hank.musicfree.core.navigation.RecommendSheetsRoute
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.navigation.TopListDetailRoute
import com.hank.musicfree.core.navigation.TopListRoute
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
    fun `MusicListEditorLiteRoute decodes legacy playlistId payload`() {
        val legacyJson = """{"playlistId":"playlist-42"}"""
        val decoded = Json.decodeFromString<MusicListEditorLiteRoute>(legacyJson)

        assertEquals(MusicListEditorLiteRoute(playlistId = "playlist-42"), decoded)
    }

    @Test
    fun `local library MusicListEditorLiteRoute is serializable`() {
        val route = MusicListEditorLiteRoute.localLibrary()
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
            date = "2026-05-10",
            description = "Album description",
            worksNum = 12,
            seedToken = "seed-album",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<AlbumDetailRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `ArtistDetailRoute is serializable`() {
        val route = ArtistDetailRoute(
            pluginPlatform = "demo",
            artistId = "artist-1",
            name = "Artist A",
            avatar = "https://example.com/artist.jpg",
            description = "Artist description",
            fans = 1234,
            worksNum = 42,
            seedToken = "seed-artist",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<ArtistDetailRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `LocalRoute is serializable`() {
        val json = Json.encodeToString(LocalRoute)
        val decoded = Json.decodeFromString<LocalRoute>(json)
        assertEquals(LocalRoute, decoded)
    }

    @Test
    fun `ListenStatsRoute defaults serialize round-trip`() {
        val route = com.hank.musicfree.core.navigation.ListenStatsRoute()
        val json = kotlinx.serialization.json.Json.encodeToString(
            com.hank.musicfree.core.navigation.ListenStatsRoute.serializer(),
            route,
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            com.hank.musicfree.core.navigation.ListenStatsRoute.serializer(),
            json,
        )
        org.junit.Assert.assertEquals(route, decoded)
        org.junit.Assert.assertEquals("WEEK", decoded.scope)
        org.junit.Assert.assertEquals(-1L, decoded.anchorEpochDay)
    }

    @Test
    fun `ListenDetailRoute requires mode and propagates filterValue`() {
        val route = com.hank.musicfree.core.navigation.ListenDetailRoute(
            mode = "BY_ARTIST",
            scope = "WEEK",
            anchorEpochDay = 20221L,
            filterValue = "周杰伦",
        )
        val json = kotlinx.serialization.json.Json.encodeToString(
            com.hank.musicfree.core.navigation.ListenDetailRoute.serializer(),
            route,
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            com.hank.musicfree.core.navigation.ListenDetailRoute.serializer(),
            json,
        )
        org.junit.Assert.assertEquals(route, decoded)
        org.junit.Assert.assertEquals("周杰伦", decoded.filterValue)
    }
}
