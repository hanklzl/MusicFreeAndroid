package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import com.zili.android.musicfreeandroid.feature.settings.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PluginListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(): PluginListViewModel {
        val metaStore = mock<PluginMetaStore> {
            on { disabledPlugins } doReturn MutableStateFlow(emptySet())
            on { pluginOrder } doReturn MutableStateFlow(emptyList())
            on { alternativePlugins } doReturn MutableStateFlow(emptyMap())
            on { subscriptions } doReturn MutableStateFlow(emptyList())
        }
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList<LoadedPlugin>())
            on { pluginMetaStore } doReturn metaStore
        }
        return PluginListViewModel(pluginManager)
    }

    @Test
    fun `install state starts as Idle`() {
        val viewModel = createViewModel()
        assertEquals(PluginOperationUiState.Idle, viewModel.operationState.value)
    }

    @Test
    fun `plugin items starts empty`() = runTest {
        val viewModel = createViewModel()
        assertEquals(emptyList<PluginUiItem>(), viewModel.pluginItems.value)
    }

    @Test
    fun `reset install state sets to Idle`() = runTest {
        val viewModel = createViewModel()
        viewModel.resetInstallState()
        assertEquals(PluginOperationUiState.Idle, viewModel.operationState.value)
    }
}
