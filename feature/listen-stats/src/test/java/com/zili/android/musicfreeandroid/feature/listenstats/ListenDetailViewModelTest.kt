package com.zili.android.musicfreeandroid.feature.listenstats

import androidx.lifecycle.SavedStateHandle
import com.zili.android.musicfreeandroid.data.repository.listenstats.ListenStatsRepository
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenedSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ListenDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun mkSavedState(
        mode: String,
        scope: String = "WEEK",
        anchor: Long = 20221L,
        filterValue: String? = null,
    ) = SavedStateHandle().also {
        it["mode"] = mode
        it["scope"] = scope
        it["anchorEpochDay"] = anchor
        it["filterValue"] = filterValue
    }

    private fun mkSong(musicId: String, playCount: Int, firstSeen: Long): ListenedSong =
        ListenedSong(musicId, "p", musicId, "A", null, null, firstSeen, firstSeen, playCount, playCount * 60L)

    @Test fun firstSeen_defaults_to_firstSeenDescSort() = runTest {
        val songs = listOf(mkSong("old", 2, 1000), mkSong("new", 1, 5000))
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(songs)
        }
        val vm = ListenDetailViewModel(mkSavedState("FIRST_SEEN"), repo)
        val job = launch { vm.state.collect { } }
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), vm.state.value.items.map { it.musicId })
        assertEquals(DetailSort.FIRST_SEEN_DESC, vm.state.value.sort)
        job.cancel()
    }

    @Test fun byArtist_summary_includesFilterValue() = runTest {
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(listOf(mkSong("m1", 5, 1)))
        }
        val vm = ListenDetailViewModel(mkSavedState("BY_ARTIST", filterValue = "周杰伦"), repo)
        val job = launch { vm.state.collect { } }
        advanceUntilIdle()
        assertTrue(vm.state.value.summary.startsWith("周杰伦 ·"))
        assertTrue(vm.state.value.summary.contains("共 1 首"))
        job.cancel()
    }

    @Test fun onSortChange_reordersItems() = runTest {
        val songs = listOf(mkSong("a", 1, 1000), mkSong("b", 5, 500))
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(songs)
        }
        val vm = ListenDetailViewModel(mkSavedState("ALL_SONGS"), repo)
        val job = launch { vm.state.collect { } }
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), vm.state.value.items.map { it.musicId })
        vm.onSortChange(DetailSort.FIRST_SEEN_DESC)
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.musicId })
        job.cancel()
    }
}
