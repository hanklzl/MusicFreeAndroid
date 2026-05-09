package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        dataStoreScopes.forEach { scope ->
            scope.cancel()
        }
        dataStoreScopes.clear()
    }

    @Test
    fun `storage access state is unconfigured by default`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.setStorageDirectory(treeUri)
        // Drains scheduler-tracked work; with UnconfinedTestDispatcher launches
        // already run eagerly, but the explicit drain documents the intent.
        advanceUntilIdle()

        val persisted = appPreferences.storageDirectoryUri.first()
        assertEquals(treeUri, persisted)
    }

    @Test
    fun `basic settings state exposes default runtime-backed preferences`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.basicSettingsUiState.value

        assertEquals(3, state.maxDownload)
        assertEquals(PlayQuality.STANDARD, state.defaultDownloadQuality)
        assertEquals(false, state.useCellularDownload)
        assertEquals(true, state.lyricAutoSearchEnabled)
        assertTrue(!state.storageAccessState.isConfigured)
    }

    @Test
    fun `basic settings setters update collected runtime-backed state and preferences`() =
        runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences)
        val job = backgroundScope.launch { viewModel.basicSettingsUiState.collect {} }
        advanceUntilIdle()

        viewModel.setMaxDownload(7)
        viewModel.setDefaultDownloadQuality(PlayQuality.SUPER)
        viewModel.setUseCellularDownload(true)
        viewModel.setLyricAutoSearchEnabled(false)
        advanceUntilIdle()

        val state = viewModel.basicSettingsUiState.value
        assertEquals(7, state.maxDownload)
        assertEquals(PlayQuality.SUPER, state.defaultDownloadQuality)
        assertEquals(true, state.useCellularDownload)
        assertEquals(false, state.lyricAutoSearchEnabled)
        assertTrue(!state.storageAccessState.isConfigured)
        assertEquals(7, appPreferences.maxDownload.first())
        assertEquals(PlayQuality.SUPER, appPreferences.defaultDownloadQuality.first())
        assertEquals(true, appPreferences.useCellularDownload.first())
        assertEquals(false, appPreferences.lyricAutoSearchEnabled.first())
        job.cancel()
    }

    private fun createAppPreferences(): AppPreferences {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("settings-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }

    private fun createViewModel(appPreferences: AppPreferences): SettingsViewModel {
        return SettingsViewModel(appPreferences)
    }
}
