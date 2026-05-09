package com.zili.android.musicfreeandroid.core.media

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem

interface MediaSourceResolver {
    suspend fun resolve(
        item: MusicItem,
        quality: String = "standard",
    ): MediaSourceResolution?
}

data class MediaSourceResolution(
    val item: MusicItem,
    val source: MediaSourceResult,
    val requestedPlatform: String,
    val resolverPlatform: String,
    val redirected: Boolean,
)

object EmptyMediaSourceResolver : MediaSourceResolver {
    override suspend fun resolve(
        item: MusicItem,
        quality: String,
    ): MediaSourceResolution? = null
}
