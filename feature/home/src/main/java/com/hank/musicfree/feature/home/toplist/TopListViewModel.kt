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

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow<TopListUiState>(TopListUiState.Idle)
    val uiState: StateFlow<TopListUiState> = _uiState.asStateFlow()

    private var loadGeneration: Long = 0
    private var selectedPluginInstance: LoadedPlugin? = null

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            capablePlugins.collect { plugins ->
                when {
                    plugins.isEmpty() -> {
                        invalidateLoads()
                        selectedPluginInstance = null
                        _selectedPlugin.value = null
                        _uiState.value = TopListUiState.Error("当前没有支持榜单的插件")
                    }
                    selectedPluginInstance == null -> {
                        selectPlugin(plugins.first())
                    }
                    plugins.none { it === selectedPluginInstance } -> {
                        val replacement = _selectedPlugin.value?.let { platform ->
                            plugins.firstOrNull { it.info.platform.trim() == platform }
                        }
                        selectPlugin(replacement ?: plugins.first())
                    }
                }
            }
        }
    }

    fun selectPlugin(platform: String) {
        val plugin = capablePlugins.value.firstOrNull { it.info.platform.trim() == platform }
            ?: pluginManager.getPlugin(platform)?.takeIf { it.supportsTopLists() }
        if (plugin == null) {
            invalidateLoads()
            selectedPluginInstance = null
            _selectedPlugin.value = null
            _uiState.value = TopListUiState.Error(
                if (capablePlugins.value.isEmpty()) {
                    "当前没有支持榜单的插件"
                } else {
                    "插件不存在：$platform"
                },
            )
            return
        }
        selectPlugin(plugin)
    }

    fun refresh() {
        val plugin = currentSelectedPlugin() ?: return
        selectedPluginInstance = plugin
        _selectedPlugin.value = plugin.info.platform.trim()
        loadTopLists(plugin, nextLoadGeneration())
    }

    private fun selectPlugin(plugin: LoadedPlugin) {
        if (selectedPluginInstance === plugin && _uiState.value is TopListUiState.Success) {
            return
        }
        selectedPluginInstance = plugin
        _selectedPlugin.value = plugin.info.platform.trim()
        loadTopLists(plugin, nextLoadGeneration())
    }

    private fun loadTopLists(plugin: LoadedPlugin, generation: Long) {
        val operation = "load_top_lists"
        val flowId = newFlowId(operation, generation)
        if (!isCurrentLoad(plugin, generation)) {
            logStale(plugin, operation, flowId, generation, System.nanoTime())
            return
        }
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            if (!isCurrentLoad(plugin, generation)) {
                logStale(plugin, operation, flowId, generation, startedAt)
                return@launch
            }
            _uiState.value = TopListUiState.Loading
            MfLog.detail(
                LogCategory.HOME,
                "top_list_load_start",
                baseFields(plugin, operation, flowId, generation),
            )
            val result = runCatching {
                timedSuspend { plugin.getTopLists() }
            }
            if (!isCurrentLoad(plugin, generation)) {
                logStale(plugin, operation, flowId, generation, startedAt)
                return@launch
            }

            result.onSuccess { (groups, durationMs) ->
                MfLog.detail(
                    LogCategory.HOME,
                    "top_list_load_success",
                    baseFields(plugin, operation, flowId, generation) + mapOf(
                        "count" to groups.sumOf { it.data.size },
                        "durationMs" to durationMs,
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
                _uiState.value = TopListUiState.Success(groups)
            }.onFailure { e ->
                if (e is CancellationException) {
                    logCancelled(plugin, operation, flowId, generation, startedAt)
                    throw e
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
                _uiState.value = TopListUiState.Error(e.message ?: "加载榜单失败")
            }
        }
    }

    private fun nextLoadGeneration(): Long {
        loadGeneration += 1
        return loadGeneration
    }

    private fun invalidateLoads() {
        loadGeneration += 1
    }

    private fun isCurrentLoad(plugin: LoadedPlugin, generation: Long): Boolean =
        loadGeneration == generation &&
            selectedPluginInstance === plugin &&
            _selectedPlugin.value == plugin.info.platform.trim()

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

    private fun currentSelectedPlugin(): LoadedPlugin? {
        val current = selectedPluginInstance
        if (current != null && _selectedPlugin.value == current.info.platform.trim()) {
            return current
        }
        val platform = _selectedPlugin.value ?: return null
        return capablePlugins.value.firstOrNull { it.info.platform.trim() == platform }
            ?: pluginManager.getPlugin(platform)
    }

    private fun LoadedPlugin.supportsTopLists(): Boolean =
        info.platform.trim().isNotBlank() && "getTopLists" in info.supportedMethods
}
