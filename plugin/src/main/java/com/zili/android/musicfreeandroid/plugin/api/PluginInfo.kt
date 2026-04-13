package com.zili.android.musicfreeandroid.plugin.api

data class PluginInfo(
    val platform: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val supportedSearchType: List<String>,
    val appVersion: String? = null,
    val primaryKey: String? = null,
    val defaultSearchType: String? = null,
    val cacheControl: String? = null,
    val hints: Map<String, List<String>>? = null,
)
