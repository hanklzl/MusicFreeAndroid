package com.hank.musicfree.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSplitterTest {
    @Test fun ampersand_and_chinese_comma() {
        assertEquals(listOf("周杰伦", "林俊杰"), splitArtists("周杰伦 & 林俊杰"))
        assertEquals(listOf("A", "B", "C"), splitArtists("A、B、C"))
    }
    @Test fun feat_variants_caseInsensitive() {
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem feat. Rihanna"))
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem FEAT Rihanna"))
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem ft. Rihanna"))
        assertEquals(listOf("Tom", "Jerry"), splitArtists("Tom with Jerry"))
    }
    @Test fun slash_and_western_comma() {
        assertEquals(listOf("a", "b", "c", "d"), splitArtists("a/b、c, d"))
    }
    @Test fun trim_and_dedup_and_dropEmpty() {
        assertEquals(listOf("A", "B"), splitArtists("  A  &  B  &  A  "))
        assertEquals(emptyList<String>(), splitArtists(""))
        assertEquals(emptyList<String>(), splitArtists("   "))
    }
    @Test fun complex_mix() {
        assertEquals(listOf("A", "B", "C", "D", "E"), splitArtists("A, B feat. C / D、E"))
    }
    @Test fun firstOrNull_returnsPrimaryArtist_orNull() {
        assertEquals("周杰伦", splitArtists("周杰伦 & 林俊杰").firstOrNull())
        assertEquals(null, splitArtists("").firstOrNull())
        assertEquals(null, splitArtists("   ").firstOrNull())
    }
}
