package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val schemaVersion: Int,
    val version: String,
    val versionCode: Long,
    val releasedAt: String,
    val download: List<String>,
    val size: Long,
    val sha256: String,
    val changeLog: List<String>,
    val releaseNotesUrl: String,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
