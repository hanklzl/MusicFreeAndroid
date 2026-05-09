package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.component.HomeDrawerContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeDrawerContentInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `drawer title starts below the status bar inset`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    HomeDrawerContent(
                        uiModel = buildHomeDrawerUiModel(
                            currentLanguage = "中文",
                            currentVersion = "1.0.0",
                            scheduleCloseSummary = "",
                        ),
                        onEntryClick = {},
                        statusBarTopPadding = 24.dp,
                    )
                }
            }
        }

        val titleTop = composeRule
            .onNodeWithTag(FidelityAnchors.Home.DrawerTitle)
            .getUnclippedBoundsInRoot()
            .top
        val statusBarTop = 24.dp

        assertTrue(
            "Expected drawer title to start at or below the status bar inset. " +
                "statusBarTop=$statusBarTop titleTop=$titleTop",
            titleTop >= statusBarTop,
        )
    }
}
