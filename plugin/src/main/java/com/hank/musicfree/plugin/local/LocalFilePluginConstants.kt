package com.hank.musicfree.plugin.local

object LocalFilePluginConstants {
    const val PLATFORM = "本地"
    const val HASH = "local-plugin-hash"
    val SUPPORTED_METHODS: Set<String> = setOf(
        "getMusicInfo",
        "getLyric",
        "importMusicItem",
        "getMediaSource",
    )
}
