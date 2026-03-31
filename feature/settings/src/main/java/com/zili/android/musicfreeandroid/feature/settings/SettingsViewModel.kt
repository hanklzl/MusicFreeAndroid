package com.zili.android.musicfreeandroid.feature.settings

import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeDirectory
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InstallState {
    data object Idle : InstallState
    data object Loading : InstallState
    data class Success(val message: String) : InstallState
    data class Error(val message: String) : InstallState
}

data class StorageAccessState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    companion object {
        private const val DEFAULT_SUBSCRIPTION_URL = "https://13413.kstore.vip/yuanli/yuanli.json"
    }

    val plugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { list -> list.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
    }

    fun installFromUrl(url: String) {
        if (_installState.value is InstallState.Loading) {
            return
        }
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _installState.value = InstallState.Error("URL 不能为空")
            return
        }

        performInstallOperation {
            val fileName = trimmedUrl.substringAfterLast("/").ifBlank { "plugin.js" }
            val result = pluginManager.installFromUrl(trimmedUrl, fileName)
            if (result != null) {
                InstallState.Success("插件安装成功")
            } else {
                InstallState.Error("插件安装失败")
            }
        }
    }

    fun installFromFile(filePath: String) {
        if (_installState.value is InstallState.Loading) {
            return
        }
        val trimmedPath = filePath.trim()
        if (trimmedPath.isBlank()) {
            _installState.value = InstallState.Error("本地插件文件路径不能为空")
            return
        }

        performInstallOperation {
            val file = File(trimmedPath)
            if (!file.exists() || !file.isFile) {
                InstallState.Error("本地插件文件不存在")
            } else {
                val result = pluginManager.installFromFile(file)
                if (result != null) {
                    InstallState.Success("本地插件安装成功")
                } else {
                    InstallState.Error("本地插件安装失败")
                }
            }
        }
    }

    fun installDefaultSubscription() {
        performInstallOperation {
            val result = pluginManager.installFromSubscriptionUrl(DEFAULT_SUBSCRIPTION_URL)
            val errorMessage = result.errorMessage
            when {
                errorMessage != null -> InstallState.Error(errorMessage)
                result.successfulInstalls == 0 -> InstallState.Error(
                    "订阅导入失败：共 ${result.totalEntries} 项，成功 0，失败 ${result.failedInstalls}",
                )
                else -> InstallState.Success(
                    "订阅导入完成：共 ${result.totalEntries} 项，成功 ${result.successfulInstalls}，失败 ${result.failedInstalls}",
                )
            }
        }
    }

    fun updatePlugin(platform: String) {
        performInstallOperation {
            val result = pluginManager.updatePlugin(platform)
            if (result.successCount > 0) {
                InstallState.Success("插件更新成功")
            } else {
                InstallState.Error(
                    result.failures.firstOrNull()?.message ?: "插件更新失败",
                )
            }
        }
    }

    fun updateAllPlugins() {
        performInstallOperation {
            val result = pluginManager.updateAllPlugins()
            when {
                result.successCount > 0 && result.failureCount == 0 -> {
                    InstallState.Success(
                        "全部插件更新完成：成功 ${result.successCount}，失败 ${result.failureCount}",
                    )
                }
                result.successCount > 0 -> {
                    InstallState.Success(
                        "全部插件更新完成：成功 ${result.successCount}，失败 ${result.failureCount}",
                    )
                }
                else -> {
                    InstallState.Error(
                        result.failures.firstOrNull()?.message ?: "全部插件更新失败",
                    )
                }
            }
        }
    }

    private fun performInstallOperation(operation: suspend () -> InstallState) {
        if (_installState.value is InstallState.Loading) {
            return
        }
        _installState.value = InstallState.Loading
        viewModelScope.launch {
            try {
                _installState.value = operation()
            } catch (e: Exception) {
                _installState.value = InstallState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun uninstallPlugin(platform: String) {
        viewModelScope.launch {
            pluginManager.uninstall(platform)
        }
    }

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    fun resetInstallState() {
        _installState.value = InstallState.Idle
    }
}
