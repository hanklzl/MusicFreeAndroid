package com.zili.android.musicfreeandroid

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeEntryNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        grantAudioPermissions()
    }

    @Test
    fun searchEntry_opensSearchRoot() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarSearch).performClick()
        assertTagExists(FidelityAnchors.Screen.SearchRoot)
    }

    @Test
    fun recommendSheetsEntry_opensRecommendSheetsRoot() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsRecommendSheets).performClick()
        assertTagExists(FidelityAnchors.Screen.RecommendSheetsRoot)
    }

    @Test
    fun topListEntry_opensTopListRoot() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsTopList).performClick()
        assertTagExists(FidelityAnchors.Screen.TopListRoot)
    }

    @Test
    fun historyEntry_opensHistoryRoot() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsHistory).performClick()
        assertTagExists(FidelityAnchors.Screen.HistoryRoot)
    }

    @Test
    fun localEntry_opensLocalRoot() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsLocalMusic).performClick()
        assertTagExists(FidelityAnchors.Screen.LocalRoot)
    }

    @Test
    fun settingsEntry_opensSettingsRoot() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSettings)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
    }

    @Test
    fun permissionsEntry_opensPermissionsRoot() {
        openDrawerDestination(FidelityAnchors.Home.DrawerPermissions)
        assertTagExists(FidelityAnchors.Screen.PermissionsRoot)
    }

    @Test
    fun pluginManagementEntry_exposesSettingsPluginAnchor() {
        openDrawerDestination(FidelityAnchors.Home.DrawerPluginManagement)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        assertTagExists(FidelityAnchors.Settings.PluginManagementEntry)
    }

    private fun openDrawerDestination(destinationTag: String) {
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
        composeRule.onNodeWithTag(destinationTag).performClick()
    }

    private fun assertTagExists(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrElse { false }
        }
        composeRule.onNodeWithTag(tag).assertExists()
    }

    private fun grantAudioPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ).forEach { permission ->
            runCatching {
                instrumentation.uiAutomation
                    .executeShellCommand("pm grant $packageName $permission")
                    .close()
            }
        }
    }
}
