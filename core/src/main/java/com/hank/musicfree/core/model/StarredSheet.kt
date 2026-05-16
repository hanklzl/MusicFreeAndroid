package com.hank.musicfree.core.model

data class StarredSheet(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val kind: String = StarredKind.SHEET,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val raw: Map<String, Any?> = emptyMap(),
)
