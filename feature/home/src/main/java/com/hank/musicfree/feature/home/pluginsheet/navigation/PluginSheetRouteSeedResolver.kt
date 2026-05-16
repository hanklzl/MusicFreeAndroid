package com.hank.musicfree.feature.home.pluginsheet.navigation

import com.hank.musicfree.plugin.api.MusicSheetItemBase

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
