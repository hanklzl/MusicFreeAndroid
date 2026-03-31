package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationErrorCode
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationFailure
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationResult
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        dataStoreScopes.forEach { scope ->
            scope.cancel()
        }
        dataStoreScopes.clear()
    }

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
    fun `install from file returns success state when plugin manager installs plugin`() = runBlocking {
        val appPreferences = createAppPreferences()
        val installedPlugin = mock<LoadedPlugin>()
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        whenever(pluginManager.installFromFile(any())).thenReturn(installedPlugin)

        val viewModel = SettingsViewModel(pluginManager, appPreferences)
        val pluginFile = tmpFolder.newFile("local-plugin.js")

        viewModel.installFromFile(pluginFile.absolutePath)

        assertTrue(viewModel.installState.value is InstallState.Success)
    }

    @Test
    fun `update plugin returns failure state when source missing`() = runBlocking {
        val appPreferences = createAppPreferences()
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        whenever(pluginManager.updatePlugin("demo")).thenReturn(
            PluginOperationResult(
                operationType = PluginOperationType.UPDATE_SINGLE,
                targetPlugins = listOf("demo"),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = "demo",
                        errorCode = PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
                        message = "没有更新源",
                    ),
                ),
                startedAtEpochMs = 1L,
                finishedAtEpochMs = 2L,
            ),
        )

        val viewModel = SettingsViewModel(pluginManager, appPreferences)

        viewModel.updatePlugin("demo")

        assertTrue(viewModel.installState.value is InstallState.Error)
        assertEquals("没有更新源", (viewModel.installState.value as InstallState.Error).message)
    }

    @Test
    fun `update all plugins returns success state with summary`() = runBlocking {
        val appPreferences = createAppPreferences()
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        whenever(pluginManager.updateAllPlugins()).thenReturn(
            PluginOperationResult(
                operationType = PluginOperationType.UPDATE_ALL,
                targetPlugins = listOf("a", "b"),
                successCount = 2,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = 1L,
                finishedAtEpochMs = 2L,
            ),
        )

        val viewModel = SettingsViewModel(pluginManager, appPreferences)

        viewModel.updateAllPlugins()

        assertTrue(viewModel.installState.value is InstallState.Success)
        assertTrue(
            (viewModel.installState.value as InstallState.Success).message.contains("成功 2"),
        )
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
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
