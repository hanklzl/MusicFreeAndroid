package com.zili.android.musicfreeandroid.feature.home.albumdetail.navigation

import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AlbumDetailSeedStore {
    private val seeds = ConcurrentHashMap<String, AlbumItemBase>()

    fun put(item: AlbumItemBase): String {
        val token = UUID.randomUUID().toString()
        seeds[token] = item
        return token
    }

    fun take(token: String?): AlbumItemBase? {
        if (token.isNullOrBlank()) {
            return null
        }
        return seeds.remove(token)
    }

    internal fun clear() {
        seeds.clear()
    }
}
