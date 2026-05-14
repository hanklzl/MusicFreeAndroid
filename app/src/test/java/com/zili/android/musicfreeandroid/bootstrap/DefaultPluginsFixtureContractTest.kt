package com.zili.android.musicfreeandroid.bootstrap

import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPluginsFixtureContractTest {

    @Test
    fun `default subscription source remains imported for plugin parity smoke`() {
        assertTrue(
            "Default subscription fixture must include the Yuanli subscription used by plugin parity smoke tests",
            DefaultPlugins.subscriptionUrls.contains("https://13413.kstore.vip/yuanli/yuanli.json"),
        )
    }

    @Test
    fun `default WY plugin direct URL remains available for focused playback smoke`() {
        assertTrue(
            "Default plugin fixture must keep a direct WY plugin URL for focused playback smoke tests",
            DefaultPlugins.pluginUrls.any { it.endsWith("/plugins/wy.js") },
        )
    }
}
