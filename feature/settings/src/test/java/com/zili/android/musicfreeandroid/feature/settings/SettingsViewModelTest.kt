package com.zili.android.musicfreeandroid.feature.settings

import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `install default subscription ignores re-entry while loading`() {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        runBlocking {
            whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        }

        val viewModel = SettingsViewModel(pluginManager)
        val installStateField = SettingsViewModel::class.java.getDeclaredField("_installState")
        installStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = installStateField.get(viewModel) as MutableStateFlow<InstallState>
        stateFlow.value = InstallState.Loading

        viewModel.installDefaultSubscription()

        runBlocking {
            verify(pluginManager, never()).installFromSubscriptionUrl(any())
        }
        assertTrue(viewModel.installState.value is InstallState.Loading)
    }

    @Test
    fun `install from url ignores re-entry while loading`() {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        runBlocking {
            whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        }

        val viewModel = SettingsViewModel(pluginManager)
        val installStateField = SettingsViewModel::class.java.getDeclaredField("_installState")
        installStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = installStateField.get(viewModel) as MutableStateFlow<InstallState>
        stateFlow.value = InstallState.Loading

        viewModel.installFromUrl("https://example.com/plugin.js")

        runBlocking {
            verify(pluginManager, never()).installFromUrl(any(), any())
        }
        assertEquals(InstallState.Loading, viewModel.installState.value)
    }
}
