package com.hank.musicfree.plugin.api

data class PluginInfo(
    val platform: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val supportedSearchType: List<String>,
    val supportedSearchTypeDeclared: Boolean = supportedSearchType.isNotEmpty(),
    val appVersion: String? = null,
    val primaryKey: String? = null,
    val defaultSearchType: String? = null,
    val cacheControl: String? = null,
    val hints: Map<String, List<String>>? = null,
    val supportedMethods: Set<String> = emptySet(),
    val userVariables: List<PluginUserVariable> = emptyList(),
    /**
     * SHA-256 hex of the original plugin JS bytes (lowercase). Populated for
     * JS-backed plugins by `PluginManager.loadPluginFromFile`. Used to detect
     * idempotent re-installs (Phase C5 — RN-compatible silent dedup). May be
     * null for synthetic / built-in plugins where the concept of "bytes" does
     * not apply (e.g. the 本地 plugin uses a stable sentinel from
     * `LocalFilePluginConstants.HASH`).
     */
    val hash: String? = null,
)
