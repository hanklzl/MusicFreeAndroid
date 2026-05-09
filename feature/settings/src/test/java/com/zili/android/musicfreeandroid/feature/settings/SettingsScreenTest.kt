package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
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

    @Test
    fun `plugin type renders plugin root and entry action`() {
        var pluginClicks = 0
        setContent(
            type = SettingsType.Plugin,
            onNavigateToPluginList = { pluginClicks++ },
        )

        composeRule.onNodeWithTag(FidelityAnchors.Settings.PluginRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.PluginManagementEntry).assertIsDisplayed()
        composeRule.onNodeWithText("进入").performClick()

        composeRule.runOnIdle {
            assertEquals(1, pluginClicks)
        }
    }

    private fun setContent(
        type: SettingsType,
        onNavigateToPluginList: () -> Unit = {},
    ) {
        val viewModel = SettingsViewModel(createAppPreferences())
        composeRule.setContent {
            MusicFreeTheme {
                SettingsScreen(
                    type = type,
                    onBack = {},
                    onNavigateToPermissions = {},
                    onNavigateToFileSelector = {},
                    onNavigateToPluginList = onNavigateToPluginList,
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
}
