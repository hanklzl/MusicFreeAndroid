package com.zili.android.musicfreeandroid.downloader.prefs

import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
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
class DownloadConfigSource @Inject constructor(
    private val appPrefs: AppPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val state: StateFlow<DownloadConfig> = combine(
        appPrefs.maxDownload,
        appPrefs.useCellularDownload,
        appPrefs.defaultDownloadQuality,
        appPrefs.downloadDirRelative,
    ) { maxDl, cellular, quality, dir ->
        DownloadConfig(maxDl, cellular, quality, dir)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DownloadConfig(3, false, PlayQuality.STANDARD, "Music/MusicFree/"),
    )
}
