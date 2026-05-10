package com.zili.android.musicfreeandroid.data.datastore

import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
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
}
