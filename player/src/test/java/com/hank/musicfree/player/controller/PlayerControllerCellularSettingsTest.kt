package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.network.PlaybackNetworkState
import com.hank.musicfree.player.network.PlaybackNetworkStateProvider
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.mockito.kotlin.mock
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerCellularSettingsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `playItem blocks unresolved remote source on cellular when disabled`() = runBlocking {
        val resolver = RecordingResolver()
        val controller = controller(
            resolver = resolver,
            settings = FakeRuntimeSettings(useCellularPlay = false),
            network = FakeNetworkStateProvider(isCellular = true),
        )

        try {
            val errors = mutableListOf<String>()
            val job = launch { controller.errorEvents.take(1).toList(errors) }
            yield()

            controller.playItem(remoteItem())

            withTimeout(2_000) { job.join() }
            assertEquals("当前网络不允许播放，请在设置中开启移动网络播放", errors.single())
            assertTrue(resolver.requestedIds.isEmpty())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem resolves unresolved remote source on cellular when enabled`() = runBlocking {
        val resolver = RecordingResolver()
        val controller = controller(
            resolver = resolver,
            settings = FakeRuntimeSettings(useCellularPlay = true),
            network = FakeNetworkStateProvider(isCellular = true),
        )

        try {
            val errors = mutableListOf<String>()
            val job = launch { controller.errorEvents.take(1).toList(errors) }
            yield()

            controller.playItem(remoteItem())

            withTimeout(2_000) { job.join() }
            assertEquals("播放失败: 无法解析音源", errors.single())
            assertEquals(listOf("remote-1"), resolver.requestedIds)
        } finally {
            controller.release()
        }
    }

    private fun controller(
        resolver: MediaSourceResolver,
        settings: PlaybackRuntimeSettings,
        network: PlaybackNetworkStateProvider,
    ) = PlayerController(
        context = context,
        mediaSourceResolver = resolver,
        playbackRuntimeSettings = settings,
        networkStateProvider = network,
        listenTracker = mock<ListenTracker>(),
    )

    private fun remoteItem() = MusicItem(
        id = "remote-1",
        platform = "plugin",
        title = "Remote Song",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )

    private class RecordingResolver : MediaSourceResolver {
        val requestedIds = mutableListOf<String>()

        override suspend fun resolve(item: MusicItem, quality: String?): MediaSourceResolution? {
            requestedIds += item.id
            return null
        }
    }

    private class FakeRuntimeSettings(
        private val useCellularPlay: Boolean,
    ) : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.STANDARD

        override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc

        override suspend fun useCellularPlay(): Boolean = useCellularPlay

        override suspend fun allowConcurrentPlayback(): Boolean = false

        override suspend fun autoPlayWhenAppStart(): Boolean = false

        override suspend fun tryChangeSourceWhenPlayFail(): Boolean = false

        override suspend fun autoStopWhenError(): Boolean = false

        override suspend fun audioInterruptionAction(): AudioInterruptionAction =
            AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume(): Float = 0.5f

        override suspend fun showExitOnNotification(): Boolean = false
    }

    private class FakeNetworkStateProvider(
        private val isCellular: Boolean,
    ) : PlaybackNetworkStateProvider {
        override fun currentState(): PlaybackNetworkState =
            PlaybackNetworkState(isCellular = isCellular)
    }
}
