package com.zili.android.musicfreeandroid

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeFidelityHomeStructureTest {

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
    fun home_exposes_root_nav_operations_sheets_and_drawer_opens_from_menu() {
        assertTagDisplayed(FidelityAnchors.Screen.HomeRoot)
        assertTagDisplayed(FidelityAnchors.Home.NavBarRoot)
        assertTagDisplayed(FidelityAnchors.Home.NavBarMenu)
        assertTagDisplayed(FidelityAnchors.Home.OperationsRoot)
        assertTagDisplayed(FidelityAnchors.Home.SheetsRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniPlayPause)
        assertTagDisplayed(FidelityAnchors.Player.MiniQueue)

        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()

        assertTagDisplayed(FidelityAnchors.Home.DrawerRoot)
    }

    @Test
    fun home_content_remains_visible_above_existingMiniPlayer() {
        assertTagDisplayed(FidelityAnchors.Screen.HomeRoot)
        assertTagDisplayed(FidelityAnchors.Home.SheetsRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniPlayPause)
        assertTagDisplayed(FidelityAnchors.Player.MiniQueue)
    }

    private fun assertTagDisplayed(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrElse { false }
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun grantAudioPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permission = requiredAudioPermission()
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        val context = instrumentation.targetContext
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before home fidelity structure test." }
    }
}
