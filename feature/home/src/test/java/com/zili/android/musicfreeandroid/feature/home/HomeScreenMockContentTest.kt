package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchorPatterns
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
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
    fun `home screen content renders mock rows instead of empty state`() {
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
                    onOpenMineSheet = {},
                    onOpenStarredSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("暂无歌单").assertDoesNotExist()
        composeRule.onNodeWithTag(
            FidelityAnchorPatterns.mineSheetItem("mock-mine-liked"),
        ).assertExists()
    }
}
