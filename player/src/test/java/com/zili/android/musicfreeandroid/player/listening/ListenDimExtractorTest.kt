package com.zili.android.musicfreeandroid.player.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenDimExtractorTest {
    @Test fun null_or_empty_map_returnsBothNull() {
        assertEquals(null to null, ListenDimExtractor.extract(null))
        assertEquals(null to null, ListenDimExtractor.extract(emptyMap()))
    }
    @Test fun standard_genre_field_normalized() {
        val (lang, genre) = ListenDimExtractor.extract(mapOf("genre" to "流行"))
        assertEquals("pop", genre)
        assertNull(lang)
    }
    @Test fun language_synonyms_mapped() {
        assertEquals("zh-CN", ListenDimExtractor.extract(mapOf("language" to "国语")).first)
        assertEquals("zh-CN", ListenDimExtractor.extract(mapOf("lang" to "Mandarin")).first)
        assertEquals("yue", ListenDimExtractor.extract(mapOf("language" to "粤语")).first)
        assertEquals("en", ListenDimExtractor.extract(mapOf("language" to "English")).first)
    }
    @Test fun tags_array_genre_extraction() {
        val (_, genre) = ListenDimExtractor.extract(mapOf("tags" to listOf("华语流行", "R&B")))
        assertEquals("pop", genre)
    }
    @Test fun unknown_words_returnNull() {
        assertEquals(null to null, ListenDimExtractor.extract(mapOf("genre" to "赛博朋克")))
    }
    @Test fun non_string_field_ignored() {
        assertEquals(null to null, ListenDimExtractor.extract(mapOf("genre" to 42)))
    }
}
