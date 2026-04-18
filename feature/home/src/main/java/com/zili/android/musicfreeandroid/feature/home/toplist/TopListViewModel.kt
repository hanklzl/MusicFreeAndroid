package com.zili.android.musicfreeandroid.feature.home.toplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class TopListViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    val availablePlugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { list -> list.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow<TopListUiState>(TopListUiState.Idle)
    val uiState: StateFlow<TopListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                if (_selectedPlugin.value == null && plugins.isNotEmpty()) {
                    selectPlugin(plugins.first().platform)
                }
            }
        }
    }

    fun selectPlugin(platform: String) {
        if (_selectedPlugin.value == platform && _uiState.value is TopListUiState.Success) {
            return
        }
        _selectedPlugin.value = platform
        loadTopLists(platform)
    }

    fun refresh() {
        val platform = _selectedPlugin.value ?: return
        loadTopLists(platform)
    }

    private fun loadTopLists(platform: String) {
        val plugin = pluginManager.getPlugin(platform)
        if (plugin == null) {
            _uiState.value = TopListUiState.Error("插件不存在：$platform")
            return
        }

        viewModelScope.launch {
            _uiState.value = TopListUiState.Loading
            runCatching {
                plugin.getTopLists()
            }.onSuccess { groups ->
                _uiState.value = TopListUiState.Success(groups)
            }.onFailure { e ->
                _uiState.value = TopListUiState.Error(e.message ?: "加载榜单失败")
            }
        }
    }
}
