package com.zili.android.musicfreeandroid.core.model

interface PlaybackRuntimeSettings {
    suspend fun defaultPlayQuality(): PlayQuality

    suspend fun playQualityOrder(): QualityFallbackOrder

    suspend fun useCellularPlay(): Boolean

    suspend fun allowConcurrentPlayback(): Boolean

    suspend fun autoPlayWhenAppStart(): Boolean

    suspend fun tryChangeSourceWhenPlayFail(): Boolean

    suspend fun autoStopWhenError(): Boolean

    suspend fun audioInterruptionAction(): AudioInterruptionAction

    suspend fun audioInterruptionDuckVolume(): Float

    suspend fun showExitOnNotification(): Boolean

    object Defaults : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.STANDARD

        override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc

        override suspend fun useCellularPlay(): Boolean = false

        override suspend fun allowConcurrentPlayback(): Boolean = false

        override suspend fun autoPlayWhenAppStart(): Boolean = false

        override suspend fun tryChangeSourceWhenPlayFail(): Boolean = false

        override suspend fun autoStopWhenError(): Boolean = false

        override suspend fun audioInterruptionAction(): AudioInterruptionAction = AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume(): Float = 0.5f

        override suspend fun showExitOnNotification(): Boolean = false
    }
}
