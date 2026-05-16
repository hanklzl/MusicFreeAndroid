package com.hank.musicfree.plugin.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSubscriptionParserTest {

    @Test
    fun `parse extracts installable entries and counts invalid rows`() {
        val json = """
            {
              "plugins": [
                {"name":"alpha", "url":"https://example.com/a.js", "version":"1.0.0"},
                {"name":"missing-url", "version":"1.1.0"},
                {"name":"blank-url", "url":"   "},
                {"name":"beta", "url":"https://example.com/b.js"}
              ]
            }
        """.trimIndent()

        val result = SubscriptionParser.parse(json)

        assertTrue(result.isValid)
        assertEquals(4, result.totalEntries)
        assertEquals(
            listOf("https://example.com/a.js", "https://example.com/b.js"),
            result.installableEntries.map { it.url },
        )
    }

    @Test
    fun `parse returns invalid result for malformed json`() {
        val result = SubscriptionParser.parse("{not-json")

        assertTrue(result.isMalformed)
        assertEquals(0, result.totalEntries)
        assertTrue(result.installableEntries.isEmpty())
    }

    @Test
    fun `parse returns invalid result when plugins array is missing`() {
        val result = SubscriptionParser.parse("""{"notPlugins": []}""")

        assertTrue(result.isMalformed)
        assertEquals(0, result.totalEntries)
        assertTrue(result.installableEntries.isEmpty())
    }
}
