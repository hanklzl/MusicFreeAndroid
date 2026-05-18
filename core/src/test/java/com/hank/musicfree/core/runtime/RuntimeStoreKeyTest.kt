package com.hank.musicfree.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RuntimeStoreKeyTest {
    @Test
    fun singletonKeyIsStableAndEncoded() {
        assertEquals(
            "ui_runtime:current",
            RuntimeStoreKey.singleton("ui_runtime").value,
        )
        assertEquals(
            "ui%3Aruntime:current",
            RuntimeStoreKey.singleton("ui:runtime").value,
        )
    }

    @Test
    fun searchKeyIsStableAndNamespaced() {
        assertEquals(
            "search:music:demo-platform:hello-world",
            RuntimeStoreKey.search(
                mediaType = "music",
                platform = "demo-platform",
                queryHash = "hello-world",
            ).value,
        )
    }

    @Test
    fun detailKeyIsStableAndNamespaced() {
        assertEquals(
            "detail:plugin_sheet:demo:id-123",
            RuntimeStoreKey.detail(
                type = "plugin_sheet",
                platform = "demo",
                id = "id-123",
            ).value,
        )
    }

    @Test
    fun routeSeedKeyIsStableAndNamespaced() {
        assertEquals(
            "route_seed:album:demo:id-9",
            RuntimeStoreKey.routeSeed(
                target = "album",
                platform = "demo",
                id = "id-9",
            ).value,
        )
    }

    @Test
    fun keyPreservesCaseForOpaquePluginIds() {
        // 插件返回的 id 可能是 base64 / hash，大小写敏感，不能合并
        val upper = RuntimeStoreKey.detail("plugin_sheet", "demo", "ABCdef").value
        val lower = RuntimeStoreKey.detail("plugin_sheet", "demo", "abcdef").value
        assertNotEquals(upper, lower)
        assertEquals("detail:plugin_sheet:demo:ABCdef", upper)
    }

    @Test
    fun keyEncodingAvoidsCollisionsForUnsafeChars() {
        val colon = RuntimeStoreKey.detail("plugin_sheet", "demo", "a:b").value
        val dash = RuntimeStoreKey.detail("plugin_sheet", "demo", "a-b").value
        assertEquals("detail:plugin_sheet:demo:a%3Ab", colon)
        assertEquals("detail:plugin_sheet:demo:a-b", dash)
        assertNotEquals(colon, dash)
    }

    @Test
    fun keyEncodingKeepsSafeCharsUnchanged() {
        assertEquals(
            "detail:plugin_sheet:demo:AazZ09._-",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "AazZ09._-").value,
        )
    }

    @Test
    fun keyEncodingPercentSign() {
        assertEquals(
            "detail:plugin_sheet:demo:%25percent",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "%percent").value,
        )
    }

    @Test
    fun keyEncodingSupportsUtf8Multibyte() {
        assertEquals(
            "detail:plugin_sheet:demo:%E6%AD%8C%E5%8D%95",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "歌单").value,
        )
    }

    @Test
    fun keyEncodingSupportsMultibyteEmoji() {
        assertEquals(
            "detail:plugin_sheet:demo:%F0%9F%8E%B5",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "🎵").value,
        )
    }

    @Test
    fun keyUsesUnknownWhenSegmentIsBlankAndTrims() {
        assertEquals(
            "search:unknown:demo:unknown",
            RuntimeStoreKey.search(mediaType = "   ", platform = "demo", queryHash = "   ").value,
        )
    }

    @Test
    fun emptyOrBlankSegmentsMapToUnknown() {
        assertEquals(
            "search:unknown:unknown:unknown",
            RuntimeStoreKey.search(mediaType = "", platform = "   ", queryHash = "").value,
        )
    }

    @Test
    fun keyKeepsSafeDelimiterSegmentsAsIs() {
        assertEquals(
            "detail:plugin_sheet:demo:_",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "_").value,
        )
        assertEquals(
            "detail:plugin_sheet:demo:.",
            RuntimeStoreKey.detail("plugin_sheet", "demo", ".").value,
        )
        assertEquals(
            "detail:plugin_sheet:demo:-",
            RuntimeStoreKey.detail("plugin_sheet", "demo", "-").value,
        )
    }
}
