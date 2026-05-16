package com.hank.musicfree.plugin.engine

import com.hank.musicfree.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase F: `JsBridge` MUST drop the `"$"` (and `"internal"`) keys at both
 * directions of the bridge boundary.
 *
 * Background: RN MusicFree historically uses `"$"` as a private marker on
 * `IMusicItem` to thread internal state into JS without bleeding into the
 * MusicItem shape. On Android we already keep that state in DataStore /
 * Room (DownloadedTrack, LyricCache) and feed it through
 * [MusicItemBridgeProjector]. A malicious or sloppy plugin returning a `"$"`
 * key in `getMusicInfo` MUST NOT be able to (a) coerce localPath into
 * MusicItem.raw, or (b) round-trip a leaked marker back to JS on the next
 * call.
 */
class JsBridgeDollarKeyDefenseTest {

    @Test
    fun `toMusicItem drops dollar key from parsed raw`() {
        val raw: Map<String, Any?> = mapOf(
            "id" to "1",
            "platform" to "kuwo",
            "title" to "t",
            "artist" to "a",
            "\$" to mapOf("localPath" to "/leaked.mp3", "secret" to "leak"),
        )
        val item = JsBridge.toMusicItem(raw)
        assertFalse(
            "MusicItem.raw must not preserve reserved \$ key",
            item.raw.containsKey("\$"),
        )
        assertNull(
            "leaked \$ map must not populate top-level localPath",
            item.raw["localPath"],
        )
    }

    @Test
    fun `toMusicItem drops internal key from parsed raw`() {
        val raw: Map<String, Any?> = mapOf(
            "id" to "1",
            "platform" to "kuwo",
            "title" to "t",
            "artist" to "a",
            "internal" to mapOf("downloaded" to true),
        )
        val item = JsBridge.toMusicItem(raw)
        assertFalse(item.raw.containsKey("internal"))
    }

    @Test
    fun `musicItemToMap does not emit dollar key even if raw smuggled it`() {
        val item = MusicItem(
            id = "1",
            platform = "x",
            title = "t",
            artist = "a",
            album = null,
            duration = 1000L,
            url = null,
            artwork = null,
            qualities = null,
            // Smuggle reserved key directly into raw — bridge must scrub it.
            raw = mapOf("\$" to mapOf("secret" to "leak"), "stable" to "kept"),
        )
        val out = JsBridge.musicItemToMap(item)
        assertFalse(
            "musicItemToMap must scrub reserved \$ key",
            out.containsKey("\$"),
        )
        assertFalse(
            "musicItemToMap must scrub reserved internal key",
            out.containsKey("internal"),
        )
        // Non-reserved raw keys round-trip normally.
        assertEquals("kept", out["stable"])
    }

    @Test
    fun `round trip is idempotent and drops dollar key`() {
        val raw: Map<String, Any?> = mapOf(
            "id" to "1",
            "platform" to "x",
            "title" to "t",
            "artist" to "a",
            "\$" to mapOf("any" to "thing"),
        )
        val first = JsBridge.musicItemToMap(JsBridge.toMusicItem(raw))
        val second = JsBridge.musicItemToMap(JsBridge.toMusicItem(first))
        assertFalse(first.containsKey("\$"))
        assertFalse(second.containsKey("\$"))
        assertEquals(first, second)
    }
}
