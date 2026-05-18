package com.hank.musicfree.data.runtime

import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.db.dao.RuntimeSnapshotDao
import com.hank.musicfree.data.db.entity.RuntimeSnapshotEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRuntimeSnapshotStore @Inject constructor(
    private val dao: RuntimeSnapshotDao,
) : SnapshotStore {
    override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
        dao.get(namespace, key)?.toDomain()

    override suspend fun write(snapshot: RuntimeSnapshot) {
        dao.upsert(snapshot.toEntity())
    }

    override suspend fun delete(namespace: String, key: String) {
        dao.delete(namespace, key)
    }

    override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int =
        dao.deleteExpired(namespace, nowEpochMs)

    override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int =
        dao.pruneNamespace(namespace, keepLatest.coerceAtLeast(0))

    override suspend fun keys(namespace: String, limit: Int): List<String> =
        dao.keys(namespace, limit.coerceAtLeast(0))
}

private fun RuntimeSnapshotEntity.toDomain(): RuntimeSnapshot = RuntimeSnapshot(
    namespace = namespace,
    key = key,
    snapshotVersion = snapshotVersion,
    sourceSignature = sourceSignature,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    payloadJson = payloadJson,
)

private fun RuntimeSnapshot.toEntity(): RuntimeSnapshotEntity = RuntimeSnapshotEntity(
    namespace = namespace,
    key = key,
    snapshotVersion = snapshotVersion,
    sourceSignature = sourceSignature,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    payloadJson = payloadJson,
)
