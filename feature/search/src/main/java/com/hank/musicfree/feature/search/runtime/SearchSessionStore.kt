package com.hank.musicfree.feature.search.runtime

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.feature.search.PluginSearchState
import com.hank.musicfree.feature.search.SearchMediaType
import com.hank.musicfree.feature.search.SearchMediaSceneState
import com.hank.musicfree.feature.search.SearchResultsPagerUiState
import com.hank.musicfree.feature.search.SearchPageStatus
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.api.SearchResult
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class SearchSessionSnapshot(
    val query: String,
    val mediaType: String,
    val selectedPlatform: String?,
    val generation: Long,
    val platformResults: Map<String, SearchPlatformSnapshot>,
)

@Serializable
data class SearchPlatformSnapshot(
    val page: Int,
    val isEnd: Boolean,
    val itemCount: Int,
    val payloadJson: String,
)

data class SearchSessionState(
    val query: String = "",
    val selectedMediaType: SearchMediaType = SearchMediaType.MUSIC,
    val selectedPlatforms: Map<SearchMediaType, String?> = emptyMap(),
    val generation: Long = 0L,
    val searchablePlugins: Map<SearchMediaType, List<PluginInfo>> = emptyMap(),
    val results: Map<SearchMediaType, Map<String, PluginSearchState>> = emptyMap(),
    val pageStatus: SearchPageStatus = SearchPageStatus.EDITING,
    val restoreReason: String? = null,
) {
    val selectedPlatform: String? get() = selectedPlatforms[selectedMediaType]
}

val SearchSessionState.resultsPagerUiState: SearchResultsPagerUiState
    get() = SearchResultsPagerUiState(
        selectedMediaType = selectedMediaType,
        mediaScenes = SearchMediaType.entries.associateWith { mediaType ->
            SearchMediaSceneState(
                selectedPlatform = selectedPlatforms[mediaType],
                plugins = searchablePlugins[mediaType].orEmpty(),
                pluginScenes = results[mediaType].orEmpty(),
            )
        },
    )

fun interface SearchSessionClock {
    fun nowEpochMs(): Long
}

interface SearchSessionGateway {
    suspend fun search(platform: String, query: String, page: Int, mediaType: SearchMediaType): SearchResult
}

fun interface SearchPluginSignatureProvider {
    fun currentSignature(): String
}

private data class PendingSearch(val query: String, val mediaType: SearchMediaType)

private data class SearchRequest(
    val generation: Long,
    val query: String,
    val mediaType: SearchMediaType,
    val platform: String,
    val page: Int,
)

@Singleton
class SearchSessionStore internal constructor(
    private val snapshotStore: SnapshotStore,
    private val gateway: SearchSessionGateway,
    private val signatureProvider: SearchPluginSignatureProvider,
    private val json: Json,
    private val clock: SearchSessionClock,
) : RuntimeStore<SearchSessionState> {

    @Inject
    constructor(
        snapshotStore: SnapshotStore,
        gateway: SearchSessionGateway,
        signatureProvider: SearchPluginSignatureProvider,
    ) : this(
        snapshotStore = snapshotStore,
        gateway = gateway,
        signatureProvider = signatureProvider,
        json = Json { ignoreUnknownKeys = true },
        clock = SearchSessionClock { System.currentTimeMillis() },
    )

    override val storeName: String = STORE_NAME
    override val restoreOnStartup: Boolean = false
    private val _state = MutableStateFlow(SearchSessionState())
    override val state: StateFlow<SearchSessionState> = _state.asStateFlow()

    private var pluginsReady = false
    private var pendingSearch: PendingSearch? = null
    private var restoredSourceSignature: String? = null
    private val loadMoreInFlight = mutableSetOf<SearchRequest>()

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        return try {
            val now = clock.nowEpochMs()
            var snapshot: RuntimeSnapshot? = null
            for (key in snapshotStore.keys(NAMESPACE, SNAPSHOT_CAPACITY)) {
                val candidate = snapshotStore.read(NAMESPACE, key) ?: continue
                if (!candidate.isExpired(now)) {
                    snapshot = candidate
                    break
                }
            }
            if (snapshot == null) {
                logRestore(
                    event = "search_session_restore_skipped",
                    key = KEY_BATCH,
                    state = _state.value,
                    count = 0,
                    result = LogFields.Result.SKIPPED,
                    reason = "empty_search_session",
                    durationMs = elapsedMs(startedAt),
                )
                return RuntimeRestoreResult.Skipped("empty_search_session")
            }

            val payload = json.decodeFromString<SearchSessionSnapshot>(snapshot.payloadJson)
            val mediaType = SearchMediaType.entries.firstOrNull { it.key == payload.mediaType }
                ?: SearchMediaType.MUSIC
            val currentSignature = signatureProvider.currentSignature()
            if (currentSignature.isNotBlank() && snapshot.sourceSignature != currentSignature) {
                restoredSourceSignature = null
                _state.value = SearchSessionState(
                    query = payload.query,
                    selectedMediaType = mediaType,
                    selectedPlatforms = mapOf(mediaType to payload.selectedPlatform),
                    generation = payload.generation,
                    restoreReason = "plugin_signature_changed",
                )
                logRestore(
                    event = "search_session_restore_stale",
                    key = snapshot.key,
                    state = _state.value,
                    count = 0,
                    result = LogFields.Result.STALE,
                    reason = "plugin_signature_changed",
                    durationMs = elapsedMs(startedAt),
                )
                return RuntimeRestoreResult.Stale("plugin_signature_changed")
            }

            val pendingSignature = snapshot.sourceSignature.takeIf { currentSignature.isBlank() && it.isNotBlank() }
            restoredSourceSignature = pendingSignature
            val platformResults = payload.platformResults.mapValues { (_, platformSnapshot) ->
                PluginSearchState.Success(
                    items = decodeSearchItems(platformSnapshot.payloadJson),
                    isEnd = platformSnapshot.isEnd,
                    page = platformSnapshot.page,
                ) as PluginSearchState
            }
            _state.value = SearchSessionState(
                query = payload.query,
                selectedMediaType = mediaType,
                selectedPlatforms = mapOf(mediaType to payload.selectedPlatform),
                generation = payload.generation,
                results = if (platformResults.isEmpty()) emptyMap() else mapOf(mediaType to platformResults),
                pageStatus = if (platformResults.isEmpty()) SearchPageStatus.EDITING else SearchPageStatus.RESULT,
                restoreReason = if (pendingSignature != null) "plugin_signature_pending" else null,
            )
            logRestore(
                event = "search_session_restore_success",
                key = snapshot.key,
                state = _state.value,
                count = platformResults.values.sumOf { (it as PluginSearchState.Success).items.size },
                result = LogFields.Result.SUCCESS,
                reason = if (pendingSignature != null) "plugin_signature_pending" else null,
                durationMs = elapsedMs(startedAt),
            )
            RuntimeRestoreResult.Restored
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SEARCH,
                event = "search_session_restore_failed",
                throwable = error,
                fields = logFields(
                    key = KEY_BATCH,
                    operation = OPERATION_RESTORE,
                    state = _state.value,
                    platform = _state.value.selectedPlatform,
                    page = null,
                    count = 0,
                    result = LogFields.Result.FAILURE,
                    reason = "restore_failed",
                    durationMs = elapsedMs(startedAt),
                ),
            )
            RuntimeRestoreResult.Failed("restore_failed", error)
        }
    }

    override suspend fun persist() {
        val startedAt = System.nanoTime()
        val current = _state.value
        val key = snapshotKey(current)
        try {
            val snapshot = current.toSnapshot()
            val now = clock.nowEpochMs()
            snapshotStore.write(
                RuntimeSnapshot(
                    namespace = NAMESPACE,
                    key = key,
                    snapshotVersion = SNAPSHOT_VERSION,
                    sourceSignature = signatureProvider.currentSignature(),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    expiresAtEpochMs = now + SNAPSHOT_TTL_MS,
                    payloadJson = json.encodeToString(snapshot),
                ),
            )
            snapshotStore.pruneNamespace(NAMESPACE, SNAPSHOT_CAPACITY)
            MfLog.detail(
                category = LogCategory.SEARCH,
                event = "search_session_persist_success",
                fields = logFields(
                    key = key,
                    operation = OPERATION_PERSIST,
                    state = current,
                    platform = current.selectedPlatform,
                    page = null,
                    count = snapshot.platformResults.values.sumOf { it.itemCount },
                    result = LogFields.Result.SUCCESS,
                    reason = null,
                    durationMs = elapsedMs(startedAt),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SEARCH,
                event = "search_session_persist_failed",
                throwable = error,
                fields = logFields(
                    key = key,
                    operation = OPERATION_PERSIST,
                    state = current,
                    platform = current.selectedPlatform,
                    page = null,
                    count = 0,
                    result = LogFields.Result.FAILURE,
                    reason = "persist_failed",
                    durationMs = elapsedMs(startedAt),
                ),
            )
        }
    }

    override suspend fun prune(nowEpochMs: Long) {
        snapshotStore.deleteExpired(NAMESPACE, nowEpochMs)
        snapshotStore.pruneNamespace(NAMESPACE, SNAPSHOT_CAPACITY)
    }

    suspend fun setPluginsReady() {
        pluginsReady = true
        validateRestoredSignature()
        updatePageStatusForPluginAvailability()
        runPendingSearchIfPossible()
    }

    suspend fun setSearchablePlugins(mediaType: SearchMediaType, plugins: List<PluginInfo>) {
        _state.update { current ->
            val oldSelected = current.selectedPlatforms[mediaType]
            val nextSelected = when {
                oldSelected == null && plugins.isNotEmpty() -> plugins.first().platform
                oldSelected != null && plugins.none { it.platform == oldSelected } -> plugins.firstOrNull()?.platform
                oldSelected == null && plugins.isEmpty() -> null
                else -> oldSelected
            }
            current.copy(
                searchablePlugins = current.searchablePlugins.toMutableMap().apply {
                    put(mediaType, plugins)
                },
                selectedPlatforms = current.selectedPlatforms.toMutableMap().apply {
                    put(mediaType, nextSelected)
                },
            )
        }
        updatePageStatusForPluginAvailability()
        runPendingSearchIfPossible()
    }

    suspend fun search(query: String) {
        if (query.isBlank()) return
        restoredSourceSignature = null
        val mediaType = _state.value.selectedMediaType
        val generation = _state.value.generation + 1
        _state.update {
            it.copy(
                query = query,
                generation = generation,
                results = emptyMap(),
                pageStatus = SearchPageStatus.SEARCHING,
                restoreReason = null,
            )
        }
        ensureMediaSearched(mediaType)
    }

    suspend fun ensureMediaSearched(mediaType: SearchMediaType) {
        val current = _state.value
        if (current.query.isBlank()) return
        if (current.results[mediaType]?.isNotEmpty() == true) {
            syncSelectedPageStatus(mediaType)
            return
        }

        val plugins = searchablePluginsFor(mediaType)
        if (plugins.isNullOrEmpty()) {
            if (current.selectedMediaType == mediaType && current.pageStatus != SearchPageStatus.SEARCHING) {
                _state.update { it.copy(pageStatus = SearchPageStatus.SEARCHING) }
            }
            pendingSearch = PendingSearch(current.query, mediaType)
            return
        }

        pendingSearch = null
        searchForMediaType(current.query, mediaType, current.generation)
    }

    fun selectMediaType(type: SearchMediaType) {
        val current = _state.value
        if (current.selectedMediaType == type) return
        val needSearch = current.query.isNotBlank() && current.results[type] == null
        if (needSearch) {
            pendingSearch = PendingSearch(current.query, type)
        } else {
            pendingSearch = null
        }
        _state.update {
            val next = it.copy(
                selectedMediaType = type,
            )
            next.copy(pageStatus = pageStatusForSelectedMedia(next))
        }

        updatePageStatusForPluginAvailability()
    }

    fun selectPlatform(platform: String) {
        val mediaType = _state.value.selectedMediaType
        selectPlatform(mediaType, platform)
    }

    fun selectPlatform(mediaType: SearchMediaType, platform: String) {
        _state.update {
            it.copy(
                selectedPlatforms = it.selectedPlatforms.toMutableMap().apply {
                    put(mediaType, platform)
                },
            )
        }
    }

    suspend fun loadMore() {
        val currentState = _state.value
        val mediaType = currentState.selectedMediaType
        val platform = currentState.selectedPlatform ?: return
        loadMore(mediaType, platform)
    }

    suspend fun loadMore(mediaType: SearchMediaType, platform: String) {
        val currentState = _state.value
        val current = currentState.results[mediaType]?.get(platform)
        if (current !is PluginSearchState.Success || current.isEnd) return

        val request = SearchRequest(
            generation = currentState.generation,
            query = currentState.query,
            mediaType = mediaType,
            platform = platform,
            page = current.page + 1,
        )
        if (!loadMoreInFlight.add(request)) return

        val startedAt = System.nanoTime()
        try {
            val (result, durationMs) = timedSuspend {
                gateway.search(platform, request.query, request.page, mediaType)
            }
            val latestState = _state.value
            val latest = latestState.results[mediaType]?.get(platform)
            if (
                latestState.generation != request.generation ||
                latestState.query != request.query ||
                latest !is PluginSearchState.Success ||
                latest.page != current.page
            ) {
                logStaleResult(request, elapsedMs(startedAt))
                return
            }
            updatePluginState(
                mediaType,
                platform,
                latest.copy(
                    items = latest.items + result.data,
                    isEnd = result.isEnd,
                    page = request.page,
                ),
            )
            MfLog.detail(
                category = LogCategory.SEARCH,
                event = "search_session_page_success",
                fields = logFields(
                    key = snapshotKey(_state.value),
                    operation = "search_session_load_more",
                    state = _state.value,
                    platform = platform,
                    page = request.page,
                    count = result.data.size,
                    result = LogFields.Result.SUCCESS,
                    reason = null,
                    durationMs = durationMs,
                ),
            )
            persist()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SEARCH,
                event = "search_session_page_failed",
                throwable = error,
                fields = logFields(
                    key = snapshotKey(_state.value),
                    operation = "search_session_load_more",
                    state = _state.value,
                    platform = platform,
                    page = request.page,
                    count = 0,
                    result = LogFields.Result.FAILURE,
                    reason = "search_failed",
                    durationMs = elapsedMs(startedAt),
                ),
            )
        } finally {
            loadMoreInFlight.remove(request)
        }
    }

    fun backToEditing() {
        _state.update { it.copy(pageStatus = SearchPageStatus.EDITING) }
    }

    private suspend fun searchForMediaType(query: String, mediaType: SearchMediaType, generation: Long) {
        val plugins = searchablePluginsFor(mediaType)
        if (plugins.isNullOrEmpty()) {
            pendingSearch = PendingSearch(query, mediaType)
            updatePageStatusForPluginAvailability()
            return
        }

        pendingSearch = null
        _state.update { current ->
            current.copy(
                results = current.results.toMutableMap().apply {
                    put(mediaType, plugins.associate { it.platform to (PluginSearchState.Loading as PluginSearchState) })
                },
                pageStatus = SearchPageStatus.SEARCHING,
            )
        }
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = "search_start",
            fields = mapOf(
                "query" to query,
                "type" to mediaType.key,
                "mediaType" to mediaType.key,
                "platformCount" to plugins.size,
                "status" to "start",
                "generation" to generation,
            ),
        )

        coroutineScope {
            plugins.map { plugin ->
                async { searchPlugin(query, mediaType, generation, plugin) }
            }.awaitAll()
        }
        checkSearchCompletion(mediaType)
        if (_state.value.results[mediaType]?.values?.any { it is PluginSearchState.Success } == true) {
            persist()
        }
    }

    private suspend fun searchPlugin(
        query: String,
        mediaType: SearchMediaType,
        generation: Long,
        pluginInfo: PluginInfo,
    ) {
        val request = SearchRequest(generation, query, mediaType, pluginInfo.platform, 1)
        val startedAt = System.nanoTime()
        try {
            val (result, durationMs) = timedSuspend {
                gateway.search(pluginInfo.platform, request.query, request.page, mediaType)
            }
            if (isStaleSearchRequest(request)) {
                logStaleResult(request, elapsedMs(startedAt))
                return
            }
            MfLog.detail(
                category = LogCategory.SEARCH,
                event = "search_plugin_success",
                fields = mapOf(
                    "platform" to pluginInfo.platform,
                    "type" to mediaType.key,
                    "mediaType" to mediaType.key,
                    "query" to query,
                    "page" to 1,
                    "resultCount" to result.data.size,
                    "count" to result.data.size,
                    "isEnd" to result.isEnd,
                    "status" to "success",
                    "result" to LogFields.Result.SUCCESS,
                    "generation" to generation,
                    "durationMs" to durationMs,
                ),
            )
            updatePluginState(
                mediaType,
                pluginInfo.platform,
                PluginSearchState.Success(items = result.data, isEnd = result.isEnd, page = 1),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isStaleSearchRequest(request)) {
                logStaleResult(request, elapsedMs(startedAt))
                return
            }
            MfLog.error(
                category = LogCategory.SEARCH,
                event = "search_plugin_failed",
                throwable = error,
                fields = mapOf(
                    "platform" to pluginInfo.platform,
                    "type" to mediaType.key,
                    "mediaType" to mediaType.key,
                    "query" to query,
                    "page" to 1,
                    "status" to "failed",
                    "result" to LogFields.Result.FAILURE,
                    "generation" to generation,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            updatePluginState(mediaType, pluginInfo.platform, PluginSearchState.Error(error.message ?: "搜索失败"))
        }
    }

    private suspend fun runPendingSearchIfPossible() {
        val pending = pendingSearch ?: return
        val current = _state.value
        if (pending.mediaType != current.selectedMediaType) return
        if (searchablePluginsFor(pending.mediaType).isNullOrEmpty()) return

        pendingSearch = null
        val generation = current.generation + 1
        _state.update { it.copy(generation = generation, pageStatus = SearchPageStatus.SEARCHING) }
        searchForMediaType(pending.query, pending.mediaType, generation)
    }

    private fun searchablePluginsFor(mediaType: SearchMediaType): List<PluginInfo>? =
        _state.value.searchablePlugins[mediaType]?.takeIf { it.isNotEmpty() }

    private fun updatePageStatusForPluginAvailability() {
        val current = _state.value
        val searchable = current.searchablePlugins[current.selectedMediaType].orEmpty()
        if (current.pageStatus == SearchPageStatus.SEARCHING) return
        if (searchable.isNotEmpty()) {
            if (current.pageStatus == SearchPageStatus.NO_PLUGIN) {
                _state.update { it.copy(pageStatus = SearchPageStatus.EDITING) }
            }
            return
        }
        if (!pluginsReady) return
        if (current.pageStatus != SearchPageStatus.RESULT) {
            _state.update { it.copy(pageStatus = SearchPageStatus.NO_PLUGIN) }
        }
    }

    private fun syncSelectedPageStatus(mediaType: SearchMediaType) {
        _state.update { current ->
            if (current.selectedMediaType != mediaType) return@update current
            current.copy(pageStatus = pageStatusForSelectedMedia(current))
        }
    }

    private fun pageStatusForSelectedMedia(state: SearchSessionState): SearchPageStatus {
        val selectedResults = state.results[state.selectedMediaType]
        if (selectedResults != null) {
            return if (selectedResults.values.any { it is PluginSearchState.Loading }) {
                SearchPageStatus.SEARCHING
            } else {
                SearchPageStatus.RESULT
            }
        }

        val searchable = state.searchablePlugins[state.selectedMediaType].orEmpty()
        return when {
            state.query.isNotBlank() -> SearchPageStatus.SEARCHING
            searchable.isEmpty() && pluginsReady -> SearchPageStatus.NO_PLUGIN
            else -> SearchPageStatus.EDITING
        }
    }

    private fun updatePluginState(mediaType: SearchMediaType, platform: String, searchState: PluginSearchState) {
        _state.update { current ->
            val typeMap = (current.results[mediaType] ?: emptyMap()).toMutableMap()
            typeMap[platform] = searchState
            current.copy(results = current.results.toMutableMap().apply { put(mediaType, typeMap) })
        }
    }

    private fun isStaleSearchRequest(request: SearchRequest): Boolean {
        val latest = _state.value
        return latest.generation != request.generation || latest.query != request.query
    }

    private fun checkSearchCompletion(mediaType: SearchMediaType) {
        val current = _state.value
        if (mediaType != current.selectedMediaType) return
        val typeResults = current.results[mediaType] ?: return
        if (typeResults.values.none { it is PluginSearchState.Loading }) {
            _state.update { it.copy(pageStatus = pageStatusForSelectedMedia(it)) }
        }
    }

    private fun validateRestoredSignature() {
        val restoredSignature = restoredSourceSignature ?: return
        restoredSourceSignature = null
        val currentSignature = signatureProvider.currentSignature()
        if (restoredSignature == currentSignature) {
            if (_state.value.restoreReason == "plugin_signature_pending") {
                _state.update { it.copy(restoreReason = null) }
            }
            return
        }

        val previous = _state.value
        val next = previous.copy(
            results = emptyMap(),
            pageStatus = SearchPageStatus.EDITING,
            restoreReason = "plugin_signature_changed",
        )
        _state.value = next
        logRestore(
            event = "search_session_restore_stale",
            key = snapshotKey(previous),
            state = next,
            count = 0,
            result = LogFields.Result.STALE,
            reason = "plugin_signature_changed",
            durationMs = 0,
        )
    }

    private fun logStaleResult(request: SearchRequest, durationMs: Long) {
        val requestState = _state.value.copy(
            query = request.query,
            selectedMediaType = request.mediaType,
            selectedPlatforms = _state.value.selectedPlatforms.toMutableMap().apply {
                put(request.mediaType, request.platform)
            },
            generation = request.generation,
        )
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = "search_session_result_stale",
            fields = logFields(
                key = snapshotKey(requestState),
                operation = "search_session_result_drop",
                state = requestState,
                platform = request.platform,
                page = request.page,
                count = 0,
                result = LogFields.Result.STALE,
                reason = LogFields.Reason.STALE_GENERATION,
                durationMs = durationMs,
            ),
        )
    }

    private fun logRestore(
        event: String,
        key: String,
        state: SearchSessionState,
        count: Int,
        result: String,
        reason: String?,
        durationMs: Long,
    ) {
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = event,
            fields = logFields(
                key = key,
                operation = OPERATION_RESTORE,
                state = state,
                platform = state.selectedPlatform,
                page = null,
                count = count,
                result = result,
                reason = reason,
                durationMs = durationMs,
            ),
        )
    }

    private fun logFields(
        key: String,
        operation: String,
        state: SearchSessionState,
        platform: String?,
        page: Int?,
        count: Int,
        result: String,
        reason: String?,
        durationMs: Long,
    ): Map<String, Any?> = buildMap {
        put("store", STORE_NAME)
        put("operation", operation)
        put("key", key)
        put("query", state.query)
        put("mediaType", state.selectedMediaType.key)
        put("platform", platform.orEmpty())
        put("generation", state.generation)
        if (page != null) put("page", page)
        put("count", count)
        put("durationMs", durationMs)
        put("result", result)
        reason?.let { put("reason", it) }
    }

    private fun SearchSessionState.toSnapshot(): SearchSessionSnapshot {
        val typeResults = results[selectedMediaType].orEmpty()
        return SearchSessionSnapshot(
            query = query,
            mediaType = selectedMediaType.key,
            selectedPlatform = selectedPlatform,
            generation = generation,
            platformResults = typeResults.mapNotNull { (platform, state) ->
                val success = state as? PluginSearchState.Success ?: return@mapNotNull null
                platform to SearchPlatformSnapshot(
                    page = success.page,
                    isEnd = success.isEnd,
                    itemCount = success.items.size,
                    payloadJson = encodeSearchItems(success.items),
                )
            }.toMap(),
        )
    }

    private fun snapshotKey(state: SearchSessionState): String =
        RuntimeStoreKey.search(
            mediaType = state.selectedMediaType.key,
            platform = state.selectedPlatform ?: "all",
            queryHash = sha256(state.query),
        ).value

    private fun encodeSearchItems(items: List<PluginSearchItem>): String =
        buildJsonArray {
            items.forEach { add(it.toJsonObject()) }
        }.toString()

    private fun decodeSearchItems(payloadJson: String): List<PluginSearchItem> =
        json.parseToJsonElement(payloadJson).jsonArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            when (obj.string("type")) {
                "music" -> PluginSearchItem.Music(obj.obj("item").toMusicItem())
                "album" -> PluginSearchItem.Album(obj.obj("item").toAlbumItemBase())
                "artist" -> PluginSearchItem.Artist(obj.obj("item").toArtistItemBase())
                "sheet" -> PluginSearchItem.Sheet(obj.obj("item").toMusicSheetItemBase())
                else -> null
            }
        }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val STORE_NAME = "search_session"
        private const val NAMESPACE = "search_session"
        private const val KEY_BATCH = "search_session:batch"
        private const val SNAPSHOT_VERSION = 1
        private const val SNAPSHOT_TTL_MS = 24 * 60 * 60 * 1000L
        private const val SNAPSHOT_CAPACITY = 10
        private const val OPERATION_RESTORE = "search_session_restore"
        private const val OPERATION_PERSIST = "search_session_persist"
    }
}

private fun PluginSearchItem.toJsonObject(): JsonObject = when (this) {
    is PluginSearchItem.Music -> typedItem("music", item.toJsonObject())
    is PluginSearchItem.Album -> typedItem("album", item.toJsonObject())
    is PluginSearchItem.Artist -> typedItem("artist", item.toJsonObject())
    is PluginSearchItem.Sheet -> typedItem("sheet", item.toJsonObject())
}

private fun typedItem(type: String, item: JsonObject): JsonObject = buildJsonObject {
    put("type", JsonPrimitive(type))
    put("item", item)
}

private fun MusicItem.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("artist", artist.json())
    put("album", album.json())
    put("duration", duration.json())
    put("url", url.json())
    put("artwork", artwork.json())
    put("raw", raw.toJsonElement())
    put("addedAt", addedAt.json())
    put("localPath", localPath.json())
}

private fun AlbumItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("date", date.json())
    put("artist", artist.json())
    put("description", description.json())
    put("artwork", artwork.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun ArtistItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("name", name.json())
    put("avatar", avatar.json())
    put("fans", fans.json())
    put("description", description.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun MusicSheetItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("artist", artist.json())
    put("description", description.json())
    put("coverImg", coverImg.json())
    put("artwork", artwork.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun JsonObject.toMusicItem(): MusicItem = MusicItem(
    id = requiredString("id"),
    platform = requiredString("platform"),
    title = requiredString("title"),
    artist = requiredString("artist"),
    album = stringOrNull("album"),
    duration = longOrNull("duration") ?: 0L,
    url = stringOrNull("url"),
    artwork = stringOrNull("artwork"),
    qualities = null,
    raw = rawMap(),
    addedAt = longOrNull("addedAt") ?: 0L,
    localPath = stringOrNull("localPath"),
)

private fun JsonObject.toAlbumItemBase(): AlbumItemBase = AlbumItemBase(
    id = requiredString("id"),
    platform = requiredString("platform"),
    title = stringOrNull("title"),
    date = stringOrNull("date"),
    artist = stringOrNull("artist"),
    description = stringOrNull("description"),
    artwork = stringOrNull("artwork"),
    worksNum = intOrNull("worksNum"),
    raw = rawMap(),
)

private fun JsonObject.toArtistItemBase(): ArtistItemBase = ArtistItemBase(
    id = requiredString("id"),
    platform = requiredString("platform"),
    name = stringOrNull("name"),
    avatar = stringOrNull("avatar"),
    fans = intOrNull("fans"),
    description = stringOrNull("description"),
    worksNum = intOrNull("worksNum"),
    raw = rawMap(),
)

private fun JsonObject.toMusicSheetItemBase(): MusicSheetItemBase = MusicSheetItemBase(
    id = requiredString("id"),
    platform = requiredString("platform"),
    title = stringOrNull("title"),
    artist = stringOrNull("artist"),
    description = stringOrNull("description"),
    coverImg = stringOrNull("coverImg"),
    artwork = stringOrNull("artwork"),
    worksNum = intOrNull("worksNum"),
    raw = rawMap(),
)

private fun JsonObject.obj(name: String): JsonObject =
    requireNotNull(this[name] as? JsonObject) { "Missing object field: $name" }

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.stringOrNull(name: String): String? =
    this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(stringOrNull(name)) { "Missing string field: $name" }

private fun JsonObject.intOrNull(name: String): Int? =
    this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.longOrNull(name: String): Long? =
    this[name]?.jsonPrimitive?.longOrNull

private fun JsonObject.rawMap(): Map<String, Any?> =
    (this["raw"] as? JsonObject)?.toAnyMap().orEmpty()

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        this@toJsonElement.forEach { (key, value) ->
            put(key.toString(), value.toJsonElement())
        }
    }
    is Iterable<*> -> buildJsonArray {
        this@toJsonElement.forEach { add(it.toJsonElement()) }
    }
    is Array<*> -> buildJsonArray {
        this@toJsonElement.forEach { add(it.toJsonElement()) }
    }
    else -> JsonPrimitive(toString())
}

private fun String?.json(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Number?.json(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

private fun JsonObject.toAnyMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toAnyValue() }

private fun JsonArray.toAnyList(): List<Any?> =
    map { it.toAnyValue() }

private fun JsonElement.toAnyValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> toAnyMap()
    is JsonArray -> toAnyList()
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> contentOrNull
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}
