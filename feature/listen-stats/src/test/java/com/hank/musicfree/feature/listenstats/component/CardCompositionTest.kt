package com.hank.musicfree.feature.listenstats.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.data.db.dao.TopArtistRow
import com.hank.musicfree.data.db.dao.TopSongRow
import com.hank.musicfree.data.repository.listenstats.model.DailyBucket
import com.hank.musicfree.data.repository.listenstats.model.HourBucket
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

    // ── 每日时长长按提示 + 听歌时段刻度 ──

    @Test fun formatListenDuration_boundaries() {
        assert(formatListenDuration(0) == "0秒")
        assert(formatListenDuration(45) == "45秒")
        assert(formatListenDuration(60) == "1分钟")
        assert(formatListenDuration(59 * 60) == "59分钟")
        assert(formatListenDuration(83 * 60) == "1小时23分钟")
        assert(formatListenDuration(3600) == "1小时")
        assert(formatListenDuration(3661) == "1小时1分钟")
        assert(formatListenDuration(-5) == "0秒")
    }

    @Test fun dailyBars_longPress_showsDateAndDurationTooltip() {
        val day = LocalDate.of(2026, 5, 30).toEpochDay()
        composeRule.setContent {
            MusicFreeTheme {
                DailyBarsCard(daily = listOf(DailyBucket(dayEpochDay = day, seconds = 3661)))
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodesWithTag("daily-bar", useUnmergedTree = true).onFirst().performTouchInput { longClick() }
        // 推进虚拟时钟越过长按阈值并让气泡渲染，但停在自动消失之前
        composeRule.mainClock.advanceTimeBy(800)
        composeRule.onNodeWithText("5月30日 · 1小时1分钟", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun hourBars_longPress_showsHourAndDurationTooltip() {
        composeRule.setContent {
            MusicFreeTheme {
                HourCard(buckets = listOf(HourBucket(hourOfDay = 14, seconds = 1380)))
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodesWithTag("hour-bar", useUnmergedTree = true)[14].performTouchInput { longClick() }
        composeRule.mainClock.advanceTimeBy(800)
        composeRule.onNodeWithText("14:00 · 23分钟", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun hourCard_rendersEvenHourAxisLabels_hidesOddHours() {
        composeRule.setContent {
            MusicFreeTheme {
                HourCard(buckets = listOf(HourBucket(hourOfDay = 14, seconds = 1380)))
            }
        }
        // 每隔两小时一个刻度：偶数小时可见
        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule.onNodeWithText("6").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("22").assertIsDisplayed()
        // 奇数小时不渲染刻度
        composeRule.onAllNodesWithText("7").assertCountEquals(0)
        composeRule.onAllNodesWithText("13").assertCountEquals(0)
    }
}
