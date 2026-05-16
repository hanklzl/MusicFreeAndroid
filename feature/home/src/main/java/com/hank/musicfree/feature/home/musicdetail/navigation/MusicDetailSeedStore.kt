package com.hank.musicfree.feature.home.musicdetail.navigation

import com.hank.musicfree.core.model.MusicItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MusicDetailSeedStore {
    private val seeds = ConcurrentHashMap<String, MusicItem>()

    fun put(item: MusicItem): String {
        val token = UUID.randomUUID().toString()
        seeds[token] = item
        return token
    }

    fun take(token: String?): MusicItem? {
        if (token.isNullOrBlank()) {
            return null
        }
        return seeds.remove(token)
    }

    internal fun clear() {
        seeds.clear()
    }
}
