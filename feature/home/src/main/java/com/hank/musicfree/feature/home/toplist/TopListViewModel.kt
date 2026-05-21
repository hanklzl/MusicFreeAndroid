package com.hank.musicfree.feature.home.toplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.feature.home.pluginfeature.pluginsSupporting
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
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
class TopListViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val capablePlugins: StateFlow<List<LoadedPlugin>> = pluginManager.getSortedEnabledPlugins()
        .map { plugins -> plugins.filter { plugin -> plugin.supportsTopLists() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availablePlugins: StateFlow<List<PluginCapabilityUiModel>> = capablePlugins
        .map { plugins -> plugins.pluginsSupporting("getTopLists") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pagerUiState = MutableStateFlow(TopListPagerUiState())
    val pagerUiState: StateFlow<TopListPagerUiState> = _pagerUiState.asStateFlow()

    val selectedPlugin: StateFlow<String?> = pagerUiState
        .map { state -> state.selectedPlatform }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<TopListUiState> = pagerUiState
        .map { state -> state.selectedScene() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            TopListUiState.Error("当前没有支持榜单的插件"),
        )

    private val loadGenerationByPlatform = mutableMapOf<String, Long>()
    private val pluginInstanceByPlatform = mutableMapOf<String, LoadedPlugin>()

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            capablePlugins.collect { plugins ->
                val scenePlugins = plugins.pluginsSupporting("getTopLists")
                val platformToPlugin = plugins
                    .filter { it.supportsTopLists() }
                    .associateBy { it.info.platform.trim() }
                val validPlatforms = scenePlugins.map { it.platform }.toSet()

                loadGenerationByPlatform.keys.removeIf { it !in validPlatforms }
                pluginInstanceByPlatform.keys.removeIf { it !in validPlatforms }

                val preservedScenes = _pagerUiState.value.scenes
                val nextScenes = scenePlugins.associateBy(
                    { pluginModel -> pluginModel.platform },
                ) { pluginModel ->
                    val platform = pluginModel.platform
                    val currentPlugin = platformToPlugin[platform] ?: return@associateBy TopListUiState.Idle
                    val previousPlugin = pluginInstanceByPlatform[platform]
                    if (previousPlugin !== currentPlugin) {
                        invalidateLoadState(platform)
                        pluginInstanceByPlatform[platform] = currentPlugin
                        TopListUiState.Idle
                    } else {
                        pluginInstanceByPlatform[platform] = currentPlugin
                        preservedScenes[platform] ?: TopListUiState.Idle
                    }
                }

                val nextSelected = _pagerUiState.value.selectedPlatform
                    ?.takeIf { it in validPlatforms }
                    ?: scenePlugins.firstOrNull()?.platform

                _pagerUiState.update { state ->
                    state.copy(
                        plugins = scenePlugins,
                        scenes = nextScenes,
                        selectedPlatform = nextSelected,
                    )
                }

                _pagerUiState.value.selectedPlatform?.let { ensureSceneLoaded(it) }
            }
        }
    }

    fun selectPlugin(platform: String) {
        val targetPlatform = platform.trim()
        if (!_pagerUiState.value.plugins.any { it.platform == targetPlatform }) {
            return
        }
        _pagerUiState.update { state ->
            if (state.selectedPlatform == targetPlatform) {
                state
            } else {
                state.copy(selectedPlatform = targetPlatform)
            }
        }
        ensureSceneLoaded(targetPlatform)
    }

    fun ensureSceneLoaded(platform: String) {
        val normalizedPlatform = platform.trim()
        val scene = pagerUiState.value.scenes[normalizedPlatform] ?: return
        if (scene is TopListUiState.Loading || scene is TopListUiState.Success) {
            return
        }
        val plugin = pluginInstanceByPlatform[normalizedPlatform] ?: return

        val generation = nextLoadGeneration(normalizedPlatform)
        setScene(normalizedPlatform, TopListUiState.Loading)
        viewModelScope.launch {
            loadTopLists(normalizedPlatform, plugin, generation)
        }
    }

    fun refresh() {
        val platform = selectedPlugin.value ?: return
        refresh(platform)
    }

    fun refresh(platform: String) {
        val normalizedPlatform = platform.trim()
        val plugin = pluginInstanceByPlatform[normalizedPlatform] ?: return

        val generation = nextLoadGeneration(normalizedPlatform)
        setScene(normalizedPlatform, TopListUiState.Loading)
        viewModelScope.launch {
            if (!isCurrentLoad(normalizedPlatform, plugin, generation)) {
                clearLoading(normalizedPlatform)
                logStale(
                    plugin = plugin,
                    operation = "load_top_lists",
                    flowId = newFlowId("load_top_lists", generation),
                    generation = generation,
                    startedAt = System.nanoTime(),
                )
                return@launch
            }
            loadTopLists(normalizedPlatform, plugin, generation)
        }
    }

    private suspend fun loadTopLists(platform: String, plugin: LoadedPlugin, generation: Long) {
        val operation = "load_top_lists"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        if (!isCurrentLoad(platform, plugin, generation)) {
            clearLoading(platform)
            logStale(plugin, operation, flowId, generation, startedAt)
            return
        }

        MfLog.detail(
            LogCategory.HOME,
            "top_list_load_start",
            baseFields(plugin, operation, flowId, generation),
        )
        val result = runCatching { timedSuspend { plugin.getTopLists() } }
        if (!isCurrentLoad(platform, plugin, generation)) {
            clearLoading(platform)
            logStale(plugin, operation, flowId, generation, startedAt)
            return
        }

        result.onSuccess { (groups, durationMs) ->
            if (!isCurrentLoad(platform, plugin, generation)) {
                clearLoading(platform)
                logStale(plugin, operation, flowId, generation, startedAt)
                return@onSuccess
            }
            MfLog.detail(
                LogCategory.HOME,
                "top_list_load_success",
                baseFields(plugin, operation, flowId, generation) + mapOf(
                    "count" to groups.sumOf { it.data.size },
                    "durationMs" to durationMs,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            setScene(platform, TopListUiState.Success(groups))
        }.onFailure { e ->
            if (e is CancellationException) {
                logCancelled(plugin, operation, flowId, generation, startedAt)
                clearLoading(platform)
                throw e
            }
            if (!isCurrentLoad(platform, plugin, generation)) {
                clearLoading(platform)
                logStale(plugin, operation, flowId, generation, startedAt)
                return@onFailure
            }
            MfLog.error(
                LogCategory.HOME,
                "top_list_load_failed",
                e,
                baseFields(plugin, operation, flowId, generation) + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            setScene(platform, TopListUiState.Error(e.message ?: "加载榜单失败"))
        }
    }

    private fun nextLoadGeneration(platform: String): Long {
        val nextGeneration = (loadGenerationByPlatform[platform] ?: 0L) + 1L
        loadGenerationByPlatform[platform] = nextGeneration
        return nextGeneration
    }

    private fun invalidateLoadState(platform: String) {
        loadGenerationByPlatform[platform] = (loadGenerationByPlatform[platform] ?: 0L) + 1L
    }

    private fun isCurrentLoad(platform: String, plugin: LoadedPlugin, generation: Long): Boolean =
        isPlatformAlive(platform) &&
            pluginInstanceByPlatform[platform] === plugin &&
            loadGenerationByPlatform[platform] == generation

    private fun isPlatformAlive(platform: String): Boolean =
        _pagerUiState.value.plugins.any { it.platform == platform }

    private fun logStale(
        plugin: LoadedPlugin,
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_load_stale",
            baseFields(plugin, operation, flowId, generation) + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.STALE,
                "reason" to LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun logCancelled(
        plugin: LoadedPlugin,
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "top_list_load_cancelled",
            baseFields(plugin, operation, flowId, generation) + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
            ),
        )
    }

    private fun baseFields(
        plugin: LoadedPlugin,
        operation: String,
        flowId: String,
        generation: Long,
    ): Map<String, Any?> = mapOf(
        "screen" to "top_list",
        "operation" to operation,
        "flowId" to flowId,
        "generation" to generation,
        "platform" to plugin.info.platform.trim(),
    )

    private fun newFlowId(operation: String, generation: Long): String =
        "top_list:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun updateScene(
        platform: String,
        transform: (TopListUiState) -> TopListUiState,
    ) {
        if (!isPlatformAlive(platform)) {
            return
        }
        _pagerUiState.update { state ->
            val nextScenes = state.scenes.toMutableMap()
            nextScenes[platform] = transform(state.scenes[platform] ?: TopListUiState.Idle)
            state.copy(scenes = nextScenes)
        }
    }

    private fun setScene(platform: String, state: TopListUiState) {
        updateScene(platform) { state }
    }

    private fun clearLoading(platform: String) {
        updateScene(platform) { current ->
            if (current is TopListUiState.Loading) {
                TopListUiState.Idle
            } else {
                current
            }
        }
    }

    private fun LoadedPlugin.supportsTopLists(): Boolean =
        info.platform.trim().isNotBlank() && "getTopLists" in info.supportedMethods
}
