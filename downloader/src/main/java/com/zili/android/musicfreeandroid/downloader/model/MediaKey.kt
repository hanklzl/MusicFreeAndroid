package com.zili.android.musicfreeandroid.downloader.model

import com.zili.android.musicfreeandroid.core.model.MusicItem

@JvmInline
value class MediaKey private constructor(val value: String) {
    val id: String get() = value.substringBefore('@')
    val platform: String get() = value.substringAfter('@')

    companion object {
        fun of(id: String, platform: String): MediaKey = MediaKey("$id@$platform")
        fun of(item: MusicItem): MediaKey = of(item.id, item.platform)
    }
}
