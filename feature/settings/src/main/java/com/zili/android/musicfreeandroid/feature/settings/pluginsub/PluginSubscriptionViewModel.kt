package com.zili.android.musicfreeandroid.feature.settings.pluginsub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.plugin.meta.SubscriptionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginSubscriptionViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val metaStore: PluginMetaStore = pluginManager.pluginMetaStore

    val subscriptions: StateFlow<List<SubscriptionItem>> = metaStore.subscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun addSubscription(name: String, url: String) {
        viewModelScope.launch {
            metaStore.addSubscription(name, url)
        }
    }

    fun updateSubscription(index: Int, name: String, url: String) {
        viewModelScope.launch {
            metaStore.updateSubscription(index, name, url)
        }
    }

    fun removeSubscription(index: Int) {
        viewModelScope.launch {
            metaStore.removeSubscription(index)
        }
    }
}
