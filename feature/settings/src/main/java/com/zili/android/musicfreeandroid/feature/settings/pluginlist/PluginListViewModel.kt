package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationFailure
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationResult
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationType
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PluginUiItem(
    val info: PluginInfo,
    val enabled: Boolean,
    val alternativePlatform: String?,
    val alternativeInvalid: Boolean,
    val canUpdate: Boolean,
    val canImportMusicItem: Boolean,
    val canImportMusicSheet: Boolean,
    val canEditUserVariables: Boolean,
)

sealed interface InstallState {
    data object Idle : InstallState
    data object Loading : InstallState
    data class Success(val message: String) : InstallState
    data class Error(val message: String) : InstallState
}

data class FailureDetail(
    val source: String?,
    val pluginName: String?,
    val message: String,
)

sealed interface PluginOperationUiState {
    data object Idle : PluginOperationUiState
    data class Loading(val label: String) : PluginOperationUiState
    data class Success(val message: String) : PluginOperationUiState
    data class PartialFailure(
        val message: String,
        val failures: List<FailureDetail>,
    ) : PluginOperationUiState
    data class Failure(
        val message: String,
        val failures: List<FailureDetail> = emptyList(),
    ) : PluginOperationUiState

    companion object {
        fun fromResult(
            successMessage: String,
            partialMessage: String,
            failureMessage: String,
            result: PluginOperationResult,
        ): PluginOperationUiState {
            val details = result.failures.map {
                FailureDetail(
                    source = it.sourceRef,
                    pluginName = it.targetPlugin,
                    message = it.message,
                )
            }
            return when {
                result.failureCount == 0 -> Success(successMessage)
                result.successCount > 0 -> PartialFailure(partialMessage, details)
                else -> Failure(failureMessage, details)
            }
        }
    }
}

@HiltViewModel
class PluginListViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val metaStore: PluginMetaStore = pluginManager.pluginMetaStore

    val pluginItems: StateFlow<List<PluginUiItem>> = combine(
        pluginManager.plugins.map { list -> list.map { it.info } },
        metaStore.disabledPlugins,
        metaStore.pluginOrder,
        metaStore.alternativePlugins,
    ) { allInfos, disabled, order, alternatives ->
        val mediaSourcePlatforms = allInfos
            .filter { it.platform !in disabled && "getMediaSource" in it.supportedMethods }
            .map { it.platform }
            .toSet()
        val items = allInfos.map { info ->
            val alternative = alternatives[info.platform]
            PluginUiItem(
                info = info,
                enabled = info.platform !in disabled,
                alternativePlatform = alternative,
                alternativeInvalid = alternative != null && alternative !in mediaSourcePlatforms,
                canUpdate = !info.srcUrl.isNullOrBlank(),
                canImportMusicItem = "importMusicItem" in info.supportedMethods,
                canImportMusicSheet = "importMusicSheet" in info.supportedMethods,
                canEditUserVariables = info.userVariables.isNotEmpty(),
            )
        }
        if (order.isEmpty()) return@combine items
        val orderMap = order.withIndex().associate { (i, p) -> p to i }
        items.sortedBy { orderMap[it.info.platform] ?: Int.MAX_VALUE }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _operationState = MutableStateFlow<PluginOperationUiState>(PluginOperationUiState.Idle)
    val operationState: StateFlow<PluginOperationUiState> = _operationState.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
    }

    fun togglePluginEnabled(platform: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginManager.setPluginEnabled(platform, enabled)
        }
    }

    fun setAlternativePlugin(sourcePlatform: String, targetPlatform: String?) {
        viewModelScope.launch {
            metaStore.setAlternativePlugin(sourcePlatform, targetPlatform)
        }
    }

    fun saveUserVariables(platform: String, values: Map<String, String>) {
        performOperation(label = "保存用户变量中") {
            pluginManager.setUserVariables(platform, values)
            PluginOperationUiState.Success("设置成功")
        }
    }

    fun userVariables(platform: String): Flow<Map<String, String>> =
        metaStore.getUserVariables(platform)

    fun hideAddToPlaylistSheet() {
        _sheetState.value = AddToPlaylistSheetState()
    }

    fun importMusicItem(platform: String, urlLike: String) {
        performOperation(label = "导入单曲中") {
            _sheetState.value = AddToPlaylistSheetState()
            val trimmed = urlLike.trim()
            if (trimmed.isBlank()) {
                return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
            }
            val item = pluginManager.getPlugin(platform)?.importMusicItem(trimmed)
                ?: return@performOperation PluginOperationUiState.Failure("导入单曲失败")
            _sheetState.value = AddToPlaylistSheetState.single(item)
            PluginOperationUiState.Success("解析成功")
        }
    }

    fun importMusicSheet(platform: String, urlLike: String) {
        performOperation(label = "导入歌单中") {
            _sheetState.value = AddToPlaylistSheetState()
            val trimmed = urlLike.trim()
            if (trimmed.isBlank()) {
                return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
            }
            val items = pluginManager.getPlugin(platform)?.importMusicSheet(trimmed).orEmpty()
            if (items.isEmpty()) {
                return@performOperation PluginOperationUiState.Failure("链接有误或目标歌单为空")
            }
            _sheetState.value = AddToPlaylistSheetState.batch(items)
            PluginOperationUiState.Success("发现 ${items.size} 首歌曲")
        }
    }

    fun installFromUrl(url: String) {
        performOperation(label = "正在安装") {
            val result = pluginManager.installFromNetworkUrl(url)
            PluginOperationUiState.fromResult(
                successMessage = "安装成功",
                partialMessage = "部分插件安装失败",
                failureMessage = "安装失败",
                result = result,
            )
        }
    }

    fun installFromFile(path: String) {
        performOperation(label = "正在安装") {
            val file = File(path.trim())
            if (!file.exists()) {
                return@performOperation PluginOperationUiState.Failure(
                    message = "文件不存在：$path",
                    failures = listOf(
                        FailureDetail(
                            source = path,
                            pluginName = null,
                            message = "文件不存在",
                        ),
                    ),
                )
            }
            val plugin = pluginManager.installFromFile(file)
            if (plugin != null) {
                PluginOperationUiState.Success("安装成功：${plugin.info.platform}")
            } else {
                PluginOperationUiState.Failure(
                    message = "安装失败",
                    failures = listOf(
                        FailureDetail(
                            source = file.absolutePath,
                            pluginName = null,
                            message = "插件格式无效",
                        ),
                    ),
                )
            }
        }
    }

    fun updatePlugin(platform: String) {
        performOperation(label = "正在更新") {
            val result = pluginManager.updatePlugin(platform)
            PluginOperationUiState.fromResult(
                successMessage = "更新成功",
                partialMessage = "部分插件更新失败",
                failureMessage = "更新失败",
                result = result,
            )
        }
    }

    fun updateAllPlugins() {
        performOperation(label = "正在更新全部插件") {
            val result = pluginManager.updateAllPlugins()
            PluginOperationUiState.fromResult(
                successMessage = "全部更新成功",
                partialMessage = "部分插件更新失败",
                failureMessage = "全部插件更新失败",
                result = result,
            )
        }
    }

    fun updateSubscriptions() {
        performOperation(label = "正在更新订阅") {
            val subs = metaStore.subscriptions.first()
            if (subs.isEmpty()) {
                return@performOperation PluginOperationUiState.Failure("暂无订阅源")
            }
            var totalSuccess = 0
            val failures = mutableListOf<PluginOperationFailure>()
            val targetPlugins = mutableListOf<String>()
            val startedAt = System.currentTimeMillis()
            for (sub in subs) {
                val result = pluginManager.updateFromSubscriptionUrl(sub.url)
                totalSuccess += result.successCount
                failures += result.failures
                targetPlugins += result.targetPlugins
            }
            PluginOperationUiState.fromResult(
                successMessage = "订阅更新完成",
                partialMessage = "部分订阅更新失败",
                failureMessage = "订阅更新失败",
                result = PluginOperationResult(
                    operationType = PluginOperationType.UPDATE_SUBSCRIPTION,
                    targetPlugins = targetPlugins,
                    successCount = totalSuccess,
                    failureCount = failures.size,
                    failures = failures,
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun uninstallPlugin(platform: String) {
        viewModelScope.launch {
            pluginManager.uninstall(platform)
        }
    }

    fun uninstallAllPlugins() {
        performOperation(label = "正在卸载") {
            pluginManager.uninstallAllPlugins()
            PluginOperationUiState.Success("已卸载全部插件")
        }
    }

    fun resetInstallState() {
        setOperationState(PluginOperationUiState.Idle)
    }

    private fun performOperation(
        label: String,
        operation: suspend () -> PluginOperationUiState,
    ) {
        if (_operationState.value is PluginOperationUiState.Loading) return
        setOperationState(PluginOperationUiState.Loading(label))
        viewModelScope.launch {
            try {
                setOperationState(operation())
            } catch (e: Exception) {
                setOperationState(PluginOperationUiState.Failure(e.message ?: "未知错误"))
            }
        }
    }

    private fun setOperationState(state: PluginOperationUiState) {
        _operationState.value = state
        _installState.value = state.toInstallState()
    }

    private fun PluginOperationUiState.toInstallState(): InstallState {
        return when (this) {
            PluginOperationUiState.Idle -> InstallState.Idle
            is PluginOperationUiState.Loading -> InstallState.Loading
            is PluginOperationUiState.Success -> InstallState.Success(message)
            is PluginOperationUiState.PartialFailure -> InstallState.Error(message)
            is PluginOperationUiState.Failure -> InstallState.Error(message)
        }
    }
}
