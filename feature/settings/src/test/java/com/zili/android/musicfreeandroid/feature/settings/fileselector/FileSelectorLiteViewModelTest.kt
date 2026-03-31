package com.zili.android.musicfreeandroid.feature.settings.fileselector

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.feature.settings.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSelectorLiteViewModelTest {

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
    fun `ui state is unconfigured by default`() = runBlocking {
        val viewModel = FileSelectorLiteViewModel(createAppPreferences())

        val state = viewModel.uiState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `onDirectorySelected persists selected tree uri`() = runBlocking {
        val appPreferences = createAppPreferences()
        val viewModel = FileSelectorLiteViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.onDirectorySelected(treeUri)

        val persisted = appPreferences.storageDirectoryUri.first { it == treeUri }
        assertEquals(treeUri, persisted)
    }

    @Test
    fun `onDirectorySelected replaces previous selected directory`() = runBlocking {
        val appPreferences = createAppPreferences()
        val viewModel = FileSelectorLiteViewModel(appPreferences)
        val firstTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"
        val secondTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        viewModel.onDirectorySelected(firstTreeUri)
        viewModel.onDirectorySelected(secondTreeUri)

        val persisted = appPreferences.storageDirectoryUri.first { it == secondTreeUri }
        assertEquals(secondTreeUri, persisted)
    }

    private fun createAppPreferences(): AppPreferences {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("file-selector-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }
}
