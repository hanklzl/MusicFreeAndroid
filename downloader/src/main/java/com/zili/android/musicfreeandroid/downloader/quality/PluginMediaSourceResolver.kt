package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceResolver @Inject constructor(
    private val pluginManager: PluginManager,
) {
    /**
     * Returns null if no plugin matches; else delegates to plugin.getMediaSource.
     * Caller should fall back to musicItem.url when this returns null.
     */
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? {
        val plugin = pluginManager.getPlugin(item.platform) ?: return null
        return runCatching { plugin.getMediaSource(item, qualityWire) }.getOrNull()
    }
}
