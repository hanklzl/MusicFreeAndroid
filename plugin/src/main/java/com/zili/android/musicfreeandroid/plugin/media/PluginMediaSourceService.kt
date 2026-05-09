package com.zili.android.musicfreeandroid.plugin.media

import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
) : MediaSourceResolver {

    override suspend fun resolve(
        item: MusicItem,
        quality: String,
    ): MediaSourceResolution? {
        if (!item.url.isNullOrBlank()) {
            return null
        }
        val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
        val disabled = pluginManager.pluginMetaStore.disabledPlugins.first()
        val alternatives = pluginManager.pluginMetaStore.alternativePlugins.first()
        val alternativePlatform = alternatives[item.platform]
            ?.takeUnless { it == item.platform }
            ?.takeUnless { it in disabled }
        val alternativePlugin = alternativePlatform
            ?.let { pluginManager.getPlugin(it) }
            ?.takeIf { it.supportsMediaSource() }

        alternativePlugin?.resolveWith(
            item = item,
            quality = quality,
            requestedPlatform = item.platform,
            redirected = true,
        )?.let { return it }

        return sourcePlugin.resolveWith(
            item = item,
            quality = quality,
            requestedPlatform = item.platform,
            redirected = false,
        )
    }

    private suspend fun LoadedPlugin.resolveWith(
        item: MusicItem,
        quality: String,
        requestedPlatform: String,
        redirected: Boolean,
    ): MediaSourceResolution? {
        if (!supportsMediaSource()) return null
        val source = runCatching { getMediaSource(item, quality) }.getOrNull()
            ?.takeIf { it.url.isNotBlank() }
            ?: return null
        return MediaSourceResolution(
            item = item.copy(url = source.url),
            source = source,
            requestedPlatform = requestedPlatform,
            resolverPlatform = info.platform,
            redirected = redirected,
        )
    }

    private fun LoadedPlugin.supportsMediaSource(): Boolean =
        "getMediaSource" in info.supportedMethods
}
