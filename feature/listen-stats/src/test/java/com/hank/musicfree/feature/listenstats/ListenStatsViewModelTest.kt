package com.hank.musicfree.feature.listenstats

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.data.repository.listenstats.ListenStatsRepository
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import com.hank.musicfree.data.repository.listenstats.model.emptySnapshot
import com.hank.musicfree.player.controller.PlayerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ListenStatsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun createSavedStateHandle(scope: String = "WEEK", anchor: Long = -1L) =
        SavedStateHandle().also {
            it["scope"] = scope
            it["anchorEpochDay"] = anchor
        }

    private fun newViewModel(scope: String = "WEEK"): Pair<ListenStatsViewModel, Pair<ListenStatsRepository, PlayerController>> {
        val repo: ListenStatsRepository = mock {
            on { firstEventDate() } doReturn flowOf(null)
            on { statsForWindow(any(), any()) } doReturn flowOf(emptySnapshot())
        }
        val player: PlayerController = mock()
        val vm = ListenStatsViewModel(
            savedStateHandle = createSavedStateHandle(scope),
            repository = repo,
            playerController = player,
        )
        return vm to (repo to player)
    }

    @Test fun initial_scope_from_route_param() = runTest {
        val (vm, _) = newViewModel(scope = "MONTH")
        // Activate the stateIn flow by collecting it
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(TimeScope.MONTH, vm.state.value.scope)
        job.cancelAndJoin()
    }

    @Test fun onPagerNext_advancesAnchorByOneWeek() = runTest {
        val (vm, _) = newViewModel(scope = "WEEK")
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        val before = vm.state.value.anchor
        vm.onPagerNext()
        advanceUntilIdle()
        assertEquals(before.plusWeeks(1), vm.state.value.anchor)
        job.cancelAndJoin()
    }

    @Test fun onClearConfirmed_flushesTrackerThenClearsRepo() = runTest {
        val (vm, deps) = newViewModel()
        val (repo, player) = deps
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.onClearRequested()
        vm.onClearConfirmed()
        advanceUntilIdle()
        verify(player).flushListenTrackerForClear()
        verify(repo).clearAll()
        job.cancelAndJoin()
    }
}
