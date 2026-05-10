package com.zili.android.musicfreeandroid.plugin.media

import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.core.model.fallbackSequence
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
) : MediaSourceResolver {

    override suspend fun resolve(
        item: MusicItem,
        quality: String?,
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

        for (candidateQuality in qualityCandidates(quality)) {
            alternativePlugin?.resolveWith(
                item = item,
                quality = candidateQuality,
                requestedPlatform = item.platform,
                redirected = true,
            )?.let { return it }

            sourcePlugin.resolveWith(
                item = item,
                quality = candidateQuality,
                requestedPlatform = item.platform,
                redirected = false,
            )?.let { return it }
        }

        return null
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

    private suspend fun qualityCandidates(explicitQuality: String?): List<String> {
        if (!explicitQuality.isNullOrBlank()) {
            return listOf(explicitQuality)
        }

        val defaultQuality = playbackRuntimeSettings.defaultPlayQuality()
        val order = playbackRuntimeSettings.playQualityOrder()
        return defaultQuality.fallbackSequence(order).map { it.wireName() }
    }

    private fun PlayQuality.wireName(): String = name.lowercase()
}
