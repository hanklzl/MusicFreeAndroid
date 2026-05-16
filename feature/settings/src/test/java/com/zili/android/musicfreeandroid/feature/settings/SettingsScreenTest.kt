package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        dataStoreScopes.forEach { scope -> scope.cancel() }
        dataStoreScopes.clear()
    }

    @Test
    fun `basic type renders basic settings root`() {
        setContent(type = SettingsType.Basic)

        composeRule.onNodeWithTag(FidelityAnchors.Screen.SettingsRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicRoot).assertIsDisplayed()
    }

    private fun setContent(type: SettingsType) {
        val viewModel = SettingsViewModel(
            appPreferences = createAppPreferences(),
            feedbackLogExporter = FakeFeedbackLogExporter(),
            pluginManager = mock<PluginManager>(),
            cacheCleaner = mock<SettingsCacheCleaner>(),
        )
        composeRule.setContent {
            MusicFreeTheme {
                SettingsScreen(
                    type = type,
                    onBack = {},
                    onNavigateToPermissions = {},
                    onNavigateToFileSelector = {},
                    onNavigateToSetCustomTheme = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun createAppPreferences(): AppPreferences {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("settings-screen-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }

    private inner class FakeFeedbackLogExporter : FeedbackLogExporterContract {
        override suspend fun createPackage(): FeedbackPackage {
            val file = tmpFolder.newFile("settings-screen-feedback.zip")
            return FeedbackPackage(file = file, fileName = file.name, sizeBytes = file.length())
        }

        override suspend fun clearLogs() {
            // no-op
        }

        override suspend fun pruneLogs() {
            // no-op
        }
    }
}
