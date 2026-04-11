package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchorPatterns
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeScreenMockContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `home screen content renders mock rows instead of empty state and wires mock row click callback`() {
        val openedMineSheetIds = mutableListOf<String>()

        composeRule.setContent {
            MusicFreeTheme {
                HomeScreenContent(
                    state = HomeScreenState(),
                    visualUiModel = buildHomeVisualUiModel(HomeSheetTab.Mine),
                    drawerUiModel = buildHomeDrawerUiModel(
                        currentLanguage = "中文",
                        currentVersion = "1.0.0",
                        scheduleCloseSummary = "",
                    ),
                    currentLanguage = "中文",
                    currentVersion = "1.0.0",
                    scheduleCloseSummary = "",
                    onDrawerEntryClick = {},
                    onNavigateToSearch = {},
                    onNavigateToRecommendSheets = {},
                    onNavigateToTopList = {},
                    onNavigateToHistory = {},
                    onNavigateToLocal = {},
                    onSelectTab = {},
                    onCreateClick = {},
                    onImportClick = {},
                    onOpenMineSheet = { openedMineSheetIds += it },
                    onOpenStarredSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithText("暂无歌单").assertCountEquals(0)
        composeRule.onAllNodesWithTag(FidelityAnchorPatterns.mineSheetItem("mock-mine-liked")).assertCountEquals(1)
        composeRule.onNodeWithTag(FidelityAnchorPatterns.mineSheetItem("mock-mine-liked")).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("mock-mine-liked"), openedMineSheetIds)
        }
    }

    @Test
    fun `home screen ignores mock mine row navigation at container level`() {
        var playlistDetailNavigations = 0

        composeRule.setContent {
            MusicFreeTheme {
                HomeScreen(
                    onNavigateToSearch = {},
                    onNavigateToRecommendSheets = {},
                    onNavigateToHistory = {},
                    onNavigateToLocal = {},
                    onNavigateToSettings = {},
                    onNavigateToPermissions = {},
                    onNavigateToTopList = {},
                    onNavigateToPlaylistDetail = { playlistDetailNavigations++ },
                    homeSystemActionHandler = object : HomeSystemActionHandler {
                        override fun backToDesktop() = Unit
                        override suspend fun exitApp() = Unit
                    },
                )
            }
        }

        composeRule.onNodeWithTag(FidelityAnchorPatterns.mineSheetItem("mock-mine-liked")).performClick()

        composeRule.runOnIdle {
            assertEquals(0, playlistDetailNavigations)
        }
    }
}
