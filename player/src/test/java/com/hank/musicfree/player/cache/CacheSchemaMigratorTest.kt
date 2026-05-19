package com.hank.musicfree.player.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [CacheSchemaMigrator].
 *
 * No Android runtime is required — CacheSchemaMigrator is a plain Kotlin object.
 */
class CacheSchemaMigratorTest {

    // ── isLegacyKey: known quality suffixes → NOT legacy ─────────────────────

    @Test fun `isLegacyKey returns false for low suffix`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("kugou:song1:low"))
    }

    @Test fun `isLegacyKey returns false for standard suffix`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("netease:abc:standard"))
    }

    @Test fun `isLegacyKey returns false for high suffix`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("qq:xyz:high"))
    }

    @Test fun `isLegacyKey returns false for super suffix`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("migu:id123:super"))
    }

    @Test fun `isLegacyKey returns false for unknown suffix`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("bilibili:vid:unknown"))
    }

    @Test fun `isLegacyKey returns false for uppercase quality suffix (case insensitive)`() {
        assertFalse(CacheSchemaMigrator.isLegacyKey("kugou:song1:HIGH"))
        assertFalse(CacheSchemaMigrator.isLegacyKey("kugou:song1:Low"))
        assertFalse(CacheSchemaMigrator.isLegacyKey("kugou:song1:STANDARD"))
    }

    // ── isLegacyKey: no known quality suffix → IS legacy ────────────────────

    @Test fun `isLegacyKey returns true for plain platform-colon-id key`() {
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:song1"))
    }

    @Test fun `isLegacyKey returns true for complex id without quality suffix`() {
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:complex:id:noquality"))
    }

    @Test fun `isLegacyKey returns true for key with unrecognized final segment`() {
        assertTrue(CacheSchemaMigrator.isLegacyKey("netease:abc123:mp3"))
    }

    @Test fun `isLegacyKey returns true for key ending in partial quality word`() {
        // "hig" is not "high"
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:id:hig"))
        // "standar" is not "standard"
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:id:standar"))
    }
}
