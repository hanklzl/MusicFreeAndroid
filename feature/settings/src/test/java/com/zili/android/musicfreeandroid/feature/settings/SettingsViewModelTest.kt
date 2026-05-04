package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
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
        advanceUntilIdle()

        val persisted = appPreferences.storageDirectoryUri.first()
        assertEquals(treeUri, persisted)
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
