package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

data class CandidateFetchResult(
    val candidates: List<RecommendationCandidate>,
    val pluginSignature: String,
    val availablePluginCount: Int,
)

data class PluginSourceContext(
    val signature: String,
    val availablePluginCount: Int,
)

class PluginCandidateFetcher internal constructor(
    private val pluginManager: PluginManager,
    private val perCallTimeoutMs: Long,
) {
    @Inject
    constructor(pluginManager: PluginManager) : this(pluginManager, DEFAULT_CALL_TIMEOUT_MS)

    suspend fun currentPluginContext(): PluginSourceContext {
        pluginManager.ensurePluginsLoaded()
        val plugins = pluginManager.getSortedEnabledPlugins().first().filter(::supportsRecommendation)
        return PluginSourceContext(
            signature = pluginSignature(plugins),
            availablePluginCount = plugins.size,
        )
    }

    suspend fun fetch(
        profile: PreferenceProfile,
        queries: List<RecommendationQuery>,
    ): CandidateFetchResult {
        val flowId = "today_recommend:${UUID.randomUUID()}"
        val startedAt = System.nanoTime()
        MfLog.detail(
            category = LogCategory.HOME,
            event = "recommend_candidate_fetch_start",
            fields = mapOf(
                "operation" to "recommend_candidate_fetch",
                "flowId" to flowId,
                "profileSignature" to profile.signature,
                "queryCount" to queries.size,
            ),
        )
        return try {
            pluginManager.ensurePluginsLoaded()
            val plugins = pluginManager.getSortedEnabledPlugins().first().filter(::supportsRecommendation)
            val semaphore = Semaphore(MAX_PLUGIN_CONCURRENCY)
            val candidates = supervisorScope {
                plugins.map { plugin ->
                    async {
                        semaphore.withPermit {
                            fetchFromPlugin(plugin, profile, queries, flowId)
                        }
                    }
                }.awaitAll().flatten()
            }
            val result = CandidateFetchResult(
                candidates = candidates,
                pluginSignature = pluginSignature(plugins),
                availablePluginCount = plugins.size,
            )
            MfLog.detail(
                category = LogCategory.HOME,
                event = "recommend_candidate_fetch_success",
                fields = mapOf(
                    "operation" to "recommend_candidate_fetch",
                    "flowId" to flowId,
                    "result" to LogFields.Result.SUCCESS,
                    "pluginCount" to plugins.size,
                    "count" to candidates.size,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            result
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.HOME,
                event = "recommend_candidate_fetch_cancelled",
                fields = mapOf(
                    "operation" to "recommend_candidate_fetch",
                    "flowId" to flowId,
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.HOME,
                event = "recommend_candidate_fetch_failed",
                throwable = error,
                fields = mapOf(
                    "operation" to "recommend_candidate_fetch",
                    "flowId" to flowId,
                    "result" to LogFields.Result.FAILURE,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            throw error
        }
    }

    private suspend fun fetchFromPlugin(
        plugin: LoadedPlugin,
        profile: PreferenceProfile,
        queries: List<RecommendationQuery>,
        flowId: String,
    ): List<RecommendationCandidate> {
        val result = mutableListOf<RecommendationCandidate>()
        val info = plugin.info
        if ("getRecommendSheetsByTag" in info.supportedMethods) {
            val matched = if ("getRecommendSheetTags" in info.supportedMethods) {
                safeCall(info.platform, "tag_list", flowId) { plugin.getRecommendSheetTags() }
                    ?.let { tags -> selectTag(tags.pinned + tags.data.flatMap { it.data }, profile) }
            } else {
                null
            }
            val payload = matched?.first?.toTagPayload() ?: mapOf("id" to "")
            safeCall(info.platform, "tag_sheets", flowId) {
                plugin.getRecommendSheetsByTag(payload, 1)
            }?.data.orEmpty().forEach { sheet ->
                result += RecommendationCandidate(
                    sheet = sheet.withFallbackPlatform(info.platform),
                    source = CandidateSource.TAG,
                    preference = matched?.second,
                )
            }
        }

        val supportsSheetSearch = "search" in info.supportedMethods &&
            ("sheet" in info.supportedSearchType || !info.supportedSearchTypeDeclared)
        if (supportsSheetSearch) {
            queries.take(MAX_SEARCH_QUERIES_PER_PLUGIN).forEachIndexed { index, query ->
                safeCall(info.platform, "sheet_search_$index", flowId) {
                    plugin.search(query.query, page = 1, type = "sheet")
                }?.data.orEmpty().mapNotNull { (it as? PluginSearchItem.Sheet)?.item }.forEach { sheet ->
                    result += RecommendationCandidate(
                        sheet = sheet.withFallbackPlatform(info.platform),
                        source = CandidateSource.SEARCH,
                        preference = query.preference,
                    )
                }
            }
        }
        return result.filter { it.sheet.id.isNotBlank() && it.sheet.platform.isNotBlank() }
    }

    private fun selectTag(
        tags: List<MusicSheetItemBase>,
        profile: PreferenceProfile,
    ): Pair<MusicSheetItemBase, WeightedPreference>? {
        val preferences = profile.genres + profile.languages + profile.artists
        preferences.forEach { preference ->
            val match = tags.firstOrNull { tag ->
                val text = listOfNotNull(tag.id, tag.title, tag.description).joinToString(" ").lowercase()
                preference.value.lowercase() in text
            }
            if (match != null) return match to preference
        }
        return null
    }

    private fun MusicSheetItemBase.toTagPayload(): Map<String, Any?> =
        raw + mapOf("id" to id, "title" to title)

    private fun supportsRecommendation(plugin: LoadedPlugin): Boolean {
        val info = plugin.info
        val supportsTag = "getRecommendSheetsByTag" in info.supportedMethods
        val supportsSheetSearch = "search" in info.supportedMethods &&
            ("sheet" in info.supportedSearchType || !info.supportedSearchTypeDeclared)
        return supportsTag || supportsSheetSearch
    }

    private fun MusicSheetItemBase.withFallbackPlatform(fallback: String): MusicSheetItemBase =
        if (platform.isBlank()) copy(platform = fallback) else this

    private suspend fun <T> safeCall(
        platform: String,
        source: String,
        flowId: String,
        block: suspend () -> T,
    ): T? {
        val startedAt = System.nanoTime()
        return try {
            withTimeout(perCallTimeoutMs) { block() }
        } catch (error: TimeoutCancellationException) {
            MfLog.error(
                category = LogCategory.HOME,
                event = "recommend_candidate_source_timeout",
                throwable = error,
                fields = sourceFailureFields(platform, source, flowId, startedAt, "timeout"),
            )
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.HOME,
                event = "recommend_candidate_source_failed",
                throwable = error,
                fields = sourceFailureFields(platform, source, flowId, startedAt, "plugin_call_failed"),
            )
            null
        }
    }

    private fun sourceFailureFields(
        platform: String,
        source: String,
        flowId: String,
        startedAt: Long,
        reason: String,
    ): Map<String, Any?> = mapOf(
        "operation" to "recommend_candidate_source",
        "flowId" to flowId,
        "platform" to platform,
        "source" to source,
        "result" to LogFields.Result.FAILURE,
        "reason" to reason,
        "durationMs" to elapsedMs(startedAt),
    )

    private fun pluginSignature(plugins: List<LoadedPlugin>): String {
        val source = plugins.joinToString("|") { plugin ->
            with(plugin.info) { "$platform:${hash.orEmpty()}:${version.orEmpty()}" }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private companion object {
        const val DEFAULT_CALL_TIMEOUT_MS = 15_000L
        const val MAX_PLUGIN_CONCURRENCY = 4
        const val MAX_SEARCH_QUERIES_PER_PLUGIN = 2
    }
}
