package com.hank.musicfree.feature.settings.traffic

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.data.traffic.TrafficRangeSummary
import com.hank.musicfree.data.traffic.TrafficStatsRepository
import com.hank.musicfree.player.cache.MediaCacheStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficStatsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun newRepo(): TrafficStatsRepository = mockk(relaxed = true) {
        every { observeMonthly(any()) } returns flowOf(
            TrafficRangeSummary(LocalDate.now(), emptyList(), 0L, emptyMap()),
        )
    }

    private fun newCacheStore(): MediaCacheStore = mockk(relaxed = true) {
        every { usedBytesFlow } returns MutableStateFlow(0L)
    }

    @Test fun clearAllRecords_calls_repo() = runTest {
        val repo = newRepo()
        val cacheStore = newCacheStore()
        val vm = TrafficStatsViewModel(repo, cacheStore, SavedStateHandle())
        vm.clearAllRecords()
        advanceUntilIdle()
        coVerify(timeout = 1_000) { repo.clearAll() }
    }

    @Test fun clearMediaCache_calls_store() = runTest {
        val repo = newRepo()
        val cacheStore = newCacheStore()
        val vm = TrafficStatsViewModel(repo, cacheStore, SavedStateHandle())
        vm.clearMediaCache()
        advanceUntilIdle()
        coVerify(timeout = 1_000) { cacheStore.clear() }
    }
}
