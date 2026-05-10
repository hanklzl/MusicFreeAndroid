package com.zili.android.musicfreeandroid.core.model

interface PlaybackRuntimeSettings {
    suspend fun defaultPlayQuality(): PlayQuality

    suspend fun playQualityOrder(): QualityFallbackOrder

    suspend fun useCellularPlay(): Boolean

    object Defaults : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.STANDARD

        override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc

        override suspend fun useCellularPlay(): Boolean = false
    }
}
