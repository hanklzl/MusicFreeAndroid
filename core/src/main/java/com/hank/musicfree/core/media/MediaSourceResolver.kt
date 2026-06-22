package com.hank.musicfree.core.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality

interface MediaSourceResolver {
    suspend fun resolve(
        item: MusicItem,
        quality: String? = null,
        sid: String? = null,
    ): MediaSourceResolution?

    suspend fun resolveCachedSourceForVerifiedByteCache(
        item: MusicItem,
        quality: PlayQuality,
        sid: String,
    ): MediaSourceResolution? = null
}

data class MediaSourceResolution(
    val item: MusicItem,
    val source: MediaSourceResult,
    val requestedPlatform: String,
    val resolverPlatform: String,
    val redirected: Boolean,
    val cachePolicy: MediaSourceCachePolicy,
)

object EmptyMediaSourceResolver : MediaSourceResolver {
    override suspend fun resolve(
        item: MusicItem,
        quality: String?,
        sid: String?,
    ): MediaSourceResolution? = null
}
