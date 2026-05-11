package com.zili.android.musicfreeandroid.feature.home.toplist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolver
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.fallbackTopListSeed
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.timedSuspend
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TopListDetailRoute>()
    private val seedResolver = PluginSheetRouteSeedResolver(route.seedToken) {
        route.fallbackTopListSeed()
    }

    private val _uiState = MutableStateFlow(TopListDetailUiState(loading = true))
    val uiState: StateFlow<TopListDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentTopList: MusicSheetItemBase? = null
    private var loadGeneration: Long = 0

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
            loadInitial(nextLoadGeneration())
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial(nextLoadGeneration())
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val topList = currentTopList
        if (state.loading || state.loadingMore || state.isEnd || topList == null) {
            return
        }

        val generation = nextLoadGeneration()
        val operation = "load_more"
        val flowId = newFlowId(operation, generation)
        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            logLoadFailure(
                event = "top_list_detail_load_failed",
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = System.nanoTime(),
                fields = topListFields(topList) + mapOf("page" to page + 1, "reason" to "plugin_missing"),
            )
            return
        }
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            logLoadStart(operation, flowId, generation, topListFields(topList) + mapOf("page" to page + 1))
            runCatching {
                timedSuspend { plugin.getTopListDetail(topList, page + 1) }
            }.onSuccess { (detail, durationMs) ->
                if (!isCurrentLoad(generation)) {
                    logLoadStale(operation, flowId, generation, startedAt, topListFields(topList) + mapOf("page" to page + 1))
                    return@onSuccess
                }
                if (detail == null) {
                    logLoadFailure(
                        event = "top_list_detail_load_failed",
                        throwable = null,
                        operation = operation,
                        flowId = flowId,
                        generation = generation,
                        startedAt = startedAt,
                        fields = topListFields(topList) + mapOf("page" to page + 1, "reason" to LogFields.Reason.UNKNOWN),
                    )
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载榜单失败",
                    )
                    return@onSuccess
                }
                page += 1
                currentTopList = detail.topListItem ?: topList
                logLoadSuccess(
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    durationMs = durationMs,
                    fields = topListFields(currentTopList) + mapOf(
                        "page" to page,
                        "count" to detail.musicList.size,
                        "isEnd" to detail.isEnd,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    title = detail.topListItem?.title ?: _uiState.value.title,
                    topListItem = currentTopList,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                if (e is CancellationException) {
                    logLoadCancelled(operation, flowId, generation, startedAt, topListFields(topList) + mapOf("page" to page + 1))
                    throw e
                }
                if (!isCurrentLoad(generation)) {
                    logLoadStale(operation, flowId, generation, startedAt, topListFields(topList) + mapOf("page" to page + 1))
                    return@onFailure
                }
                logLoadFailure(
                    event = "top_list_detail_load_failed",
                    throwable = e,
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    startedAt = startedAt,
                    fields = topListFields(topList) + mapOf("page" to page + 1),
                )
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载榜单失败",
                )
            }
        }
    }

    suspend fun playAt(index: Int): Boolean {
        val list = _uiState.value.musicList
        if (index !in list.indices) {
            MfLog.detail(
                LogCategory.HOME,
                "top_list_detail_play_skipped",
                mapOf(
                    "screen" to SCREEN_TOP_LIST_DETAIL,
                    "operation" to "play_at",
                    "platform" to route.pluginPlatform,
                    "page" to index,
                    "count" to list.size,
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to "invalid_index",
                ),
            )
            return false
        }

        val clicked = list[index]
        val resolved = if (clicked.url.isNullOrBlank()) {
            mediaSourceResolver.resolve(clicked)?.item ?: run {
                MfLog.error(
                    LogCategory.HOME,
                    "top_list_detail_play_failed",
                    fields = musicItemFields(clicked) + mapOf(
                        "screen" to SCREEN_TOP_LIST_DETAIL,
                        "operation" to "play_at",
                        "page" to index,
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "no_source",
                    ),
                )
                return false
            }
        } else {
            clicked
        }

        val queue = list.toMutableList()
        queue[index] = resolved
        when (appPreferences.clickMusicInAlbum.first()) {
            AlbumMusicClickAction.PlayMusic -> playerController.playItem(resolved)
            AlbumMusicClickAction.PlayAlbum -> playerController.playQueue(queue, index)
        }
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_play_success",
            musicItemFields(resolved) + mapOf(
                "screen" to SCREEN_TOP_LIST_DETAIL,
                "operation" to "play_at",
                "page" to index,
                "count" to queue.size,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        return true
    }

    private suspend fun loadInitial(generation: Long) {
        val operation = "load_initial"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        _uiState.value = TopListDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            logLoadFailure(
                event = "top_list_detail_load_failed",
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = startedAt,
                fields = mapOf("reason" to "plugin_missing"),
            )
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seedTopList = seedResolver.resolve()

        logLoadStart(operation, flowId, generation, topListFields(seedTopList) + mapOf("page" to 1))
        runCatching {
            timedSuspend { plugin.getTopListDetail(seedTopList, page = 1) }
        }.onSuccess { (detail, durationMs) ->
            if (!isCurrentLoad(generation)) {
                logLoadStale(operation, flowId, generation, startedAt, topListFields(seedTopList) + mapOf("page" to 1))
                return@onSuccess
            }
            if (detail == null) {
                logLoadFailure(
                    event = "top_list_detail_load_failed",
                    throwable = null,
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    startedAt = startedAt,
                    fields = topListFields(seedTopList) + mapOf("page" to 1, "reason" to LogFields.Reason.UNKNOWN),
                )
                _uiState.value = TopListDetailUiState(
                    loading = false,
                    errorMessage = "加载榜单失败",
                )
                return@onSuccess
            }
            page = 1
            currentTopList = detail.topListItem ?: seedTopList
            logLoadSuccess(
                operation = operation,
                flowId = flowId,
                generation = generation,
                durationMs = durationMs,
                fields = topListFields(currentTopList) + mapOf(
                    "page" to 1,
                    "count" to detail.musicList.size,
                    "isEnd" to detail.isEnd,
                ),
            )
            _uiState.value = TopListDetailUiState(
                title = detail.topListItem?.title ?: seedTopList.title ?: "榜单详情",
                topListItem = currentTopList,
                musicList = detail.musicList,
                loading = false,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            if (e is CancellationException) {
                logLoadCancelled(operation, flowId, generation, startedAt, topListFields(seedTopList) + mapOf("page" to 1))
                throw e
            }
            if (!isCurrentLoad(generation)) {
                logLoadStale(operation, flowId, generation, startedAt, topListFields(seedTopList) + mapOf("page" to 1))
                return@onFailure
            }
            logLoadFailure(
                event = "top_list_detail_load_failed",
                throwable = e,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = startedAt,
                fields = topListFields(seedTopList) + mapOf("page" to 1),
            )
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载榜单失败",
            )
        }
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_TOP_LIST_DETAIL,
                "operation" to "download",
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

    private fun nextLoadGeneration(): Long {
        loadGeneration += 1
        return loadGeneration
    }

    private fun isCurrentLoad(generation: Long): Boolean = loadGeneration == generation

    private fun logLoadStart(
        operation: String,
        flowId: String,
        generation: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_load_start",
            baseLoadFields(operation, flowId, generation) + fields,
        )
    }

    private fun logLoadSuccess(
        operation: String,
        flowId: String,
        generation: Long,
        durationMs: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_load_success",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to durationMs,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
    }

    private fun logLoadFailure(
        event: String,
        throwable: Throwable?,
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.error(
            LogCategory.HOME,
            event,
            throwable,
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.FAILURE,
            ),
        )
    }

    private fun logLoadStale(
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_load_stale",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.STALE,
                "reason" to LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun logLoadCancelled(
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_load_cancelled",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
            ),
        )
    }

    private fun baseLoadFields(operation: String, flowId: String, generation: Long): Map<String, Any?> = mapOf(
        "screen" to SCREEN_TOP_LIST_DETAIL,
        "operation" to operation,
        "flowId" to flowId,
        "generation" to generation,
        "platform" to route.pluginPlatform,
    )

    private fun topListFields(item: MusicSheetItemBase?): Map<String, Any?> = mapOf(
        "listId" to item?.id.orEmpty(),
        "itemName" to item?.title.orEmpty(),
    )

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun newFlowId(operation: String, generation: Long): String =
        "$SCREEN_TOP_LIST_DETAIL:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val SCREEN_TOP_LIST_DETAIL = "top_list_detail"
    }
}
