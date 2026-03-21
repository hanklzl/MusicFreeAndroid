package com.zili.android.musicfreeandroid

import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.HistoryRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
    fun `SettingsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SettingsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SettingsRoute>(json)
        assertNotNull(decoded)
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
}
