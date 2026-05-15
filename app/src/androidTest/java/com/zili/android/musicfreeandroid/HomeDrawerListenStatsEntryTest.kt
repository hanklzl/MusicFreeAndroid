package com.zili.android.musicfreeandroid

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class HomeDrawerListenStatsEntryTest {

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
    fun openDrawer_clickListenStats_navigatesToListenStatsScreen() {
        // 等待首页根节点出现
        assertTagExists(FidelityAnchors.Screen.HomeRoot)
        // 等待菜单按钮出现
        assertTagExists(FidelityAnchors.Home.NavBarMenu)
        // 点击菜单按钮打开抽屉
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        // 等待抽屉根节点出现
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
        // 等待「听歌足迹」抽屉项出现
        assertTagExists(FidelityAnchors.Home.DrawerMeListenStats)
        // 点击「听歌足迹」
        composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerMeListenStats).performClick()
        composeRule.waitForIdle()
        // 验证 AppBar title 为「听歌足迹」
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("听歌足迹")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("听歌足迹").assertIsDisplayed()
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
        ) { "Expected $permission to be granted before listen-stats navigation test." }
    }

    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permission = requiredNotificationPermission() ?: return
        instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        val context = instrumentation.targetContext
        check(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        ) { "Expected $permission to be granted before listen-stats navigation test." }
    }
}
