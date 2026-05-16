package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val sourcePackageName: String,
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val files: List<BackupManifestFile>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupManifestFile(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupAppMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)
