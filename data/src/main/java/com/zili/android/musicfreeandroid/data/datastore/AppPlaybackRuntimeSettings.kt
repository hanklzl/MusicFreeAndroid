package com.zili.android.musicfreeandroid.data.datastore

import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.AudioInterruptionAction
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AppPlaybackRuntimeSettings @Inject constructor(
    private val appPreferences: AppPreferences,
) : PlaybackRuntimeSettings {
    override suspend fun defaultPlayQuality(): PlayQuality =
        appPreferences.defaultPlayQuality.first()

    override suspend fun playQualityOrder(): QualityFallbackOrder =
        appPreferences.playQualityOrder.first()

    override suspend fun useCellularPlay(): Boolean =
        appPreferences.useCellularPlay.first()

    override suspend fun allowConcurrentPlayback(): Boolean =
        appPreferences.allowConcurrentPlayback.first()

    override suspend fun autoPlayWhenAppStart(): Boolean =
        appPreferences.autoPlayWhenAppStart.first()

    override suspend fun tryChangeSourceWhenPlayFail(): Boolean =
        appPreferences.tryChangeSourceWhenPlayFail.first()

    override suspend fun autoStopWhenError(): Boolean =
        appPreferences.autoStopWhenError.first()

    override suspend fun audioInterruptionAction(): AudioInterruptionAction =
        appPreferences.audioInterruptionAction.first()

    override suspend fun audioInterruptionDuckVolume(): Float =
        appPreferences.audioInterruptionDuckVolume.first()

    override suspend fun showExitOnNotification(): Boolean =
        appPreferences.showExitOnNotification.first()
}
