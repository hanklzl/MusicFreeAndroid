package com.hank.musicfree.feature.settings.cachemanagement

import com.hank.musicfree.feature.settings.MainDispatcherRule
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import com.hank.musicfree.feature.settings.SongCacheClearResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CacheManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `clearSpecifiedSongCache clears cache and reports result`() = runTest {
        val cleaner = mockk<SettingsCacheCleaner>()
        coEvery {
            cleaner.clearSongPlaybackCache("元力QQ", "302986918")
        } returns SongCacheClearResult(
            platform = "元力QQ",
            itemId = "302986918",
            localAssociationCleared = true,
            durationMs = 12L,
        )
        val viewModel = CacheManagementViewModel(cleaner)

        viewModel.onPlatformChange(" 元力QQ ")
        viewModel.onItemIdChange(" 302986918 ")
        viewModel.clearSpecifiedSongCache()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isClearing)
        assertEquals("元力QQ", viewModel.uiState.value.platform)
        assertEquals("302986918", viewModel.uiState.value.itemId)
        assertEquals("已清理 元力QQ / 302986918，已解除本地播放关联", viewModel.uiState.value.message)
        coVerify { cleaner.clearSongPlaybackCache("元力QQ", "302986918") }
    }
}
