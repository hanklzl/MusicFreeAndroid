package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PluginUiItem(
    val info: PluginInfo,
    val enabled: Boolean,
)

sealed interface InstallState {
    data object Idle : InstallState
    data object Loading : InstallState
    data class Success(val message: String) : InstallState
    data class Error(val message: String) : InstallState
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
    ) { allInfos, disabled, order ->
        val items = allInfos.map { info ->
            PluginUiItem(info = info, enabled = info.platform !in disabled)
        }
        if (order.isEmpty()) return@combine items
        val orderMap = order.withIndex().associate { (i, p) -> p to i }
        items.sortedBy { orderMap[it.info.platform] ?: Int.MAX_VALUE }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
    }

    fun togglePluginEnabled(platform: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginManager.setPluginEnabled(platform, enabled)
        }
    }

    fun installFromUrl(url: String) {
        performOperation {
            val trimmed = url.trim()
            if (trimmed.isBlank()) {
                return@performOperation InstallState.Error("URL 不能为空")
            }
            val plugin = pluginManager.installFromUrl(trimmed, trimmed.substringAfterLast('/'))
            if (plugin != null) {
                InstallState.Success("安装成功：${plugin.info.platform}")
            } else {
                InstallState.Error("安装失败")
            }
        }
    }

    fun installFromFile(path: String) {
        performOperation {
            val file = java.io.File(path.trim())
            if (!file.exists()) {
                return@performOperation InstallState.Error("文件不存在：$path")
            }
            val plugin = pluginManager.installFromFile(file)
            if (plugin != null) {
                InstallState.Success("安装成功：${plugin.info.platform}")
            } else {
                InstallState.Error("安装失败")
            }
        }
    }

    fun updatePlugin(platform: String) {
        performOperation {
            val result = pluginManager.updatePlugin(platform)
            if (result.failures.isEmpty()) {
                InstallState.Success("更新成功")
            } else {
                InstallState.Error("更新失败：${result.failures.first().message}")
            }
        }
    }

    fun updateAllPlugins() {
        performOperation {
            val result = pluginManager.updateAllPlugins()
            if (result.failures.isEmpty()) {
                InstallState.Success("全部更新成功")
            } else {
                val msg = "成功 ${result.successCount} 个，失败 ${result.failureCount} 个"
                if (result.successCount > 0) InstallState.Success(msg) else InstallState.Error(msg)
            }
        }
    }

    fun updateSubscriptions() {
        performOperation {
            val subs = metaStore.subscriptions.first()
            if (subs.isEmpty()) {
                return@performOperation InstallState.Error("暂无订阅源")
            }
            var totalSuccess = 0
            var totalFail = 0
            var totalEntries = 0
            for (sub in subs) {
                val result = pluginManager.installFromSubscriptionUrl(sub.url)
                totalSuccess += result.successfulInstalls
                totalFail += result.failedInstalls
                totalEntries += result.totalEntries
            }
            if (totalFail == 0) {
                InstallState.Success("订阅更新完成：共 $totalEntries 项，全部成功")
            } else {
                val msg = "订阅更新：共 $totalEntries 项，成功 $totalSuccess，失败 $totalFail"
                if (totalSuccess > 0) InstallState.Success(msg) else InstallState.Error(msg)
            }
        }
    }

    fun uninstallPlugin(platform: String) {
        viewModelScope.launch {
            pluginManager.uninstall(platform)
        }
    }

    fun uninstallAllPlugins() {
        performOperation {
            pluginManager.uninstallAllPlugins()
            InstallState.Success("已卸载全部插件")
        }
    }

    fun resetInstallState() {
        _installState.value = InstallState.Idle
    }

    private fun performOperation(operation: suspend () -> InstallState) {
        if (_installState.value is InstallState.Loading) return
        _installState.value = InstallState.Loading
        viewModelScope.launch {
            try {
                _installState.value = operation()
            } catch (e: Exception) {
                _installState.value = InstallState.Error(e.message ?: "未知错误")
            }
        }
    }
}
