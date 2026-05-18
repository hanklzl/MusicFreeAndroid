package com.hank.musicfree.feature.search.runtime

import com.hank.musicfree.feature.search.SearchMediaType
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.manager.PluginManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManagerSearchSessionGateway @Inject constructor(
    private val pluginManager: PluginManager,
) : SearchSessionGateway,
    SearchPluginSignatureProvider {

    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult {
        val plugin = pluginManager.getPlugin(platform) ?: error("plugin_missing")
        return plugin.search(query, page = page, type = mediaType.key)
    }

    override fun currentSignature(): String =
        pluginManager.plugins.value
            .map { it.info }
            .sortedBy { it.platform }
            .joinToString("|") { info -> info.signaturePart() }
}

private fun PluginInfo.signaturePart(): String =
    listOf(
        platform,
        version.orEmpty(),
        hash.orEmpty(),
        supportedSearchType.sorted().joinToString(","),
        supportedMethods.sorted().joinToString(","),
    ).joinToString(":")
