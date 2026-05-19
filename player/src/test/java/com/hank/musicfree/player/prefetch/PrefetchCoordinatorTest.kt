package com.hank.musicfree.player.prefetch

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrefetchCoordinatorTest {

    private val itemA = MusicItem(
        id = "a1", platform = "netease", title = "Track A",
        artist = "Artist", album = null, duration = 200_000L,
        url = null, artwork = null, qualities = null,
    )

    private val itemB = MusicItem(
        id = "b2", platform = "netease", title = "Track B",
        artist = "Artist", album = null, duration = 180_000L,
        url = null, artwork = null, qualities = null,
    )

    private val itemC = MusicItem(
        id = "c3", platform = "netease", title = "Track C",
        artist = "Artist", album = null, duration = 160_000L,
        url = null, artwork = null, qualities = null,
    )

    /**
     * Builds a [PrefetchCoordinator] backed by the test scope's dispatcher so that
     * [advanceUntilIdle] drains both the test coroutines and the coordinator's internal scope.
     */
    private fun TestScope.makeCoordinator(
        resolver: MediaSourceResolver,
        progressFlow: MutableSharedFlow<ProgressTick>,
        nextItemFlow: MutableStateFlow<MusicItem?>,
        isWifiFlow: MutableStateFlow<Boolean>,
    ): PrefetchCoordinator = PrefetchCoordinator(
        resolver = resolver,
        progressFlow = progressFlow,
        nextItemFlow = nextItemFlow,
        isWifiFlow = isWifiFlow,
        dispatcher = coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!,
    )

    // 1. progress < 0.6 doesn't trigger
    @Test
    fun `progress below threshold does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        // Emit progress at 30% and 50%
        progress.emit(ProgressTick(itemA, 60_000L, 200_000L))  // 0.30
        progress.emit(ProgressTick(itemA, 100_000L, 200_000L)) // 0.50
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), anyOrNull(), anyOrNull())
        coordinator.stop()
    }

    // 2. progress >= 0.6 on Wi-Fi triggers exactly once
    @Test
    fun `progress at 60 percent on wifi triggers prefetch exactly once`() = runTest {
        val resolver: MediaSourceResolver = mock()
        whenever(resolver.resolve(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 120_000L, 200_000L)) // 0.60
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(itemB, null, null)
        coordinator.stop()
    }

    // 3. non-Wi-Fi doesn't trigger
    @Test
    fun `non-wifi does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(false)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80 but no wifi
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), anyOrNull(), anyOrNull())
        coordinator.stop()
    }

    // 4. same next item doesn't prefetch twice
    @Test
    fun `same next item is only prefetched once even after multiple progress updates`() = runTest {
        val resolver: MediaSourceResolver = mock()
        whenever(resolver.resolve(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 140_000L, 200_000L)) // 0.70
        advanceUntilIdle()
        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(itemB, null, null)
        coordinator.stop()
    }

    // 5. switching next item allows a fresh prefetch
    @Test
    fun `switching next item allows fresh prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        whenever(resolver.resolve(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        // First prefetch — next is itemB
        progress.emit(ProgressTick(itemA, 140_000L, 200_000L)) // 0.70
        advanceUntilIdle()

        // Queue changes — now next is itemC
        nextItem.value = itemC
        advanceUntilIdle() // let the StateFlow update propagate
        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80 with nextItem=itemC
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(itemB, null, null)
        verify(resolver, times(1)).resolve(itemC, null, null)
        coordinator.stop()
    }

    // bonus: verify null next item doesn't trigger
    @Test
    fun `null next item does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(null)
        val isWifi = MutableStateFlow(true)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 180_000L, 200_000L)) // 0.90
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), anyOrNull(), anyOrNull())
        coordinator.stop()
    }
}
