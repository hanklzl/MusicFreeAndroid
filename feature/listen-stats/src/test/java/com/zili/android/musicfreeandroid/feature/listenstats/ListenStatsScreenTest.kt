package com.zili.android.musicfreeandroid.feature.listenstats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.Distribution
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.emptySnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ListenStatsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_snapshot_renders_appbar_and_hero_and_onboarding() {
        val state = ListenStatsScreenState(
            scope = TimeScope.WEEK,
            anchor = LocalDate.of(2026, 5, 13),
            windowLabel = "5/11 – 5/17",
            scopeLabel = "本周累计",
            firstEventDate = LocalDate.of(2026, 5, 10),
            snapshot = emptySnapshot(),
        )
        composeRule.setContent {
            MusicFreeTheme {
                StatelessListenStatsScaffold(
                    state = state,
                    onBack = {},
                    onScopeChange = {},
                    onAnchorPrev = {},
                    onAnchorNext = {},
                    onNavigateToDetail = { _, _, _, _ -> },
                    onClearRequested = {},
                    onClearConfirmed = {},
                    onClearDismissed = {},
                    onBarClick = {},
                    onHeatmapClick = {},
                )
            }
        }
        composeRule.onNodeWithText("听歌足迹").assertIsDisplayed()
        composeRule.onNodeWithText("本周累计").assertIsDisplayed()
        composeRule.onNodeWithText("开始统计于 2026 年 5 月 10 日").assertIsDisplayed()
    }

    @Test fun coverage_below_30_hides_language_and_genre_cards() {
        val state = ListenStatsScreenState(
            scope = TimeScope.WEEK,
            anchor = LocalDate.of(2026, 5, 13),
            windowLabel = "5/11 – 5/17",
            scopeLabel = "本周累计",
            firstEventDate = null,
            snapshot = emptySnapshot().copy(
                languageDistribution = Distribution(emptyList(), 0.10f),
                genreDistribution = Distribution(emptyList(), 0.05f),
            ),
        )
        composeRule.setContent {
            MusicFreeTheme {
                StatelessListenStatsScaffold(
                    state = state,
                    onBack = {},
                    onScopeChange = {},
                    onAnchorPrev = {},
                    onAnchorNext = {},
                    onNavigateToDetail = { _, _, _, _ -> },
                    onClearRequested = {},
                    onClearConfirmed = {},
                    onClearDismissed = {},
                    onBarClick = {},
                    onHeatmapClick = {},
                )
            }
        }
        // 不应出现 "语言分布" / "音乐风格" 标题
        composeRule.onAllNodesWithText("语言分布").assertCountEquals(0)
        composeRule.onAllNodesWithText("音乐风格").assertCountEquals(0)
    }
}
