package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `install default subscription ignores re-entry while loading`() = runBlocking {
        val appPreferences = createAppPreferences()
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val viewModel = SettingsViewModel(pluginManager, appPreferences)
        val installStateField = SettingsViewModel::class.java.getDeclaredField("_installState")
        installStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = installStateField.get(viewModel) as MutableStateFlow<InstallState>
        stateFlow.value = InstallState.Loading

        viewModel.installDefaultSubscription()

        verify(pluginManager, never()).installFromSubscriptionUrl(any())
        assertTrue(viewModel.installState.value is InstallState.Loading)
    }

    @Test
    fun `install from url ignores re-entry while loading`() = runBlocking {
        val appPreferences = createAppPreferences()
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val viewModel = SettingsViewModel(pluginManager, appPreferences)
        val installStateField = SettingsViewModel::class.java.getDeclaredField("_installState")
        installStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = installStateField.get(viewModel) as MutableStateFlow<InstallState>
        stateFlow.value = InstallState.Loading

        viewModel.installFromUrl("https://example.com/plugin.js")

        verify(pluginManager, never()).installFromUrl(any(), any())
        assertEquals(InstallState.Loading, viewModel.installState.value)
    }

    @Test
    fun `storage access state is unconfigured by default`() = runBlocking {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `set storage directory persists selected tree uri`() = runBlocking {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.setStorageDirectory(treeUri)

        assertEquals(treeUri, appPreferences.storageDirectoryUri.first { it == treeUri })
    }

    private fun createAppPreferences(): AppPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tmpFolder.newFile("settings-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }

    private suspend fun createViewModel(appPreferences: AppPreferences): SettingsViewModel {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        return SettingsViewModel(pluginManager, appPreferences)
    }
}
