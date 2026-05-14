package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses canonical version json`() {
        val raw = """
            {
              "schemaVersion": 1,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": [
                "https://example.com/a.apk",
                "https://example.com/b.apk"
              ],
              "size": 23456789,
              "sha256": "f3a8c901",
              "changeLog": ["新功能 1", "修复 2"],
              "releaseNotesUrl": "https://example.com/notes"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(1, info.schemaVersion)
        assertEquals("1.2.3", info.version)
        assertEquals(10203L, info.versionCode)
        assertEquals(2, info.download.size)
        assertEquals(23456789L, info.size)
        assertEquals("f3a8c901", info.sha256)
        assertEquals(listOf("新功能 1", "修复 2"), info.changeLog)
        assertEquals("https://example.com/notes", info.releaseNotesUrl)
    }

    @Test
    fun `ignores unknown fields`() {
        val raw = """
            {
              "schemaVersion": 1,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": ["https://example.com/a.apk"],
              "size": 1,
              "sha256": "x",
              "changeLog": [],
              "releaseNotesUrl": "https://example.com/notes",
              "futureField": "ignored"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals("1.2.3", info.version)
    }

    @Test(expected = Exception::class)
    fun `rejects missing required field`() {
        val raw = """{ "schemaVersion": 1, "version": "1.2.3" }"""
        json.decodeFromString(UpdateInfo.serializer(), raw)
    }

    @Test
    fun `marks schema version greater than supported`() {
        val raw = """
            {
              "schemaVersion": 99,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": ["https://example.com/a.apk"],
              "size": 1,
              "sha256": "x",
              "changeLog": [],
              "releaseNotesUrl": "https://example.com/notes"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION)
    }
}
