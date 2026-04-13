package com.zili.android.musicfreeandroid.feature.settings.pluginsort

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginSortViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val _sortedPlatforms = MutableStateFlow<List<String>>(emptyList())
    val sortedPlatforms: StateFlow<List<String>> = _sortedPlatforms.asStateFlow()

    init {
        viewModelScope.launch {
            val allPlatforms = pluginManager.plugins.first().map { it.info.platform }
            val savedOrder = pluginManager.pluginMetaStore.pluginOrder.first()

            _sortedPlatforms.value = if (savedOrder.isEmpty()) {
                allPlatforms
            } else {
                val orderMap = savedOrder.withIndex().associate { (i, p) -> p to i }
                allPlatforms.sortedBy { orderMap[it] ?: Int.MAX_VALUE }
            }
        }
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val current = _sortedPlatforms.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _sortedPlatforms.value = current
    }

    fun saveOrder() {
        viewModelScope.launch {
            pluginManager.setPluginOrder(_sortedPlatforms.value)
        }
    }
}
