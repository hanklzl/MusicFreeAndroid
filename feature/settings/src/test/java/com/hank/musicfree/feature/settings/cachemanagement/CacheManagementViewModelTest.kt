package com.hank.musicfree.feature.settings.cachemanagement

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.OnlineCacheQualityRow
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import com.hank.musicfree.feature.settings.MainDispatcherRule
import com.hank.musicfree.feature.settings.OnlineCacheClearResult
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `initial load exposes cache rows and summary`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val row = cacheRow(
            platform = "kg",
            itemId = "1",
            title = "Song",
            status = OnlineCacheQualityStatus.Reusable,
            totalBytes = 128L,
        )
        coEvery { repo.listOnlineCacheCatalog() } returns listOf(row)
        val cleaner = mockk<SettingsCacheCleaner>()

        val viewModel = CacheManagementViewModel(repo, cleaner)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(row), state.visibleRows)
        assertEquals(1, state.summary.songCount)
        assertEquals(1, state.summary.qualityCount)
        assertEquals(1, state.summary.reusableCount)
        assertEquals(128L, state.summary.totalBytes)
    }

    @Test
    fun `search filters by title artist and platform`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val blue = cacheRow(
            platform = "kg",
            itemId = "1",
            title = "Blue Song",
            artist = "Mika",
        )
        val red = cacheRow(
            platform = "qq",
            itemId = "2",
            title = "Red Song",
            artist = "Keiko",
        )
        coEvery { repo.listOnlineCacheCatalog() } returns listOf(blue, red)
        val viewModel = CacheManagementViewModel(repo, mockk())
        advanceUntilIdle()

        viewModel.onSearchQueryChange("blue")
        assertEquals(listOf("Blue Song"), viewModel.uiState.value.visibleRows.map { it.title })

        viewModel.onSearchQueryChange("qq")
        assertEquals(listOf("Red Song"), viewModel.uiState.value.visibleRows.map { it.title })

        viewModel.onSearchQueryChange("keiko")
        assertEquals(listOf("Red Song"), viewModel.uiState.value.visibleRows.map { it.title })
    }

    @Test
    fun `status filter shows reusable rows only`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val reusable = cacheRow(
            platform = "kg",
            itemId = "1",
            title = "Reusable Song",
            status = OnlineCacheQualityStatus.Reusable,
        )
        val partial = cacheRow(
            platform = "qq",
            itemId = "2",
            title = "Partial Song",
            status = OnlineCacheQualityStatus.Partial,
        )
        coEvery { repo.listOnlineCacheCatalog() } returns listOf(reusable, partial)
        val viewModel = CacheManagementViewModel(repo, mockk())
        advanceUntilIdle()

        viewModel.onFilterChange(CacheManagementFilter.Reusable)

        assertEquals(listOf("Reusable Song"), viewModel.uiState.value.visibleRows.map { it.title })
    }

    @Test
    fun `clear quality refreshes rows and message`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val row = cacheRow(platform = "kg", itemId = "1", title = "Song")
        coEvery { repo.listOnlineCacheCatalog() } returnsMany listOf(listOf(row), listOf(row))
        val cleaner = mockk<SettingsCacheCleaner>()
        coEvery {
            cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD)
        } returns OnlineCacheClearResult(
            scope = "quality",
            platform = "kg",
            itemId = "1",
            quality = PlayQuality.STANDARD,
            freedBytes = 0L,
            durationMs = 3L,
        )
        val viewModel = CacheManagementViewModel(repo, cleaner)
        advanceUntilIdle()

        viewModel.selectRow(row)
        viewModel.clearSelectedQuality(PlayQuality.STANDARD)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isClearing)
        assertNull(state.selectedRow)
        assertEquals("已清理 Song 的标准在线播放缓存", state.message)
        coVerify { cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD) }
        coVerify(atLeast = 2) { repo.listOnlineCacheCatalog() }
    }

    @Test
    fun `clear selected song ignores duplicate calls while first clear is pending`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = mockk<MediaCacheRepository>()
            val row = cacheRow(platform = "kg", itemId = "1", title = "Song")
            coEvery { repo.listOnlineCacheCatalog() } returnsMany listOf(listOf(row), listOf(row))
            val cleaner = mockk<SettingsCacheCleaner>()
            coEvery {
                cleaner.clearOnlineSongCache("kg", "1", null)
            } returns OnlineCacheClearResult(
                scope = "song",
                platform = "kg",
                itemId = "1",
                quality = null,
                freedBytes = 0L,
                durationMs = 4L,
            )
            val viewModel = CacheManagementViewModel(repo, cleaner)
            advanceUntilIdle()

            viewModel.selectRow(row)
            viewModel.clearSelectedSong()
            viewModel.clearSelectedSong()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isClearing)
            coVerify(exactly = 1) { cleaner.clearOnlineSongCache("kg", "1", null) }
        }

    @Test
    fun `clear song refreshes rows and message`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val row = cacheRow(platform = "kg", itemId = "1", title = "Song")
        coEvery { repo.listOnlineCacheCatalog() } returnsMany listOf(listOf(row), emptyList())
        val cleaner = mockk<SettingsCacheCleaner>()
        coEvery {
            cleaner.clearOnlineSongCache("kg", "1", null)
        } returns OnlineCacheClearResult(
            scope = "song",
            platform = "kg",
            itemId = "1",
            quality = null,
            freedBytes = 0L,
            durationMs = 4L,
        )
        val viewModel = CacheManagementViewModel(repo, cleaner)
        advanceUntilIdle()

        viewModel.selectRow(row)
        viewModel.clearSelectedSong()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isClearing)
        assertNull(state.selectedRow)
        assertEquals("已清理 Song 的在线播放缓存", state.message)
        coVerify { cleaner.clearOnlineSongCache("kg", "1", null) }
        coVerify(atLeast = 2) { repo.listOnlineCacheCatalog() }
    }

    @Test
    fun `clear all refreshes rows and message`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        val row = cacheRow(platform = "kg", itemId = "1", title = "Song")
        coEvery { repo.listOnlineCacheCatalog() } returnsMany listOf(listOf(row), emptyList())
        val cleaner = mockk<SettingsCacheCleaner>()
        coEvery {
            cleaner.clearAllOnlinePlaybackCache()
        } returns OnlineCacheClearResult(
            scope = "all",
            platform = null,
            itemId = null,
            quality = null,
            freedBytes = 100L,
            durationMs = 5L,
        )
        val viewModel = CacheManagementViewModel(repo, cleaner)
        advanceUntilIdle()

        viewModel.clearAll()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isClearing)
        assertEquals("已清理全部在线播放缓存", state.message)
        coVerify { cleaner.clearAllOnlinePlaybackCache() }
        coVerify(atLeast = 2) { repo.listOnlineCacheCatalog() }
    }

    @Test
    fun `clear all refresh clears loading after stale initial load completes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = mockk<MediaCacheRepository>()
            val staleRow = cacheRow(platform = "kg", itemId = "stale", title = "Stale")
            val clearOwnedRow = cacheRow(platform = "qq", itemId = "fresh", title = "Fresh")
            val initialLoadRelease = CompletableDeferred<Unit>()
            var loadCallCount = 0
            coEvery { repo.listOnlineCacheCatalog() } coAnswers {
                loadCallCount += 1
                if (loadCallCount == 1) {
                    initialLoadRelease.await()
                    listOf(staleRow)
                } else {
                    listOf(clearOwnedRow)
                }
            }
            val cleaner = mockk<SettingsCacheCleaner>()
            coEvery {
                cleaner.clearAllOnlinePlaybackCache()
            } returns OnlineCacheClearResult(
                scope = "all",
                platform = null,
                itemId = null,
                quality = null,
                freedBytes = 100L,
                durationMs = 5L,
            )
            val viewModel = CacheManagementViewModel(repo, cleaner)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoading)
            viewModel.clearAll()
            advanceUntilIdle()
            initialLoadRelease.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isClearing)
            assertEquals(listOf(clearOwnedRow), state.visibleRows)
            assertEquals("已清理全部在线播放缓存", state.message)
        }

    @Test
    fun `repository load failure sets error state`() = runTest(mainDispatcherRule.dispatcher) {
        val repo = mockk<MediaCacheRepository>()
        coEvery { repo.listOnlineCacheCatalog() } throws IllegalStateException("boom")

        val viewModel = CacheManagementViewModel(repo, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("缓存列表加载失败", state.errorMessage)
    }

    private fun cacheRow(
        platform: String,
        itemId: String,
        title: String,
        artist: String = "Artist",
        status: OnlineCacheQualityStatus = OnlineCacheQualityStatus.Reusable,
        quality: PlayQuality = PlayQuality.STANDARD,
        totalBytes: Long = 100L,
    ): OnlineCacheSongRow = OnlineCacheSongRow(
        platform = platform,
        itemId = itemId,
        title = title,
        artist = artist,
        album = null,
        artwork = null,
        durationMs = null,
        updatedAt = 1_700_000_000_000L,
        sourceMetadataBytes = 12L,
        totalBytes = totalBytes,
        qualities = listOf(
            OnlineCacheQualityRow(
                quality = quality,
                status = status,
                cachedBytes = totalBytes,
                contentLength = totalBytes.takeIf { it > 0L },
                updatedAt = 1_700_000_000_000L,
                invalidReason = null,
            ),
        ),
    )
}
