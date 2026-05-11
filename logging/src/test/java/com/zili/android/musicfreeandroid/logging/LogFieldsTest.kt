package com.zili.android.musicfreeandroid.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFieldsTest {
    @Test
    fun `new diagnostic categories have stable wire names`() {
        assertEquals("data", LogCategory.DATA.wireName)
        assertEquals("file_io", LogCategory.FILE_IO.wireName)
        assertEquals("download", LogCategory.DOWNLOAD.wireName)
        assertEquals("settings", LogCategory.SETTINGS.wireName)
        assertEquals("home", LogCategory.HOME.wireName)
        assertEquals("lyrics", LogCategory.LYRICS.wireName)
    }

    @Test
    fun `result helpers use stable values`() {
        assertEquals("success", LogFields.Result.SUCCESS)
        assertEquals("failure", LogFields.Result.FAILURE)
        assertEquals("cancelled", LogFields.Result.CANCELLED)
        assertEquals("stale", LogFields.Result.STALE)
        assertEquals("skipped", LogFields.Result.SKIPPED)
    }

    @Test
    fun `reason helpers use stable values`() {
        assertEquals("cancelled", LogFields.Reason.CANCELLED)
        assertEquals("stale_generation", LogFields.Reason.STALE_GENERATION)
        assertEquals("empty_input", LogFields.Reason.EMPTY_INPUT)
        assertEquals("not_found", LogFields.Reason.NOT_FOUND)
        assertEquals("duplicate", LogFields.Reason.DUPLICATE)
        assertEquals("network_unavailable", LogFields.Reason.NETWORK_UNAVAILABLE)
        assertEquals("cellular_blocked", LogFields.Reason.CELLULAR_BLOCKED)
        assertEquals("unsupported", LogFields.Reason.UNSUPPORTED)
        assertEquals("invalid_url", LogFields.Reason.INVALID_URL)
        assertEquals("unknown", LogFields.Reason.UNKNOWN)
    }

    @Test
    fun `field helpers produce stable keys`() {
        assertEquals("operation" to "load_initial", LogFields.operation("load_initial"))
        assertEquals("screen" to "player", LogFields.screen("player"))
        assertEquals("result" to "success", LogFields.result(LogFields.Result.SUCCESS))
        assertEquals("reason" to "not_found", LogFields.reason(LogFields.Reason.NOT_FOUND))
        assertEquals("platform" to "netease", LogFields.platform("netease"))
        assertEquals("platform" to "", LogFields.platform(null))
        assertEquals(
            mapOf("itemId" to "song-1", "itemName" to "Song"),
            LogFields.item("song-1", "Song"),
        )
    }

    @Test
    fun `host extracts only network host`() {
        assertEquals("example.com", LogFields.host("https://example.com/path?q=1"))
        assertEquals("example.com", LogFields.host("http://example.com:8080/path"))
        assertEquals("", LogFields.host("not a url"))
        assertEquals("", LogFields.host(null))
    }

    @Test
    fun `trimmed preview preserves short input and marks truncation`() {
        assertEquals("short", LogFields.preview("short", maxLength = 8))
        val long = LogFields.preview("abcdefghijklmnop", maxLength = 8)
        assertEquals(8, long.length)
        assertTrue(long.startsWith("abcde"))
        assertTrue(long.endsWith("..."))
        assertFalse(long.contains("ijklmnop"))
        assertEquals("", LogFields.preview("abc", maxLength = 0))
        assertEquals("", LogFields.preview("abc", maxLength = -1))
        assertEquals("..", LogFields.preview("abcdef", maxLength = 2))
    }
}
