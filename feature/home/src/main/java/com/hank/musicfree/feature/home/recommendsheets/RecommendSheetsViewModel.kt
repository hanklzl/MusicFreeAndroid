package com.hank.musicfree.feature.home.recommendsheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.feature.home.pluginfeature.pluginsSupporting
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendSheetsViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    companion object {
        private const val DEFAULT_TAG_ID = "__default__"
    }

    val availablePlugins: StateFlow<List<PluginCapabilityUiModel>> = pluginManager.getSortedEnabledPlugins()
        .map { plugins -> plugins.pluginsSupporting("getRecommendSheetsByTag") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow(RecommendSheetsUiState(loading = true))
    val uiState: StateFlow<RecommendSheetsUiState> = _uiState.asStateFlow()

    private var page: Int = 0
    private var loadGeneration: Long = 0

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                when {
                    plugins.isEmpty() -> {
                        invalidateLoads()
                        page = 0
                        _selectedPlugin.value = null
                        _uiState.value = RecommendSheetsUiState(
                            loading = false,
                            isEnd = true,
                            emptyMessage = "当前没有支持推荐歌单的插件",
                        )
                    }
                    _selectedPlugin.value == null ||
                        plugins.none { it.platform == _selectedPlugin.value } -> {
                        selectPlugin(plugins.first().platform)
                    }
                }
            }
        }
    }

    fun selectPlugin(platform: String) {
        _selectedPlugin.value = platform
        val generation = nextLoadGeneration()
        viewModelScope.launch {
            loadTagsAndFirstPage(platform, generation)
        }
    }

    fun selectTag(tagId: String) {
        val platform = _selectedPlugin.value ?: return
        val tag = _uiState.value.tags.firstOrNull { it.id == tagId } ?: return
        if (_uiState.value.selectedTagId == tag.id && _uiState.value.sheets.isNotEmpty()) {
            return
        }
        val generation = nextLoadGeneration()
        viewModelScope.launch {
            if (!isCurrentLoad(platform, generation)) return@launch
            page = 0
            _uiState.value = _uiState.value.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                loading = true,
                loadingMore = false,
                isEnd = false,
                errorMessage = null,
                emptyMessage = null,
            )
            loadSheets(platform = platform, tag = tag, reset = true, generation = generation)
        }
    }

    fun refresh() {
        val platform = _selectedPlugin.value ?: return
        val tag = currentTag() ?: defaultTag()
        val generation = nextLoadGeneration()
        viewModelScope.launch {
            if (!isCurrentLoad(platform, generation)) return@launch
            page = 0
            _uiState.value = _uiState.value.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                loading = true,
                loadingMore = false,
                isEnd = false,
                errorMessage = null,
                emptyMessage = null,
            )
            loadSheets(platform = platform, tag = tag, reset = true, generation = generation)
        }
    }

    fun loadMore() {
        val platform = _selectedPlugin.value ?: return
        val state = _uiState.value
        val tag = currentTag() ?: defaultTag()
        if (state.loading || state.loadingMore || state.isEnd) {
            return
        }
        val generation = nextLoadGeneration()
        viewModelScope.launch {
            loadSheets(platform = platform, tag = tag, reset = false, generation = generation)
        }
    }

    private suspend fun loadTagsAndFirstPage(platform: String, generation: Long) {
        val operation = "load_tags"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        if (!isCurrentLoad(platform, generation)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }
        val plugin = pluginManager.getPlugin(platform)
        if (plugin == null) {
            if (!isCurrentLoad(platform, generation)) {
                logStale(operation, flowId, generation, platform, startedAt)
                return
            }
            logFailure(
                event = "recommend_sheets_tags_failed",
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                platform = platform,
                startedAt = startedAt,
                fields = mapOf("reason" to "plugin_missing"),
            )
            _uiState.value = RecommendSheetsUiState(
                loading = false,
                isEnd = true,
                errorMessage = "插件不存在：$platform",
            )
            return
        }

        if (!isCurrentLoad(platform, generation)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }
        _uiState.value = RecommendSheetsUiState(loading = true)
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
            logFailure(
                event = "recommend_sheets_tags_failed",
                throwable = error,
                operation = operation,
                flowId = flowId,
                generation = generation,
                platform = platform,
                startedAt = startedAt,
            )
        }.getOrNull()?.first
        if (!isCurrentLoad(platform, generation)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }

        val tags = buildTags(tagsResult?.pinned.orEmpty(), tagsResult?.data.orEmpty())
        val selected = tags.firstOrNull() ?: defaultTag()

        page = 0
        _uiState.value = RecommendSheetsUiState(
            tags = tags,
            selectedTagId = selected.id,
            loading = true,
        )
        loadSheets(platform = platform, tag = selected, reset = true, generation = generation)
    }

    private suspend fun loadSheets(
        platform: String,
        tag: RecommendTag,
        reset: Boolean,
        generation: Long,
    ) {
        val operation = if (reset) "load_initial" else "load_more"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        if (!isCurrentLoad(platform, generation)) {
            logStale(operation, flowId, generation, platform, startedAt)
            return
        }
        val plugin = pluginManager.getPlugin(platform)
        if (plugin == null) {
            if (!isCurrentLoad(platform, generation)) {
                logStale(operation, flowId, generation, platform, startedAt)
                return
            }
            logFailure(
                event = "recommend_sheets_load_failed",
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                platform = platform,
                startedAt = startedAt,
                fields = mapOf(
                    "page" to if (reset) 1 else page + 1,
                    "itemId" to tag.id,
                    "reason" to "plugin_missing",
                ),
            )
            _uiState.value = _uiState.value.copy(
                loading = false,
                loadingMore = false,
                errorMessage = "插件不存在：$platform",
            )
            return
        }

        val nextPage = if (reset) 1 else page + 1
        if (!isCurrentLoad(platform, generation)) {
            logStale(operation, flowId, generation, platform, startedAt, mapOf("page" to nextPage))
            return
        }
        _uiState.value = _uiState.value.copy(
            loading = reset,
            loadingMore = !reset,
            errorMessage = null,
            emptyMessage = null,
        )

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
        if (!isCurrentLoad(platform, generation)) {
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
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loadingMore = false,
                    errorMessage = "加载推荐歌单失败",
                )
                return@onSuccess
            }
            page = nextPage
            val incoming = sheetsResult.data.map { item ->
                if (item.platform.isBlank()) item.copy(platform = platform) else item
            }
            val merged = if (reset) incoming else _uiState.value.sheets + incoming
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
            _uiState.value = _uiState.value.copy(
                sheets = merged,
                loading = false,
                loadingMore = false,
                isEnd = sheetsResult.isEnd,
                errorMessage = null,
            )
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
            _uiState.value = _uiState.value.copy(
                loading = false,
                loadingMore = false,
                errorMessage = e.message ?: "加载推荐歌单失败",
            )
        }
    }

    private fun nextLoadGeneration(): Long {
        loadGeneration += 1
        return loadGeneration
    }

    private fun invalidateLoads() {
        loadGeneration += 1
    }

    private fun isCurrentLoad(platform: String, generation: Long): Boolean =
        loadGeneration == generation && _selectedPlugin.value == platform

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

    private fun currentTag(): RecommendTag? {
        val state = _uiState.value
        return state.tags.firstOrNull { it.id == state.selectedTagId }
    }

    private fun defaultTag(): RecommendTag = RecommendTag(
        id = DEFAULT_TAG_ID,
        title = "默认",
        payload = mapOf("id" to ""),
    )
}
