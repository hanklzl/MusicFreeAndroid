package com.hank.musicfree.feature.home.recommendsheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendSheetsViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    companion object {
        private const val DEFAULT_TAG_ID = "__default__"
        private const val EMPTY_PLUGIN_MESSAGE = "当前没有支持推荐歌单的插件"
    }

    private val capableLoadedPlugins = pluginManager.getSortedEnabledPlugins()
        .map { plugins ->
            plugins.filter { it.info.supportedMethods.contains("getRecommendSheetsByTag") && it.info.platform.isNotBlank() }
                .map { plugin -> plugin.info.platform.trim() to plugin }
                .toMap(LinkedHashMap())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val availablePlugins: StateFlow<List<PluginCapabilityUiModel>> = capableLoadedPlugins
        .map { plugins ->
            plugins.values.map { plugin ->
                PluginCapabilityUiModel(
                    platform = plugin.info.platform.trim(),
                    label = plugin.info.platform.trim(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pagerUiState = MutableStateFlow(RecommendSheetsPagerUiState())
    val pagerUiState: StateFlow<RecommendSheetsPagerUiState> = _pagerUiState.asStateFlow()

    val selectedPlugin: StateFlow<String?> = pagerUiState
        .map { state -> state.selectedPlatform }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<RecommendSheetsUiState> = pagerUiState
        .map { state -> state.selectedScene() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RecommendSheetsSceneState(
                loading = false,
                isEnd = true,
                emptyMessage = EMPTY_PLUGIN_MESSAGE,
            ),
        )

    private val loadGenerationByPlatform = mutableMapOf<String, Long>()
    private val pluginInstanceByPlatform = mutableMapOf<String, LoadedPlugin>()

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            capableLoadedPlugins.collect { plugins ->
                val platforms = plugins.keys
                loadGenerationByPlatform.keys.removeIf { it !in platforms }
                pluginInstanceByPlatform.keys.removeIf { it !in platforms }

                if (plugins.isEmpty()) {
                    _pagerUiState.value = RecommendSheetsPagerUiState()
                    return@collect
                }

                _pagerUiState.update { state ->
                    val nextScenes = plugins.mapValues { (platform, pluginInstance) ->
                        val previousInstance = pluginInstanceByPlatform[platform]
                        if (previousInstance !== pluginInstance) {
                            pluginInstanceByPlatform[platform] = pluginInstance
                            if (previousInstance != null) {
                                nextLoadGeneration(platform)
                            }
                            RecommendSheetsSceneState()
                        } else {
                            state.scenes[platform] ?: RecommendSheetsSceneState()
                        }
                    }
                    val selectedPlatform = state.selectedPlatform
                        ?.takeIf { it in platforms }
                        ?: plugins.keys.first()
                    state.copy(
                        plugins = plugins.values.map { plugin ->
                            PluginCapabilityUiModel(
                                platform = plugin.info.platform.trim(),
                                label = plugin.info.platform.trim(),
                            )
                        },
                        scenes = nextScenes,
                        selectedPlatform = selectedPlatform,
                    )
                }

                _pagerUiState.value.selectedPlatform?.let { ensureSceneLoaded(it) }
            }
        }
    }

    fun selectPlugin(platform: String) {
        val target = platform.trim()
        if (!_pagerUiState.value.plugins.any { it.platform == target }) {
            return
        }

        _pagerUiState.update { state ->
            if (state.selectedPlatform == target) state else state.copy(selectedPlatform = target)
        }
        ensureSceneLoaded(target)
    }

    fun ensureSceneLoaded(platform: String) {
        val scene = pagerUiState.value.scenes[platform] ?: return
        if (scene.loaded || scene.firstPageInFlight) {
            return
        }
        if (!hasPlugin(platform)) {
            return
        }

        val generation = nextLoadGeneration(platform)
        updateScene(platform) { existing ->
            existing.copy(
                loading = true,
                firstPageInFlight = true,
                loadingMore = false,
                loadingMorePage = null,
                errorMessage = null,
                emptyMessage = null,
            )
        }

        viewModelScope.launch {
            val plugin = pluginInstanceByPlatform[platform] ?: return@launch
            if (!isCurrentLoad(platform, generation, plugin)) {
                return@launch
            }
            loadTagsAndFirstPage(platform = platform, generation = generation, plugin = plugin)
        }
    }

    fun selectTag(tagId: String) {
        val platform = selectedPlugin.value ?: return
        selectTag(platform = platform, tagId = tagId)
    }

    fun selectTag(platform: String, tagId: String) {
        val scene = pagerUiState.value.scenes[platform] ?: return
        val tag = scene.tag(tagId) ?: return
        if (scene.selectedTagId == tag.id && scene.loaded && scene.sheets.isNotEmpty()) {
            return
        }

        val generation = nextLoadGeneration(platform)
        updateScene(platform) { existing ->
            existing.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                page = 0,
                loading = true,
                loadingMore = false,
                loadingMorePage = null,
                isEnd = false,
                loaded = false,
                firstPageInFlight = true,
                errorMessage = null,
                emptyMessage = null,
            )
        }
        viewModelScope.launch {
            val plugin = pluginInstanceByPlatform[platform] ?: return@launch
            if (!isCurrentLoad(platform, generation, plugin)) {
                return@launch
            }
            loadSheets(platform = platform, tag = tag, reset = true, generation = generation, plugin = plugin)
        }
    }

    fun refresh() {
        val platform = selectedPlugin.value ?: return
        refresh(platform)
    }

    fun refresh(platform: String) {
        val scene = pagerUiState.value.scenes[platform] ?: return
        val tag = scene.currentTag() ?: defaultTag()
        val generation = nextLoadGeneration(platform)
        updateScene(platform) { existing ->
            existing.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                page = 0,
                loading = true,
                loadingMore = false,
                loadingMorePage = null,
                isEnd = false,
                loaded = false,
                firstPageInFlight = true,
                errorMessage = null,
                emptyMessage = null,
            )
        }
        viewModelScope.launch {
            val plugin = pluginInstanceByPlatform[platform] ?: return@launch
            if (!isCurrentLoad(platform, generation, plugin)) {
                return@launch
            }
            loadSheets(platform = platform, tag = tag, reset = true, generation = generation, plugin = plugin)
        }
    }

    fun loadMore() {
        val platform = selectedPlugin.value ?: return
        loadMore(platform)
    }

    fun loadMore(platform: String) {
        val scene = pagerUiState.value.scenes[platform] ?: return
        if (scene.loading || scene.loadingMore || scene.isEnd || scene.firstPageInFlight || !scene.loaded) {
            return
        }
        val tag = scene.currentTag() ?: defaultTag()

        val generation = nextLoadGeneration(platform)
        updateScene(platform) { existing ->
            existing.copy(
                loadingMore = true,
                loadingMorePage = existing.page + 1,
                loading = false,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val plugin = pluginInstanceByPlatform[platform] ?: return@launch
            if (!isCurrentLoad(platform, generation, plugin)) {
                return@launch
            }
            loadSheets(platform = platform, tag = tag, reset = false, generation = generation, plugin = plugin)
        }
    }

    private suspend fun loadTagsAndFirstPage(platform: String, generation: Long, plugin: LoadedPlugin) {
        val operation = "load_tags"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        if (!isCurrentLoad(platform, generation, plugin)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }

        logStart(
            event = "recommend_sheets_tags_start",
            operation = operation,
            flowId = flowId,
            generation = generation,
            platform = platform,
        )
        val tagsResult = runCatching {
            timedSuspend { plugin.getRecommendSheetTags() }
        }.onSuccess { (result, durationMs) ->
            MfLog.detail(
                LogCategory.HOME,
                "recommend_sheets_tags_success",
                baseFields(operation, flowId, generation, platform) + mapOf(
                    "count" to ((result?.pinned?.size ?: 0) + result?.data.orEmpty().sumOf { it.data.size }),
                    "durationMs" to durationMs,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        }.onFailure { error ->
            if (error is CancellationException) {
                logCancelled(
                    event = "recommend_sheets_tags_cancelled",
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    platform = platform,
                    startedAt = startedAt,
                )
                throw error
            }
            if (!isCurrentLoad(platform, generation, plugin)) {
                logStale(operation, flowId, generation, platform, startedAt)
                return@onFailure
            }
            logFailure(
                event = "recommend_sheets_tags_failed",
                throwable = error,
                operation = operation,
                flowId = flowId,
                generation = generation,
                platform = platform,
                startedAt = startedAt,
                fields = mapOf("reason" to LogFields.Reason.UNKNOWN),
            )
            updateScene(platform) { existing ->
                existing.copy(
                    loading = false,
                    firstPageInFlight = false,
                    loadingMore = false,
                    loadingMorePage = null,
                    isEnd = true,
                    loaded = false,
                    errorMessage = "加载推荐歌单失败",
                )
            }
        }.getOrNull()?.first

        if (!isCurrentLoad(platform, generation, plugin)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }

        val tags = buildTags(tagsResult?.pinned.orEmpty(), tagsResult?.data.orEmpty())
        val selected = tags.firstOrNull() ?: defaultTag()
        updateScene(platform) { state ->
            state.copy(
                tags = tags,
                selectedTagId = selected.id,
                loading = true,
                loadingMore = false,
                loadingMorePage = null,
                isEnd = false,
                firstPageInFlight = true,
                loaded = false,
                errorMessage = null,
                emptyMessage = null,
            )
        }
        loadSheets(platform = platform, tag = selected, reset = true, generation = generation, plugin = plugin)
    }

    private suspend fun loadSheets(
        platform: String,
        tag: RecommendTag,
        reset: Boolean,
        generation: Long,
        plugin: LoadedPlugin,
    ) {
        val operation = if (reset) "load_initial" else "load_more"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        if (!isCurrentLoad(platform, generation, plugin)) {
            logStale(
                operation,
                flowId,
                generation,
                platform,
                startedAt,
                mapOf("page" to nextPage(platform, reset)),
            )
            return
        }

        val nextPage = nextPage(platform, reset)
        updateScene(platform) { existing ->
            existing.copy(
                loading = reset,
                loadingMore = !reset,
                loadingMorePage = if (reset) null else nextPage,
                firstPageInFlight = true,
                errorMessage = null,
            )
        }

        logStart(
            event = "recommend_sheets_load_start",
            operation = operation,
            flowId = flowId,
            generation = generation,
            platform = platform,
            fields = mapOf("page" to nextPage, "itemId" to tag.id),
        )
        val result = runCatching {
            timedSuspend { plugin.getRecommendSheetsByTag(tag.payload, nextPage) }
        }

        if (!isCurrentLoad(platform, generation, plugin)) {
            logStale(operation, flowId, generation, platform, startedAt, mapOf("page" to nextPage))
            return
        }

        result.onSuccess { (sheetsResult, durationMs) ->
            if (sheetsResult == null) {
                logFailure(
                    event = "recommend_sheets_load_failed",
                    throwable = null,
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    platform = platform,
                    startedAt = startedAt,
                    fields = mapOf(
                        "page" to nextPage,
                        "itemId" to tag.id,
                        "reason" to LogFields.Reason.UNKNOWN,
                    ),
                )
                updateScene(platform) { existing ->
                    existing.copy(
                        loading = false,
                        loadingMore = false,
                        loadingMorePage = null,
                        firstPageInFlight = false,
                        loaded = if (reset) false else existing.loaded,
                        errorMessage = "加载推荐歌单失败",
                    )
                }
                return@onSuccess
            }

            val incoming = sheetsResult.data.map { item ->
                if (item.platform.isBlank()) item.copy(platform = platform) else item
            }
            val merged = if (reset) {
                incoming
            } else {
                (pagerUiState.value.scenes[platform]?.sheets ?: emptyList()) + incoming
            }

            MfLog.detail(
                LogCategory.HOME,
                "recommend_sheets_load_success",
                baseFields(operation, flowId, generation, platform) + mapOf(
                    "page" to nextPage,
                    "itemId" to tag.id,
                    "count" to incoming.size,
                    "isEnd" to sheetsResult.isEnd,
                    "durationMs" to durationMs,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            updateScene(platform) { existing ->
                existing.copy(
                    sheets = merged,
                    page = nextPage,
                    loading = false,
                    loadingMore = false,
                    loadingMorePage = null,
                    firstPageInFlight = false,
                    loaded = true,
                    isEnd = sheetsResult.isEnd,
                    errorMessage = null,
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) {
                logCancelled(
                    event = "recommend_sheets_load_cancelled",
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    platform = platform,
                    startedAt = startedAt,
                    fields = mapOf("page" to nextPage, "itemId" to tag.id),
                )
                throw e
            }
            if (!isCurrentLoad(platform, generation, plugin)) {
                logStale(operation, flowId, generation, platform, startedAt, mapOf("page" to nextPage))
                return@onFailure
            }
            logFailure(
                event = "recommend_sheets_load_failed",
                throwable = e,
                operation = operation,
                flowId = flowId,
                generation = generation,
                platform = platform,
                startedAt = startedAt,
                fields = mapOf("page" to nextPage, "itemId" to tag.id),
            )
            updateScene(platform) { existing ->
                existing.copy(
                    loading = false,
                    loadingMore = false,
                    loadingMorePage = null,
                    firstPageInFlight = false,
                    loaded = existing.loaded,
                    errorMessage = e.message ?: "加载推荐歌单失败",
                )
            }
        }
    }

    private fun nextLoadGeneration(platform: String): Long {
        val next = (loadGenerationByPlatform[platform] ?: 0L) + 1
        loadGenerationByPlatform[platform] = next
        return next
    }

    private fun nextLoadGeneration(): Long {
        val platform = pagerUiState.value.selectedPlatform ?: return 0
        return nextLoadGeneration(platform)
    }

    private fun nextPage(platform: String, reset: Boolean): Int {
        val currentPage = pagerUiState.value.scenes[platform]?.page ?: 0
        return if (reset) 1 else currentPage + 1
    }

    private fun isCurrentLoad(platform: String, generation: Long, plugin: LoadedPlugin): Boolean =
        hasPlugin(platform) &&
            pluginInstanceByPlatform[platform] === plugin &&
            loadGenerationByPlatform[platform] == generation

    private fun hasPlugin(platform: String): Boolean =
        pagerUiState.value.plugins.any { it.platform == platform }

    private fun updateScene(platform: String, transform: (RecommendSheetsSceneState) -> RecommendSheetsSceneState) {
        _pagerUiState.update { state ->
            val currentScene = state.scenes[platform] ?: RecommendSheetsSceneState()
            state.copy(scenes = state.scenes + (platform to transform(currentScene)))
        }
    }

    private fun logStart(
        event: String,
        operation: String,
        flowId: String,
        generation: Long,
        platform: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            event,
            baseFields(operation, flowId, generation, platform) + fields,
        )
    }

    private fun logFailure(
        event: String,
        throwable: Throwable?,
        operation: String,
        flowId: String,
        generation: Long,
        platform: String,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.error(
            LogCategory.HOME,
            event,
            throwable,
            baseFields(operation, flowId, generation, platform) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.FAILURE,
            ),
        )
    }

    private fun logStale(
        operation: String,
        flowId: String,
        generation: Long,
        platform: String,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "recommend_sheets_load_stale",
            baseFields(operation, flowId, generation, platform) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.STALE,
                "reason" to LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun logCancelled(
        event: String,
        operation: String,
        flowId: String,
        generation: Long,
        platform: String,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            event,
            baseFields(operation, flowId, generation, platform) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
            ),
        )
    }

    private fun baseFields(
        operation: String,
        flowId: String,
        generation: Long,
        platform: String,
    ): Map<String, Any?> = mapOf(
        "screen" to "recommend_sheets",
        "operation" to operation,
        "flowId" to flowId,
        "generation" to generation,
        "platform" to platform,
    )

    private fun newFlowId(operation: String, generation: Long): String =
        "recommend_sheets:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun buildTags(
        pinned: List<MusicSheetItemBase>,
        groups: List<com.hank.musicfree.plugin.api.MusicSheetGroupItem>,
    ): List<RecommendTag> {
        val map = linkedMapOf<String, RecommendTag>()
        map[DEFAULT_TAG_ID] = defaultTag()

        fun add(item: MusicSheetItemBase) {
            val id = item.id.ifBlank { return }
            if (map.containsKey(id)) return
            val payload = item.raw + mapOf("id" to item.id, "title" to item.title)
            map[id] = RecommendTag(
                id = id,
                title = item.title ?: id,
                payload = payload,
            )
        }

        pinned.forEach(::add)
        groups.flatMap { it.data }.forEach(::add)
        return map.values.toList()
    }

    private fun RecommendSheetsSceneState.tag(tagId: String): RecommendTag? {
        return tags.firstOrNull { it.id == tagId }
    }

    private fun RecommendSheetsSceneState.currentTag(): RecommendTag? {
        return tags.firstOrNull { it.id == selectedTagId }
    }

    private fun defaultTag(): RecommendTag = RecommendTag(
        id = DEFAULT_TAG_ID,
        title = "默认",
        payload = mapOf("id" to ""),
    )

    private fun RecommendSheetsPagerUiState.selectedScene(): RecommendSheetsUiState {
        return selectedPlatform?.let { platform ->
            scenes[platform] ?: RecommendSheetsSceneState()
        } ?: RecommendSheetsSceneState(
            loading = false,
            isEnd = true,
            emptyMessage = EMPTY_PLUGIN_MESSAGE,
        )
    }
}
