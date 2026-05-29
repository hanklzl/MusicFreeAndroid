package com.hank.musicfree.core.media

enum class MediaSourceCachePolicy(val wire: String) {
    Cache("cache"),
    NoCache("no-cache"),
    NoStore("no-store");

    companion object {
        fun parse(value: String?): MediaSourceCachePolicy = when (value?.lowercase()) {
            "cache" -> Cache
            "no-store" -> NoStore
            else -> NoCache
        }
    }
}

fun MediaSourceCachePolicy.canReadResolvedSource(isOffline: Boolean): Boolean =
    this == MediaSourceCachePolicy.Cache || (this == MediaSourceCachePolicy.NoCache && isOffline)

fun MediaSourceCachePolicy.canWriteResolvedSource(): Boolean =
    this != MediaSourceCachePolicy.NoStore

fun MediaSourceCachePolicy.canWriteByteCache(): Boolean =
    this != MediaSourceCachePolicy.NoStore
