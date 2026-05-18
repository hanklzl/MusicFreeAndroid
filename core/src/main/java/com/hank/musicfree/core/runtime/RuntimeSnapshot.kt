package com.hank.musicfree.core.runtime

data class RuntimeSnapshot(
    val namespace: String,
    val key: String,
    val snapshotVersion: Int,
    val sourceSignature: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val payloadJson: String,
) {
    fun isExpired(nowEpochMs: Long): Boolean =
        expiresAtEpochMs?.let { nowEpochMs >= it } == true
}
