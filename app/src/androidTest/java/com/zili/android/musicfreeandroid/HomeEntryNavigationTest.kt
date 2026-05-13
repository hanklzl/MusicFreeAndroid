package com.zili.android.musicfreeandroid

import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.permissions.requiredNotificationPermission
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
        grantNotificationPermission()
        grantAudioPermissions()
    }

    @Test
    fun searchEntry_opensSearchRootAndFocusesInput() {
        waitForHomeEntry(FidelityAnchors.Home.NavBarSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarSearch).performClick()
        assertTagExists(FidelityAnchors.Screen.SearchRoot)
        waitUntilFocused(FidelityAnchors.Search.Input)
        composeRule.onNodeWithTag(FidelityAnchors.Search.Input, useUnmergedTree = true)
            .assertIsFocused()
    }

    @Test
    fun recommendSheetsEntry_opensRecommendSheetsRoot() {
        waitForHomeEntry(FidelityAnchors.Home.OperationsRecommendSheets)
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsRecommendSheets).performClick()
        assertTagExists(FidelityAnchors.Screen.RecommendSheetsRoot)
    }

    @Test
    fun topListEntry_opensTopListRoot() {
        waitForHomeEntry(FidelityAnchors.Home.OperationsTopList)
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsTopList).performClick()
        assertTagExists(FidelityAnchors.Screen.TopListRoot)
    }

    @Test
    fun historyEntry_opensHistoryRoot() {
        waitForHomeEntry(FidelityAnchors.Home.OperationsHistory)
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsHistory).performClick()
        assertTagExists(FidelityAnchors.Screen.HistoryRoot)
    }

    @Test
    fun localEntry_opensLocalRoot() {
        assertAudioPermissionGranted()
        waitForHomeEntry(FidelityAnchors.Home.OperationsLocalMusic)
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsLocalMusic).performClick()
        assertTagExists(FidelityAnchors.Screen.LocalRoot)
    }

    @Test
    fun settingsBasicEntry_opensBasicSettingsRoot() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSettingsBasic)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        assertTagExists(FidelityAnchors.Settings.BasicRoot)
    }

    @Test
    fun settingsPluginEntry_opensPluginListRoot() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSettingsPlugin)
        assertTagExists(FidelityAnchors.Screen.PluginListRoot)
    }

    @Test
    fun settingsThemeEntry_exposesSettingsThemeAnchor() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSettingsTheme)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        assertTagExists(FidelityAnchors.Settings.ThemeRoot)
        assertSettingsFallbackEntry(FidelityAnchors.Settings.ThemeEntry)
    }

    @Test
    fun scheduleCloseEntry_opensTimingClosePanel() {
        openDrawerDestination(FidelityAnchors.Home.DrawerOtherScheduleClose)
        assertTagExists(FidelityAnchors.Panel.TimingCloseRoot)
    }

    @Test
    fun backupEntry_exposesSettingsBackupAnchor() {
        openDrawerDestination(FidelityAnchors.Home.DrawerOtherBackup)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        assertTagExists(FidelityAnchors.Settings.BackupRoot)
        assertSettingsFallbackEntry(FidelityAnchors.Settings.BackupEntry)
    }

    @Test
    fun permissionsEntry_opensPermissionsRoot() {
        openDrawerDestination(FidelityAnchors.Home.DrawerOtherPermissions)
        assertTagExists(FidelityAnchors.Screen.PermissionsRoot)
    }

    @Test
    fun checkUpdateEntry_opensUpdateCheckDialog() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSoftwareCheckUpdate)
        assertTagExists(FidelityAnchors.Dialog.UpdateCheckRoot)
    }

    @Test
    fun aboutEntry_exposesSettingsAboutAnchor() {
        openDrawerDestination(FidelityAnchors.Home.DrawerSoftwareAbout)
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        assertTagExists(FidelityAnchors.Settings.AboutRoot)
        assertSettingsFallbackEntry(FidelityAnchors.Settings.AboutEntry)
    }

    private fun openDrawerDestination(destinationTag: String) {
        waitForHomeEntry(FidelityAnchors.Home.NavBarMenu)
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
        composeRule.onNodeWithTag(destinationTag).performClick()
    }

    private fun waitForHomeEntry(entryTag: String) {
        assertTagExists(FidelityAnchors.Screen.HomeRoot)
        assertTagExists(entryTag)
    }

    private fun assertSettingsFallbackEntry(tag: String) {
        assertTagExists(FidelityAnchors.Screen.SettingsRoot)
        scrollToNode(tag)
        assertTagExists(tag)
    }

    private fun scrollToNode(tag: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitUntilFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.getOrElseNullable(SemanticsProperties.Focused) { null } == true
                }
        }
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
        val permission = requiredAudioPermission()
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        assertAudioPermissionGranted()
    }

    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permission = requiredNotificationPermission()
        if (permission == null) return
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        assertNotificationPermissionGranted()
    }

    private fun assertNotificationPermissionGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = requiredNotificationPermission() ?: return
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before home entry navigation tests." }
    }

    private fun assertAudioPermissionGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = requiredAudioPermission()
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before local entry navigation test." }
    }
}
