package com.hank.musicfree.updater.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses canonical v2 manifest with two variants`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": ["新功能 1", "修复 2"],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 23456789,
                  "sha256": "aaaa"
                },
                "x86_64": {
                  "download": ["https://example.com/x64.apk"],
                  "size": 25123456,
                  "sha256": "bbbb"
                }
              },
              "mapping": {
                "url": "https://example.com/mapping.zip",
                "sha256": "cccc"
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(2, info.schemaVersion)
        assertEquals("1.2.3", info.version)
        assertEquals(10203L, info.versionCode)
        assertEquals(setOf("arm64-v8a", "x86_64"), info.variants.keys)
        val arm = info.variants.getValue("arm64-v8a")
        assertEquals(listOf("https://example.com/arm64.apk"), arm.download)
        assertEquals(23456789L, arm.size)
        assertEquals("aaaa", arm.sha256)
        assertNotNull(info.mapping)
        assertEquals("https://example.com/mapping.zip", info.mapping!!.url)
    }

    @Test
    fun `parses v2 manifest without optional mapping`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x"
                }
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(1, info.variants.size)
        assertNull(info.mapping)
    }

    @Test
    fun `ignores unknown fields at root and variant level`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x",
                  "future": "ignored"
                }
              },
              "future": "ignored"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals("1.2.3", info.version)
    }

    @Test(expected = Exception::class)
    fun `rejects missing variants field`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": []
            }
        """.trimIndent()
        json.decodeFromString(UpdateInfo.serializer(), raw)
    }

    @Test
    fun `marks schema version greater than supported`() {
        val raw = """
            {
              "schemaVersion": 99,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x"
                }
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION)
        assertEquals(2, UpdateInfo.SUPPORTED_SCHEMA_VERSION)
    }

    @Test
    fun `accepts empty variants map but consumer must reject`() {
        // 解析层允许 {}；UpdateChecker 负责拒。本测试只确认模型层接受。
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {}
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.variants.isEmpty())
    }
}
