package com.zili.android.musicfreeandroid.feature.settings.fileselector

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.feature.settings.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

        val state = viewModel.uiState.first { it.selectedDirectory?.treeUri == treeUri }
        assertTrue(state.isConfigured)
        assertEquals("MusicFree", state.selectedDirectory?.displayName)
    }

    @Test
    fun `onDirectorySelected replaces previous selected directory`() = runBlocking {
        val appPreferences = createAppPreferences()
        val viewModel = FileSelectorLiteViewModel(appPreferences)
        val firstTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"
        val secondTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        viewModel.onDirectorySelected(firstTreeUri)
        viewModel.onDirectorySelected(secondTreeUri)

        val state = viewModel.uiState.first { it.selectedDirectory?.treeUri == secondTreeUri }
        assertTrue(state.isConfigured)
        assertEquals("Download", state.selectedDirectory?.displayName)
    }

    private fun createAppPreferences(): AppPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tmpFolder.newFile("file-selector-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }
}
