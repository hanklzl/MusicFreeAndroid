package com.hank.musicfree.core.model

import org.junit.Test
import org.junit.Assert.assertEquals

class SortModeTest {
    @Test
    fun `enum declares 6 values in canonical order`() {
        assertEquals(
            listOf("Manual", "Title", "Artist", "Album", "Newest", "Oldest"),
            SortMode.entries.map { it.name },
        )
    }

    @Test
    fun `default mode is Manual`() {
        assertEquals(SortMode.Manual, SortMode.DEFAULT)
    }
}
