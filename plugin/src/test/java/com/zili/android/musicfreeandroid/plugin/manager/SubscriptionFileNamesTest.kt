package com.zili.android.musicfreeandroid.plugin.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFileNamesTest {

    @Test
    fun `plugin file name is deterministic for same entry`() {
        val entry = SubscriptionPluginEntry(
            index = 0,
            name = "alpha",
            url = "https://example.com/plugins/source.js?raw=1",
            version = "1.0.0",
        )

        val first = SubscriptionFileNames.pluginFileName(entry)
        val second = SubscriptionFileNames.pluginFileName(entry)

        assertEquals(first, second)
        assertTrue(first.endsWith(".js"))
    }

    @Test
    fun `plugin file name avoids collisions for same basename`() {
        val first = SubscriptionPluginEntry(
            index = 0,
            name = "alpha",
            url = "https://example.com/a/source.js",
            version = null,
        )
        val second = SubscriptionPluginEntry(
            index = 1,
            name = "beta",
            url = "https://mirror.example.com/b/source.js",
            version = null,
        )

        val firstName = SubscriptionFileNames.pluginFileName(first)
        val secondName = SubscriptionFileNames.pluginFileName(second)

        assertNotEquals(firstName, secondName)
        assertTrue(firstName.startsWith("001-"))
        assertTrue(secondName.startsWith("002-"))
    }

    @Test
    fun `network plugin file name is reloadable when url has no js suffix`() {
        val fileName = SubscriptionFileNames.networkPluginFileName(
            "https://example.com/download?id=source",
        )

        assertTrue(fileName.startsWith("download-"))
        assertTrue(fileName.endsWith(".js"))
    }
}
