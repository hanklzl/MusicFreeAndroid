package com.hank.musicfree.plugin.playback

enum class CacheControl(val wire: String) {
    Cache("cache"),
    NoCache("no-cache"),
    NoStore("no-store");

    companion object {
        fun parse(s: String?): CacheControl = when (s?.lowercase()) {
            "cache" -> Cache
            "no-store" -> NoStore
            else -> NoCache
        }
    }
}

fun shouldUseCache(cc: CacheControl, isOffline: Boolean): Boolean =
    cc == CacheControl.Cache || (cc == CacheControl.NoCache && isOffline)

fun shouldWriteCache(cc: CacheControl, isOffline: Boolean): Boolean = when (cc) {
    CacheControl.Cache -> true
    CacheControl.NoStore -> false
    CacheControl.NoCache -> isOffline // online + no-cache → don't pollute cache
}

@Deprecated(
    message = "Use shouldWriteCache(cc, isOffline)",
    replaceWith = ReplaceWith("shouldWriteCache(cc, isOffline = true)"),
)
fun shouldWriteCache(cc: CacheControl): Boolean = shouldWriteCache(cc, isOffline = true)
