package com.hank.musicfree.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchorPatterns
import com.hank.musicfree.feature.home.sheets.HomeSheetTab
import com.hank.musicfree.feature.home.sheets.HomeSheetUiModel
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateState
import com.hank.musicfree.updater.downloader.UpdateDownloadManager
import com.hank.musicfree.updater.installer.ApkInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeScreenMockContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fakeChecker: UpdateChecker = mock<UpdateChecker>().also {
        whenever(it.state).thenReturn(MutableStateFlow(UpdateState.Idle))
    }
    private val fakeDownloadManager: UpdateDownloadManager = mock()
    private val fakeInstaller: ApkInstaller = mock()

    private val fakeMineRows = listOf(
        HomeSheetUiModel(
            id = "mock-mine-liked",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "我喜欢",
            subtitle = "18首",
            coverUri = null,
            isDefault = true,
        ),
        HomeSheetUiModel(
            id = "mock-mine-cloud",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "云端备份",
            subtitle = "32首",
            coverUri = null,
        ),
    )

    @Test
    fun `home screen content renders mock rows instead of empty state and wires mock row click callback`() {
        val openedMineSheetIds = mutableListOf<String>()

        composeRule.setContent {
            MusicFreeTheme {
                HomeScreenContent(
                    state = HomeScreenState(),
                    visualUiModel = buildHomeVisualUiModel(HomeSheetTab.Mine, fakeMineRows),
                    drawerUiModel = buildHomeDrawerUiModel(
                        currentVersion = "1.0.0",
                        scheduleCloseSummary = "",
                    ),
                    currentVersion = "1.0.0",
                    scheduleCloseSummary = "",
                    checker = fakeChecker,
                    downloadManager = fakeDownloadManager,
                    installer = fakeInstaller,
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
                    onOpenStarredAlbum = {},
                    onTrashClick = {},
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

}
