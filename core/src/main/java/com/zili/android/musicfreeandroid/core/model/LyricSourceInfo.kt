package com.zili.android.musicfreeandroid.core.model

sealed interface LyricSourceInfo {
    data class Plugin(val platform: String) : LyricSourceInfo
    data class AutoSearch(val platform: String, val title: String, val id: String) : LyricSourceInfo
    data class Associated(val platform: String, val title: String, val id: String) : LyricSourceInfo
    data object LocalRaw : LyricSourceInfo
    data object LocalTranslation : LyricSourceInfo
    data object Cache : LyricSourceInfo
}
