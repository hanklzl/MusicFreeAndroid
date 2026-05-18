package com.hank.musicfree

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class HomeDrawerTrafficStatsEntryTest {

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
    fun click_traffic_stats_entry_navigates_to_screen() {
        // 等待首页根节点出现
        assertTagExists(FidelityAnchors.Screen.HomeRoot)
        // 等待菜单按钮出现
        assertTagExists(FidelityAnchors.Home.NavBarMenu)
        // 点击菜单按钮打开抽屉
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        // 等待抽屉根节点出现
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
        // 等待「流量统计」抽屉项出现
        assertTagExists(FidelityAnchors.Home.DrawerMeTrafficStats)
        // 点击「流量统计」
        composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerMeTrafficStats).performClick()
        composeRule.waitForIdle()
        // 验证已进入 TrafficStatsScreen
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FidelityAnchors.Screen.TrafficStatsRoot)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FidelityAnchors.Screen.TrafficStatsRoot).assertIsDisplayed()
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
        ) { "Expected $permission to be granted before traffic-stats navigation test." }
    }

    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permission = requiredNotificationPermission() ?: return
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        val context = instrumentation.targetContext
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before traffic-stats navigation test." }
    }
}
