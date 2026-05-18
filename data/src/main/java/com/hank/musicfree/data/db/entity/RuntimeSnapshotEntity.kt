package com.hank.musicfree.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "runtime_snapshots",
    primaryKeys = ["namespace", "key"],
    indices = [
        Index(
            value = ["namespace", "updatedAtEpochMs"],
            name = "index_runtime_snapshots_namespace_updatedAtEpochMs",
        ),
        Index(
            value = ["namespace", "expiresAtEpochMs"],
            name = "index_runtime_snapshots_namespace_expiresAtEpochMs",
        ),
    ],
)
data class RuntimeSnapshotEntity(
    val namespace: String,
    val key: String,
    val snapshotVersion: Int,
    val sourceSignature: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val payloadJson: String,
)
