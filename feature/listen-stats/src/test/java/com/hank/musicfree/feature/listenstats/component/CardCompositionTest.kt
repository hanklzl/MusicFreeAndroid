package com.hank.musicfree.feature.listenstats.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.data.db.dao.TopArtistRow
import com.hank.musicfree.data.db.dao.TopSongRow
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CardCompositionTest {

    @get:Rule val composeRule = createComposeRule()

    // ── Task 18 ──────────────────────────────────────────────────────────────

    @Test fun hero_renders_scope_label() {
        composeRule.setContent {
            MusicFreeTheme {
                HeroTotalDurationCard(totalSeconds = 3661, scopeLabel = "本周累计")
            }
        }
        composeRule.onNodeWithText("本周累计").assertIsDisplayed()
        composeRule.onNodeWithText(" 小时 ").assertIsDisplayed()
        composeRule.onNodeWithText(" 分钟").assertIsDisplayed()
    }

    @Test fun segmented_active_callback_fires() {
        var lastScope: TimeScope? = null
        composeRule.setContent {
            MusicFreeTheme {
                TimeScopeSegmented(current = TimeScope.WEEK, onChange = { lastScope = it })
            }
        }
        composeRule.onNodeWithText("月").performClick()
        assert(lastScope == TimeScope.MONTH)
    }

    @Test fun secondaryKpi_clicks_dispatch_separately() {
        var songsClicked = false
        var artistsClicked = false
        composeRule.setContent {
            MusicFreeTheme {
                SecondaryKpiRow(
                    distinctSongs = 147, distinctArtists = 52,
                    onSongsClick = { songsClicked = true },
                    onArtistsClick = { artistsClicked = true },
                )
            }
        }
        composeRule.onNodeWithText("听过的歌曲").performClick()
        assert(songsClicked && !artistsClicked)
    }

    @Test fun onboarding_renders_when_firstEventDate_present() {
        composeRule.setContent {
            MusicFreeTheme {
                OnboardingHint(LocalDate.of(2026, 5, 15))
            }
        }
        composeRule.onNodeWithText("开始统计于 2026 年 5 月 15 日").assertIsDisplayed()
    }

    @Test fun onboarding_hidden_when_null() {
        composeRule.setContent {
            MusicFreeTheme {
                OnboardingHint(null)
            }
        }
        composeRule.onAllNodesWithText("开始统计于", substring = true).assertCountEquals(0)
    }

    // ── Task 19 ──────────────────────────────────────────────────────────────

    @Test fun topArtists_clickSendsArtistName() {
        var clicked: String? = null
        composeRule.setContent {
            MusicFreeTheme {
                TopArtistsCard(
                    rows = listOf(TopArtistRow("周杰伦", 87, 28, 90000)),
                    onSeeAll = {}, onRowClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("周杰伦").performClick()
        assert(clicked == "周杰伦")
    }

    @Test fun streak_renders_unit_suffix() {
        composeRule.setContent {
            MusicFreeTheme {
                StreakDiscoveryRow(
                    streakDays = 14, maxStreak = 42, firstSeenCount = 8,
                    onStreakClick = {}, onDiscoveryClick = {},
                )
            }
        }
        composeRule.onNodeWithText("最长 42 天").assertIsDisplayed()
    }

    // ── Task 20 ──────────────────────────────────────────────────────────────

    @Test fun moreMenu_opensAndDispatchesClear() {
        var cleared = false
        composeRule.setContent {
            MusicFreeTheme {
                MoreMenu(onClear = { cleared = true })
            }
        }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("清除统计数据").performClick()
        assert(cleared)
    }

    @Test fun clearDialog_buttonsDispatch() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            MusicFreeTheme {
                ClearStatsDialog(onConfirm = { confirmed = true }, onDismiss = { dismissed = true })
            }
        }
        composeRule.onNodeWithText("清除").performClick()
        assert(confirmed && !dismissed)
    }

    // ── Listen-Stats 修复:封面 + 移除 plugin 展示 ──

    @Test fun topSongsCard_rowHasCover_andSubtitleNoPlatform() {
        val row = TopSongRow(
            musicId = "m1", platform = "qq",
            title = "情人知己", artistRaw = "叶蒨文",
            album = null, artwork = "https://x/cover.jpg",
            playCount = 3, totalSec = 180,
        )
        composeRule.setContent {
            MusicFreeTheme {
                TopSongsCard(rows = listOf(row), onSeeAll = {}, onRowClick = {})
            }
        }
        composeRule.onAllNodes(hasTestTag("top-song-cover"), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("叶蒨文").assertIsDisplayed()
        composeRule.onAllNodesWithText("qq", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
    }

    @Test fun songDetailRow_hasCover_andSubtitleNoPlatform() {
        val song = ListenedSong(
            musicId = "m1", platform = "qq",
            title = "情人知己", artistRaw = "叶蒨文",
            album = null, artwork = "https://x/cover.jpg",
            firstSeenMs = 0, lastSeenMs = 0,
            playCount = 3, totalSec = 180,
        )
        composeRule.setContent {
            MusicFreeTheme {
                SongDetailRow(song = song)
            }
        }
        composeRule.onAllNodes(hasTestTag("song-cover"), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("叶蒨文").assertIsDisplayed()
        composeRule.onAllNodesWithText("qq", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
    }
}
