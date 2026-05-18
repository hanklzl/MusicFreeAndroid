package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hank.musicfree.data.db.entity.RuntimeSnapshotEntity

@Dao
interface RuntimeSnapshotDao {
    @Query("SELECT * FROM runtime_snapshots WHERE namespace = :namespace AND `key` = :key")
    suspend fun get(namespace: String, key: String): RuntimeSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: RuntimeSnapshotEntity)

    @Query("DELETE FROM runtime_snapshots WHERE namespace = :namespace AND `key` = :key")
    suspend fun delete(namespace: String, key: String): Int

    @Query(
        """
        DELETE FROM runtime_snapshots
        WHERE namespace = :namespace
          AND expiresAtEpochMs IS NOT NULL
          AND expiresAtEpochMs <= :nowEpochMs
        """,
    )
    suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int

    @Query(
        """
        DELETE FROM runtime_snapshots
        WHERE namespace = :namespace
          AND `key` NOT IN (
              SELECT `key` FROM runtime_snapshots
              WHERE namespace = :namespace
              ORDER BY updatedAtEpochMs DESC, `key` ASC
              LIMIT :keepLatest
          )
        """,
    )
    suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int

    @Query(
        """
        SELECT `key` FROM runtime_snapshots
        WHERE namespace = :namespace
        ORDER BY updatedAtEpochMs DESC, `key` ASC
        LIMIT :limit
        """,
    )
    suspend fun keys(namespace: String, limit: Int): List<String>
}
