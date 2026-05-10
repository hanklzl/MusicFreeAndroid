package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeSheetsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `header actions are pure callbacks and do not open create playlist dialog`() {
        var createClicks = 0
        var importClicks = 0
        var selectedTab: HomeSheetTab? = null

        composeRule.setContent {
            MusicFreeTheme {
                LazyColumn {
                    homeSheetsSection(
                        uiModel = sampleHomePlaylistSectionUiModel(),
                        onSelectTab = { selectedTab = it },
                        onCreateClick = { createClicks++ },
                        onImportClick = { importClicks++ },
                        onOpenMineSheet = {},
                        onOpenStarredSheet = {},
                        onOpenStarredAlbum = {},
                        onTrashClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsCreate, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsImport, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsStarredTab, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, createClicks)
            assertEquals(1, importClicks)
            assertEquals(HomeSheetTab.Starred, selectedTab)
        }
        // Create dialog currently has no dedicated testTag/anchor, so title text is the most stable
        // observable sentinel to assert that no dialog was opened from header actions.
        composeRule.onAllNodesWithText("新建播放列表").assertCountEquals(0)
    }

    private fun sampleHomePlaylistSectionUiModel() = com.zili.android.musicfreeandroid.feature.home.HomePlaylistSectionUiModel(
        selectedTab = HomeSheetTab.Mine,
        mineCount = 2,
        starredCount = 1,
        rows = listOf(
            HomeSheetUiModel(
                id = "mine-1",
                platform = null,
                tab = HomeSheetTab.Mine,
                title = "我喜欢的音乐",
                subtitle = "12首",
                coverUri = null,
            ),
        ),
    )
}
