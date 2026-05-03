package com.zili.android.musicfreeandroid.core.model

import org.junit.Test
import org.junit.Assert.assertEquals

class SortModeTest {
    @Test
    fun `enum values cover all 6 RN sort modes`() {
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
