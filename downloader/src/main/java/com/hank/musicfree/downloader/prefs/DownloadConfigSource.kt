package com.hank.musicfree.downloader.prefs

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadConfigSource internal constructor(
    appPrefs: AppPreferences,
    scope: CoroutineScope,
) {
    @Inject
    constructor(appPrefs: AppPreferences) : this(
        appPrefs,
        CoroutineScope(Dispatchers.Default + SupervisorJob()),
    )

    val state: StateFlow<DownloadConfig> = combine(
        appPrefs.maxDownload,
        appPrefs.useCellularDownload,
        appPrefs.defaultDownloadQuality,
        appPrefs.downloadQualityOrder,
        appPrefs.downloadDirRelative,
    ) { maxDl, cellular, quality, qualityOrder, dir ->
        DownloadConfig(maxDl, cellular, quality, qualityOrder, dir)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DownloadConfig(
            3,
            false,
            PlayQuality.STANDARD,
            QualityFallbackOrder.Asc,
            "Music/MusicFree/",
        ),
    )
}
