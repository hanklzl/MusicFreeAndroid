package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.component.HomeNavBar
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsHeader
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsUiState
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
                        onOpenMenu = {},
                        onOpenSearch = {},
                    )
                    HomeSheetsHeader(
                        uiState = HomeSheetsUiState(),
                        onSelectTab = {},
                        onRequestCreate = {},
                        onImportSheet = {},
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
