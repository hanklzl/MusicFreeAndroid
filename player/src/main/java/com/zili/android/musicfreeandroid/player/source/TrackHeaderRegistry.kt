package com.zili.android.musicfreeandroid.player.source

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide LRU mapping playback url -> per-track HTTP headers + UA.
 *
 * The lookup key is the resolved playback url (not the mediaId), because Media3's
 * ResolvingDataSource sees the dataSpec.uri (= the http url ExoPlayer requests),
 * and MediaItem.customCacheKey only participates when caching is enabled. PlayerController
 * writes (track.url, headers, ua) into the registry just before setMediaItem so the factory
 * can pick the entry up at request time.
 */
@Singleton
class TrackHeaderRegistry @Inject constructor() {

    data class HeaderEntry(val headers: Map<String, String>, val userAgent: String?)

    // accessOrder=true: get() bumps recency
    private val map = object : LinkedHashMap<String, HeaderEntry>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HeaderEntry>?): Boolean =
            size > MAX
    }

    @Synchronized
    fun put(url: String, headers: Map<String, String>, userAgent: String?) {
        map[url] = HeaderEntry(headers, userAgent)
    }

    @Synchronized
    fun get(url: String): HeaderEntry? = map[url]

    companion object {
        const val MAX = 16
    }
}
