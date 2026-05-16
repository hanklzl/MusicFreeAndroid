package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.Serializable

@Serializable
data class ApkVariant(
    val download: List<String>,
    val size: Long,
    val sha256: String,
)

@Serializable
data class MappingRef(
    val url: String,
    val sha256: String,
)

@Serializable
data class UpdateInfo(
    val schemaVersion: Int,
    val version: String,
    val versionCode: Long,
    val releasedAt: String,
    val releaseNotesUrl: String,
    val changeLog: List<String>,
    val variants: Map<String, ApkVariant>,
    val mapping: MappingRef? = null,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 2
    }
}
