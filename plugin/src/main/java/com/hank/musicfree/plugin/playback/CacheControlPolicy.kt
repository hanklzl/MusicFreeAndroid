package com.hank.musicfree.plugin.playback

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.canReadResolvedSource
import com.hank.musicfree.core.media.canWriteResolvedSource

private val cacheControlWireValues = listOf(
    "cache",
    "no-cache",
    "no-store",
)

typealias CacheControl = MediaSourceCachePolicy

fun shouldUseCache(cacheControl: CacheControl, isOffline: Boolean): Boolean =
    cacheControl.canReadResolvedSource(isOffline)

fun shouldWriteCache(cacheControl: CacheControl, isOffline: Boolean): Boolean =
    cacheControl.canWriteResolvedSource()

@Deprecated(
    message = "Use shouldWriteCache(cacheControl, isOffline = true)",
    replaceWith = ReplaceWith("shouldWriteCache(cacheControl, isOffline = true)"),
)
fun shouldWriteCache(cacheControl: CacheControl): Boolean = shouldWriteCache(cacheControl, isOffline = true)
