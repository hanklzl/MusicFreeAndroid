package com.hank.musicfree.runtime

import android.content.pm.PackageManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.MainActivity
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission
import com.hank.musicfree.core.ui.FidelityAnchors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RuntimeStateRecreateTest {
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
    fun homeSheetTabSurvivesActivityRecreate() {
        assertTagExists(FidelityAnchors.Screen.HomeRoot)
        assertTagExists(FidelityAnchors.Home.SheetsStarredTab)

        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsStarredTab).performClick()
        assertSelected(FidelityAnchors.Home.SheetsStarredTab)

        composeRule.activityRule.scenario.recreate()

        assertTagExists(FidelityAnchors.Screen.HomeRoot)
        assertSelected(FidelityAnchors.Home.SheetsStarredTab)
    }

    private fun assertSelected(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNode(hasTestTag(tag).and(isSelected())).fetchSemanticsNode()
                true
            }.getOrElse { false }
        }
        composeRule.onNode(hasTestTag(tag).and(isSelected())).assertExists()
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
        val context = instrumentation.targetContext
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before runtime recreate test." }
    }

    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permission = requiredNotificationPermission() ?: return
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        val context = instrumentation.targetContext
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before runtime recreate test." }
    }
}
