package com.zili.android.musicfreeandroid

import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
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
    fun `SettingsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SettingsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SettingsRoute>(json)
        assertNotNull(decoded)
    }
}
