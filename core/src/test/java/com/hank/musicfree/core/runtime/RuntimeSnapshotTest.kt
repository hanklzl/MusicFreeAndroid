package com.hank.musicfree.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSnapshotTest {
    @Test
    fun snapshotExpiresWhenUpdatedAtIsOlderThanTtl() {
        val snapshot = RuntimeSnapshot(
            namespace = "search_session",
            key = RuntimeStoreKey.search("music", "demo", "hash").value,
            snapshotVersion = 1,
            sourceSignature = "plugin:v1",
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 1_000,
            expiresAtEpochMs = 2_000,
            payloadJson = "{}",
        )

        assertFalse(snapshot.isExpired(nowEpochMs = 1_999))
        assertTrue(snapshot.isExpired(nowEpochMs = 2_000))
    }

    @Test
    fun snapshotWithoutExpiryDoesNotExpire() {
        val snapshot = RuntimeSnapshot(
            namespace = "playback",
            key = "playback:current",
            snapshotVersion = 1,
            sourceSignature = "app:1",
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 1_000,
            expiresAtEpochMs = null,
            payloadJson = "{}",
        )

        assertFalse(snapshot.isExpired(nowEpochMs = Long.MAX_VALUE))
    }
}
