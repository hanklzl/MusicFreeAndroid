package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin

data class PluginCapabilityUiModel(
    val platform: String,
    val label: String,
)

fun List<LoadedPlugin>.pluginsSupporting(method: String): List<PluginCapabilityUiModel> =
    mapNotNull { plugin ->
        val platform = plugin.info.platform.trim()
        if (platform.isBlank()) {
            null
        } else if (method in plugin.info.supportedMethods) {
            PluginCapabilityUiModel(platform = platform, label = platform)
        } else {
            null
        }
    }
