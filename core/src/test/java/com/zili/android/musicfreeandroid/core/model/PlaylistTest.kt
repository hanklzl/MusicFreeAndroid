package com.zili.android.musicfreeandroid.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {
    @Test
    fun `default constructor preserves legacy id name coverUri only signature`() {
        val p = Playlist(id = "abc", name = "Mix", coverUri = null)
        assertEquals("abc", p.id)
        assertEquals("Mix", p.name)
        assertNull(p.coverUri)
        assertNull(p.description)
        assertEquals(SortMode.Manual, p.sortMode)
        assertEquals(0L, p.createdAt)
        assertEquals(0L, p.updatedAt)
        assertEquals(0, p.worksNum)
        assertFalse(p.isDefault)
    }

    @Test
    fun `isDefault true when id equals DEFAULT_FAVORITE_ID`() {
        val p = Playlist(id = Playlist.DEFAULT_FAVORITE_ID, name = "我喜欢", coverUri = null)
        assertTrue(p.isDefault)
    }

    @Test
    fun `DEFAULT_FAVORITE_ID and NAME constants match RN`() {
        assertEquals("favorite", Playlist.DEFAULT_FAVORITE_ID)
        assertEquals("我喜欢", Playlist.DEFAULT_FAVORITE_NAME)
    }
}
