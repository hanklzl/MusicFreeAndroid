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
        assertTrue(long.startsWith("abcdefgh"))
        assertTrue(long.endsWith("..."))
        assertFalse(long.contains("ijklmnop"))
    }
}
