package com.hank.musicfree.downloader.quality

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
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
