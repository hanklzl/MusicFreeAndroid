package com.zili.android.musicfreeandroid.feature.home.artistdetail.navigation

import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ArtistDetailSeedStore {
    private val seeds = ConcurrentHashMap<String, ArtistItemBase>()

    fun put(item: ArtistItemBase): String {
        val token = UUID.randomUUID().toString()
        seeds[token] = item
        return token
    }

    fun take(token: String?): ArtistItemBase? {
        if (token.isNullOrBlank()) {
            return null
        }
        return seeds.remove(token)
    }

    internal fun clear() {
        seeds.clear()
    }
}
