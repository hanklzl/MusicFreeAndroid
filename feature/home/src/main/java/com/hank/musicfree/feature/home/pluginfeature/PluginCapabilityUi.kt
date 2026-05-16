package com.hank.musicfree.feature.home.pluginfeature

import com.hank.musicfree.plugin.manager.LoadedPlugin

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
