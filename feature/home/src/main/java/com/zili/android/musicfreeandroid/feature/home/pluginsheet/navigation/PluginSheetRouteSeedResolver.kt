package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

internal class PluginSheetRouteSeedResolver(
    private val seedToken: String?,
    private val fallbackSeed: () -> MusicSheetItemBase,
) {
    private var cachedSeed: MusicSheetItemBase? = null

    fun resolve(): MusicSheetItemBase {
        val cached = cachedSeed
        if (cached != null) {
            return cached
        }
        return (PluginSheetSeedStore.take(seedToken) ?: fallbackSeed()).also { seed ->
            cachedSeed = seed
        }
    }
}
