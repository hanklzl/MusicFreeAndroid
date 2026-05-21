package com.hank.musicfree.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenePagerTabsTest {
    @Test
    fun `selected key resolves to matching page index`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
            ScenePagerPage(key = "c", label = "C"),
        )

        assertEquals(1, resolveScenePagerSelectedIndex(pages, "b"))
    }

    @Test
    fun `missing selected key falls back to first page`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
        )

        assertEquals(0, resolveScenePagerSelectedIndex(pages, "missing"))
    }

    @Test
    fun `null selected key falls back to first page`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
        )

        assertEquals(0, resolveScenePagerSelectedIndex(pages, null))
    }

    @Test
    fun `empty pages resolves to zero`() {
        assertEquals(0, resolveScenePagerSelectedIndex(emptyList<ScenePagerPage<String>>(), "a"))
    }

    @Test
    fun `selection dispatch is skipped when same key already dispatched`() {
        assertEquals(
            false,
            shouldDispatchScenePagerSelection(
                pageKey = "a",
                selectedKey = "b",
                lastDispatchedKey = "a",
            ),
        )
    }

    @Test
    fun `selection dispatch is enabled after external selected key change`() {
        assertEquals(
            true,
            shouldDispatchScenePagerSelection(
                pageKey = "a",
                selectedKey = "b",
                lastDispatchedKey = "b",
            ),
        )
    }

    @Test
    fun `selection dispatch is emitted when different from selected and last dispatched`() {
        assertEquals(
            true,
            shouldDispatchScenePagerSelection(
                pageKey = "c",
                selectedKey = "b",
                lastDispatchedKey = "a",
            ),
        )
    }

    @Test
    fun `selection dispatch is skipped when key matches selected`() {
        assertEquals(
            false,
            shouldDispatchScenePagerSelection(
                pageKey = "a",
                selectedKey = "a",
                lastDispatchedKey = "a",
            ),
        )
    }

    @Test
    fun `selection dispatch is skipped when clicking already selected tab`() {
        assertEquals(
            false,
            shouldDispatchScenePagerSelection(
                pageKey = "a",
                selectedKey = "a",
                lastDispatchedKey = null,
            ),
        )
    }
}
