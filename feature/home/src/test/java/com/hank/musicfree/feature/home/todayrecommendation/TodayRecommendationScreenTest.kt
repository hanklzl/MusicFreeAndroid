package com.hank.musicfree.feature.home.todayrecommendation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TodayRecommendationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `content shows title platform reason and opens selected sheet`() {
        val opened = mutableListOf<String>()
        val item = RecommendedSheet(sheet(), "因为你常听摇滚", 88.0)
        composeRule.setContent {
            MusicFreeTheme {
                TodayRecommendationContent(
                    state = TodayRecommendationUiState(
                        items = listOf(item),
                        loading = false,
                        confidence = ProfileConfidence.ESTABLISHED,
                    ),
                    onRetry = {},
                    onOpenPluginList = {},
                    onOpenSheet = { opened += it.key },
                )
            }
        }

        composeRule.onNode(hasText("摇滚现场") and hasClickAction()).assertIsDisplayed().performClick()
        composeRule.onNode(hasText("qq")).assertIsDisplayed()
        composeRule.onNode(hasText("因为你常听摇滚")).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("qq:sheet-1"), opened) }
    }

    @Test
    fun `refreshing keeps old recommendation visible`() {
        composeRule.setContent {
            MusicFreeTheme {
                TodayRecommendationContent(
                    state = TodayRecommendationUiState(
                        items = listOf(RecommendedSheet(sheet(), "热门推荐", 20.0)),
                        loading = false,
                        refreshing = true,
                    ),
                    onRetry = {},
                    onOpenPluginList = {},
                    onOpenSheet = {},
                )
            }
        }

        composeRule.onNode(hasText("摇滚现场")).assertIsDisplayed()
        composeRule.onNode(hasText("正在刷新，当前推荐会保留到新结果生成")).assertIsDisplayed()
    }

    @Test
    fun `no plugins offers plugin installation action`() {
        var opened = 0
        composeRule.setContent {
            MusicFreeTheme {
                TodayRecommendationContent(
                    state = TodayRecommendationUiState(loading = false, noPlugins = true),
                    onRetry = {},
                    onOpenPluginList = { opened++ },
                    onOpenSheet = {},
                )
            }
        }

        composeRule.onNode(hasText("去安装插件") and hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    private fun sheet() = MusicSheetItemBase(
        id = "sheet-1",
        platform = "qq",
        title = "摇滚现场",
        artist = null,
        description = null,
        coverImg = null,
        artwork = null,
        worksNum = 20,
        raw = mapOf("id" to "sheet-1"),
    )
}
