package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PluginSheetSeedStore {
    private val seeds = ConcurrentHashMap<String, MusicSheetItemBase>()

    fun put(item: MusicSheetItemBase): String {
        val token = UUID.randomUUID().toString()
        seeds[token] = item
        return token
    }

    fun take(token: String?): MusicSheetItemBase? {
        if (token.isNullOrBlank()) {
            return null
        }
        return seeds.remove(token)
    }

    internal fun clear() {
        seeds.clear()
    }
}
