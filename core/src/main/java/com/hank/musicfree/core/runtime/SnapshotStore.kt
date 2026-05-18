package com.hank.musicfree.core.runtime

interface SnapshotStore {
    suspend fun read(namespace: String, key: String): RuntimeSnapshot?
    suspend fun write(snapshot: RuntimeSnapshot)
    suspend fun delete(namespace: String, key: String)
    suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int
    suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int

    /**
     * Returns up to [limit] snapshot keys in [namespace], ordered by latest
     * [RuntimeSnapshot.updatedAtEpochMs] descending (newest first).
     *
     * Stability requirements:
     * - When [limit] <= 0, returns empty list.
     * - Unknown [namespace] returns empty list.
     * - Tied [RuntimeSnapshot.updatedAtEpochMs] MUST be ordered by key ascending.
     */
    suspend fun keys(namespace: String, limit: Int): List<String>
}
