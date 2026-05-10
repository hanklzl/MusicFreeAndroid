package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.downloader.model.MediaKey

class FakeQualityResolver {
    private val table = mutableMapOf<MediaKey, MediaSourceResult?>()
    fun bind(key: MediaKey, result: MediaSourceResult?) { table[key] = result }
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? =
        table[MediaKey.of(item)]
}
