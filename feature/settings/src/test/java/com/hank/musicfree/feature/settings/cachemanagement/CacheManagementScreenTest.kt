package com.hank.musicfree.feature.settings.cachemanagement

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.data.repository.OnlineCacheQualityRow
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CacheManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listScreenDoesNotShowManualPlatformOrIdInputs() {
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(row("Song", OnlineCacheQualityStatus.Reusable))),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("平台").assertDoesNotExist()
        composeRule.onNodeWithText("歌曲 ID").assertDoesNotExist()
        composeRule.onAllNodesWithText("Song").assertCountEquals(1)
        composeRule.onNodeWithText("kg").assertIsDisplayed()
        composeRule.onAllNodesWithText("可复用").assertCountEquals(2)
    }

    @Test
    fun emptyStateIsVisible() {
        setContent {
            CacheManagementContent(
                state = state(rows = emptyList()),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("暂无在线播放缓存").assertIsDisplayed()
    }

    @Test
    fun rowClickSelectsSong() {
        val song = row("Song", OnlineCacheQualityStatus.Reusable)
        var selected: OnlineCacheSongRow? = null
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(song)),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = { selected = it },
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithTag(cacheRowTag(song)).performClick()

        composeRule.runOnIdle {
            assertEquals(song, selected)
        }
    }

    @Test
    fun selectedRowShowsDetailActions() {
        val selected = row("Song", OnlineCacheQualityStatus.Reusable)
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(selected), selected = selected),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("清理该音质").assertIsDisplayed()
        composeRule.onNodeWithText("清理整首歌在线播放缓存").assertIsDisplayed()
    }

    @Test
    fun clearQualityConfirmationInvokesCallback() {
        val selected = row("Song", OnlineCacheQualityStatus.Reusable)
        var clearedQuality: PlayQuality? = null
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(selected), selected = selected),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = { clearedQuality = it },
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("清理该音质").performClick()
        composeRule.onNodeWithText("清理").performClick()

        composeRule.runOnIdle {
            assertEquals(PlayQuality.STANDARD, clearedQuality)
        }
    }

    @Test
    fun clearSongConfirmationInvokesCallback() {
        val selected = row("Song", OnlineCacheQualityStatus.Reusable)
        var clearSongCount = 0
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(selected), selected = selected),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = { clearSongCount += 1 },
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("清理整首歌在线播放缓存").performClick()
        composeRule.onNodeWithText("清理").performClick()

        composeRule.runOnIdle {
            assertEquals(1, clearSongCount)
        }
    }

    @Test
    fun clearAllConfirmationInvokesCallback() {
        val song = row("Song", OnlineCacheQualityStatus.Reusable)
        var clearAllCount = 0
        setContent {
            CacheManagementContent(
                state = state(rows = listOf(song)),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = { clearAllCount += 1 },
            )
        }

        composeRule.onNodeWithText("清理全部在线播放缓存").performClick()
        composeRule.onNodeWithText("清理").performClick()

        composeRule.runOnIdle {
            assertEquals(1, clearAllCount)
        }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            MusicFreeTheme {
                content()
            }
        }
    }

    private fun state(
        rows: List<OnlineCacheSongRow>,
        selected: OnlineCacheSongRow? = null,
    ) = CacheManagementUiState(
        isLoading = false,
        allRows = rows,
        visibleRows = rows,
        selectedRow = selected,
        summary = CacheManagementSummary(
            songCount = rows.size,
            qualityCount = rows.sumOf { it.qualities.size },
            reusableCount = rows.sumOf { row ->
                row.qualities.count { it.status == OnlineCacheQualityStatus.Reusable }
            },
            totalBytes = rows.sumOf { it.totalBytes },
        ),
    )

    private fun row(
        title: String,
        status: OnlineCacheQualityStatus,
    ) = OnlineCacheSongRow(
        platform = "kg",
        itemId = "1",
        title = title,
        artist = "Singer",
        album = null,
        artwork = null,
        durationMs = 180_000L,
        updatedAt = 100L,
        sourceMetadataBytes = 10L,
        totalBytes = 1034L,
        qualities = listOf(
            OnlineCacheQualityRow(
                quality = PlayQuality.STANDARD,
                status = status,
                cachedBytes = 1024L,
                contentLength = 1024L,
                updatedAt = 100L,
                invalidReason = null,
            ),
        ),
    )
}
