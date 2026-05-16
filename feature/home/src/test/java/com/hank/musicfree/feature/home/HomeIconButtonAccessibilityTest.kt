package com.hank.musicfree.feature.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.home.component.HomeNavBar
import com.hank.musicfree.feature.home.sheets.HomeSheetsHeader
import com.hank.musicfree.feature.home.sheets.HomeSheetTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeIconButtonAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `icon only homepage controls expose button role and minimum touch target`() {
        composeRule.setContent {
            MusicFreeTheme {
                androidx.compose.foundation.layout.Column {
                    HomeNavBar(
                        searchPlaceholder = "点击这里开始搜索",
                        onOpenMenu = {},
                        onOpenSearch = {},
                    )
                    HomeSheetsHeader(
                        uiModel = HomePlaylistSectionUiModel(
                            selectedTab = HomeSheetTab.Mine,
                            mineCount = 4,
                            starredCount = 4,
                            rows = emptyList(),
                        ),
                        onSelectTab = {},
                        onCreateSheetClick = {},
                        onImportSheetClick = {},
                    )
                }
            }
        }

        listOf(
            FidelityAnchors.Home.NavBarMenu,
            FidelityAnchors.Home.SheetsCreate,
            FidelityAnchors.Home.SheetsImport,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assert(
                    androidx.compose.ui.test.SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        Role.Button,
                    ),
                )
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }
}
