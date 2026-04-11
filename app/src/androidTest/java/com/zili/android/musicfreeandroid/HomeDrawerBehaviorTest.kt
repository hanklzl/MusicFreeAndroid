package com.zili.android.musicfreeandroid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.HomeDrawerAction
import com.zili.android.musicfreeandroid.feature.home.HomeScreenContent
import com.zili.android.musicfreeandroid.feature.home.HomeScreenState
import com.zili.android.musicfreeandroid.feature.home.HomeSystemActionHandler
import com.zili.android.musicfreeandroid.feature.home.buildHomeDrawerUiModel
import com.zili.android.musicfreeandroid.feature.home.buildHomeVisualUiModel
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDrawerBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: HomeScreenState
    private lateinit var fakeSystemActionHandler: FakeHomeSystemActionHandler

    @Before
    fun setUp() {
        state = HomeScreenState()
        fakeSystemActionHandler = FakeHomeSystemActionHandler()

        composeRule.setContent {
            MusicFreeTheme {
                HomeScreenContent(
                    state = state,
                    visualUiModel = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine),
                    drawerUiModel = buildHomeDrawerUiModel(
                        currentLanguage = "中文",
                        currentVersion = "1.0.0-test",
                        scheduleCloseSummary = "30 分钟后关闭",
                    ),
                    currentLanguage = "中文",
                    currentVersion = "1.0.0-test",
                    scheduleCloseSummary = "30 分钟后关闭",
                    onDrawerEntryClick = { action ->
                        when (action) {
                            HomeDrawerAction.BackToDesktop -> fakeSystemActionHandler.backToDesktop()
                            HomeDrawerAction.ExitApp -> Unit
                            else -> Unit
                        }
                    },
                    onNavigateToSearch = {},
                    onNavigateToRecommendSheets = {},
                    onNavigateToTopList = {},
                    onNavigateToHistory = {},
                    onNavigateToLocal = {},
                    onSelectTab = {},
                    onCreateClick = {},
                    onImportClick = {},
                    onOpenMineSheet = {},
                    onOpenStarredSheet = {},
                )
            }
        }
    }

    @Test
    fun language_entry_opens_dialog() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerSoftwareLanguage)

        assertTagExists(FidelityAnchors.Dialog.LanguageRoot)
        assertTrue(state.isLanguageDialogVisible)
    }

    @Test
    fun update_entry_opens_dialog() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerSoftwareCheckUpdate)

        assertTagExists(FidelityAnchors.Dialog.UpdateCheckRoot)
        assertTrue(state.isUpdateCheckVisible)
    }

    @Test
    fun scheduleClose_entry_opens_panel() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerOtherScheduleClose)

        assertTagExists(FidelityAnchors.Panel.TimingCloseRoot)
        assertTrue(state.isTimingCloseVisible)
    }

    @Test
    fun backToDesktop_action_invokes_fake_handler() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerActionBackToDesktop)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            fakeSystemActionHandler.backToDesktopCount == 1
        }
    }

    @Test
    fun scrim_click_closes_drawer() {
        openDrawer()

        composeRule.onRoot().performTouchInput {
            click(centerRight)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !state.isDrawerOpen
        }
        assertFalse(state.isDrawerOpen)
    }

    private fun clickDrawerEntry(tag: String) {
        openDrawer()
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun openDrawer() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.isDrawerOpen
        }
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
    }

    private fun assertTagExists(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            }.getOrElse { false }
        }
        composeRule.onNodeWithTag(tag).assertExists()
    }

    private class FakeHomeSystemActionHandler : HomeSystemActionHandler {
        var backToDesktopCount: Int = 0
            private set

        override fun backToDesktop() {
            backToDesktopCount += 1
        }

        override suspend fun exitApp() = Unit
    }
}
