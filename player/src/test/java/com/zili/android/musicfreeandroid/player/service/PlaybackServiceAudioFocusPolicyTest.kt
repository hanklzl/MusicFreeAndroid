package com.zili.android.musicfreeandroid.player.service

import com.zili.android.musicfreeandroid.core.model.AudioInterruptionAction
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceAudioFocusPolicyTest {

    @Test
    fun `audio focus is handled when concurrent playback is disabled`() = runTest {
        assertTrue(
            PlaybackService.shouldHandleAudioFocus(
                FakeRuntimeSettings(allowConcurrentPlayback = false),
            ),
        )
    }

    @Test
    fun `audio focus is not handled when concurrent playback is enabled`() = runTest {
        assertFalse(
            PlaybackService.shouldHandleAudioFocus(
                FakeRuntimeSettings(allowConcurrentPlayback = true),
            ),
        )
    }

    private class FakeRuntimeSettings(
        private val allowConcurrentPlayback: Boolean,
    ) : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.STANDARD

        override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc

        override suspend fun useCellularPlay(): Boolean = true

        override suspend fun allowConcurrentPlayback(): Boolean = allowConcurrentPlayback

        override suspend fun autoPlayWhenAppStart(): Boolean = false

        override suspend fun tryChangeSourceWhenPlayFail(): Boolean = false

        override suspend fun autoStopWhenError(): Boolean = false

        override suspend fun audioInterruptionAction(): AudioInterruptionAction =
            AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume(): Float = 0.5f
    }
}
