package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    fun `storage access state is unconfigured by default`() = runBlocking {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    // TODO(deps-bump-2026-05): same latent runBlocking/Flow.first race as
    // FileSelectorLiteViewModelTest's "persists selected tree uri". See that file for details.
    @Test
    @Ignore("Hangs in full settings test run; tracked alongside FileSelectorLiteViewModelTest")
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

    private fun createViewModel(appPreferences: AppPreferences): SettingsViewModel {
        return SettingsViewModel(appPreferences)
    }
}
