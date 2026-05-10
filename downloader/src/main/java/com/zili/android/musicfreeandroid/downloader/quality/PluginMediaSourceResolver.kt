package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceResolver @Inject constructor(
    private val mediaSourceResolver: MediaSourceResolver,
) {
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? {
        return mediaSourceResolver.resolve(item, qualityWire)?.source
    }
}
