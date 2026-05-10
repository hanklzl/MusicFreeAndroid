package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.SearchResultClickAction
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeDirectory
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

data class BasicSettingsUiState(
    val maxSearchHistoryLength: Int = 50,
    val musicDetailDefaultPage: MusicDetailDefaultPage = MusicDetailDefaultPage.Album,
    val musicDetailAwake: Boolean = false,
    val clickMusicInSearch: SearchResultClickAction = SearchResultClickAction.PlayMusic,
    val clickMusicInAlbum: AlbumMusicClickAction = AlbumMusicClickAction.PlayAlbum,
    val musicOrderInLocalSheet: SortMode = SortMode.Manual,
    val defaultPlayQuality: PlayQuality = PlayQuality.STANDARD,
    val playQualityOrder: QualityFallbackOrder = QualityFallbackOrder.Asc,
    val maxDownload: Int = 3,
    val defaultDownloadQuality: PlayQuality = PlayQuality.STANDARD,
    val downloadQualityOrder: QualityFallbackOrder = QualityFallbackOrder.Asc,
    val useCellularPlay: Boolean = false,
    val useCellularDownload: Boolean = false,
    val lyricAutoSearchEnabled: Boolean = true,
    val storageAccessState: StorageAccessState = StorageAccessState(),
)

data class StorageAccessState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

data class FeedbackExportUiState(
    val isExporting: Boolean = false,
    val isClearing: Boolean = false,
    val pendingPackage: FeedbackPackage? = null,
    val errorMessage: String? = null,
) {
    val isOperationInProgress: Boolean
        get() = isExporting || isClearing
}

private data class CommonBasicSettingsState(
    val maxSearchHistoryLength: Int,
    val musicDetailDefaultPage: MusicDetailDefaultPage,
    val musicDetailAwake: Boolean,
)

private data class SheetAlbumBasicSettingsState(
    val clickMusicInSearch: SearchResultClickAction,
    val clickMusicInAlbum: AlbumMusicClickAction,
    val musicOrderInLocalSheet: SortMode,
)

private data class PlaybackBasicSettingsState(
    val defaultPlayQuality: PlayQuality,
    val playQualityOrder: QualityFallbackOrder,
)

private data class DownloadBasicSettingsState(
    val maxDownload: Int,
    val defaultDownloadQuality: PlayQuality,
    val downloadQualityOrder: QualityFallbackOrder,
)

private data class NetworkBasicSettingsState(
    val useCellularPlay: Boolean,
    val useCellularDownload: Boolean,
)

private data class RuntimeBasicSettingsState(
    val common: CommonBasicSettingsState,
    val sheetAlbum: SheetAlbumBasicSettingsState,
    val playback: PlaybackBasicSettingsState,
    val download: DownloadBasicSettingsState,
    val network: NetworkBasicSettingsState,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val feedbackLogExporter: FeedbackLogExporterContract,
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    private val feedbackActionLock = Mutex()

    private val _feedbackExportUiState = MutableStateFlow(FeedbackExportUiState())
    val feedbackExportUiState: StateFlow<FeedbackExportUiState> = _feedbackExportUiState.asStateFlow()

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    val maxDownload: StateFlow<Int> = appPreferences.maxDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val maxSearchHistoryLength: StateFlow<Int> = appPreferences.maxSearchHistoryLength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    val musicDetailDefaultPage: StateFlow<MusicDetailDefaultPage> = appPreferences.musicDetailDefaultPage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MusicDetailDefaultPage.Album)

    val musicDetailAwake: StateFlow<Boolean> = appPreferences.musicDetailAwake
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clickMusicInSearch: StateFlow<SearchResultClickAction> = appPreferences.clickMusicInSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResultClickAction.PlayMusic)

    val clickMusicInAlbum: StateFlow<AlbumMusicClickAction> = appPreferences.clickMusicInAlbum
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumMusicClickAction.PlayAlbum)

    val musicOrderInLocalSheet: StateFlow<SortMode> = appPreferences.musicOrderInLocalSheet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortMode.Manual)

    val defaultPlayQuality: StateFlow<PlayQuality> = appPreferences.defaultPlayQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    val playQualityOrder: StateFlow<QualityFallbackOrder> = appPreferences.playQualityOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QualityFallbackOrder.Asc)

    val useCellularDownload: StateFlow<Boolean> = appPreferences.useCellularDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val useCellularPlay: StateFlow<Boolean> = appPreferences.useCellularPlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultDownloadQuality: StateFlow<PlayQuality> = appPreferences.defaultDownloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    val downloadQualityOrder: StateFlow<QualityFallbackOrder> = appPreferences.downloadQualityOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QualityFallbackOrder.Asc)

    val lyricAutoSearchEnabled: StateFlow<Boolean> = appPreferences.lyricAutoSearchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val commonBasicSettingsState = combine(
        maxSearchHistoryLength,
        musicDetailDefaultPage,
        musicDetailAwake,
    ) { maxSearchHistoryLength, musicDetailDefaultPage, musicDetailAwake ->
        CommonBasicSettingsState(
            maxSearchHistoryLength = maxSearchHistoryLength,
            musicDetailDefaultPage = musicDetailDefaultPage,
            musicDetailAwake = musicDetailAwake,
        )
    }

    private val sheetAlbumBasicSettingsState = combine(
        clickMusicInSearch,
        clickMusicInAlbum,
        musicOrderInLocalSheet,
    ) { clickMusicInSearch, clickMusicInAlbum, musicOrderInLocalSheet ->
        SheetAlbumBasicSettingsState(
            clickMusicInSearch = clickMusicInSearch,
            clickMusicInAlbum = clickMusicInAlbum,
            musicOrderInLocalSheet = musicOrderInLocalSheet,
        )
    }

    private val playbackBasicSettingsState = combine(
        defaultPlayQuality,
        playQualityOrder,
    ) { defaultPlayQuality, playQualityOrder ->
        PlaybackBasicSettingsState(
            defaultPlayQuality = defaultPlayQuality,
            playQualityOrder = playQualityOrder,
        )
    }

    private val downloadBasicSettingsState = combine(
        maxDownload,
        defaultDownloadQuality,
        downloadQualityOrder,
    ) { maxDownload, defaultDownloadQuality, downloadQualityOrder ->
        DownloadBasicSettingsState(
            maxDownload = maxDownload,
            defaultDownloadQuality = defaultDownloadQuality,
            downloadQualityOrder = downloadQualityOrder,
        )
    }

    private val networkBasicSettingsState = combine(
        useCellularPlay,
        useCellularDownload,
    ) { useCellularPlay, useCellularDownload ->
        NetworkBasicSettingsState(
            useCellularPlay = useCellularPlay,
            useCellularDownload = useCellularDownload,
        )
    }

    private val runtimeBasicSettingsState = combine(
        commonBasicSettingsState,
        sheetAlbumBasicSettingsState,
        playbackBasicSettingsState,
        downloadBasicSettingsState,
        networkBasicSettingsState,
    ) { common, sheetAlbum, playback, download, network ->
        RuntimeBasicSettingsState(
            common = common,
            sheetAlbum = sheetAlbum,
            playback = playback,
            download = download,
            network = network,
        )
    }

    val basicSettingsUiState: StateFlow<BasicSettingsUiState> = combine(
        runtimeBasicSettingsState,
        lyricAutoSearchEnabled,
        storageAccessState,
    ) { runtime, lyricAutoSearchEnabled, storageAccessState ->
        BasicSettingsUiState(
            maxSearchHistoryLength = runtime.common.maxSearchHistoryLength,
            musicDetailDefaultPage = runtime.common.musicDetailDefaultPage,
            musicDetailAwake = runtime.common.musicDetailAwake,
            clickMusicInSearch = runtime.sheetAlbum.clickMusicInSearch,
            clickMusicInAlbum = runtime.sheetAlbum.clickMusicInAlbum,
            musicOrderInLocalSheet = runtime.sheetAlbum.musicOrderInLocalSheet,
            defaultPlayQuality = runtime.playback.defaultPlayQuality,
            playQualityOrder = runtime.playback.playQualityOrder,
            maxDownload = runtime.download.maxDownload,
            defaultDownloadQuality = runtime.download.defaultDownloadQuality,
            downloadQualityOrder = runtime.download.downloadQualityOrder,
            useCellularPlay = runtime.network.useCellularPlay,
            useCellularDownload = runtime.network.useCellularDownload,
            lyricAutoSearchEnabled = lyricAutoSearchEnabled,
            storageAccessState = storageAccessState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BasicSettingsUiState())

    fun setMaxSearchHistoryLength(value: Int) = viewModelScope.launch {
        appPreferences.setMaxSearchHistoryLength(value)
    }

    fun setMusicDetailDefaultPage(value: MusicDetailDefaultPage) = viewModelScope.launch {
        appPreferences.setMusicDetailDefaultPage(value)
    }

    fun setMusicDetailAwake(value: Boolean) = viewModelScope.launch {
        appPreferences.setMusicDetailAwake(value)
    }

    fun setClickMusicInSearch(value: SearchResultClickAction) = viewModelScope.launch {
        appPreferences.setClickMusicInSearch(value)
    }

    fun setClickMusicInAlbum(value: AlbumMusicClickAction) = viewModelScope.launch {
        appPreferences.setClickMusicInAlbum(value)
    }

    fun setMusicOrderInLocalSheet(value: SortMode) = viewModelScope.launch {
        appPreferences.setMusicOrderInLocalSheet(value)
    }

    fun setDefaultPlayQuality(quality: PlayQuality) = viewModelScope.launch {
        appPreferences.setDefaultPlayQuality(quality)
    }

    fun setPlayQualityOrder(order: QualityFallbackOrder) = viewModelScope.launch {
        appPreferences.setPlayQualityOrder(order)
    }

    fun setMaxDownload(value: Int) = viewModelScope.launch {
        appPreferences.setMaxDownload(value)
    }

    fun setUseCellularDownload(value: Boolean) = viewModelScope.launch {
        appPreferences.setUseCellularDownload(value)
    }

    fun setDefaultDownloadQuality(quality: PlayQuality) = viewModelScope.launch {
        appPreferences.setDefaultDownloadQuality(quality)
    }

    fun setDownloadQualityOrder(order: QualityFallbackOrder) = viewModelScope.launch {
        appPreferences.setDownloadQualityOrder(order)
    }

    fun setUseCellularPlay(value: Boolean) = viewModelScope.launch {
        appPreferences.setUseCellularPlay(value)
    }

    fun setLyricAutoSearchEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setLyricAutoSearchEnabled(value)
    }

    fun createFeedbackPackage() {
        viewModelScope.launch {
            if (!feedbackActionLock.tryLock()) {
                return@launch
            }
            try {
                _feedbackExportUiState.update {
                    it.copy(
                        isExporting = true,
                        isClearing = false,
                        errorMessage = null,
                        pendingPackage = null,
                    )
                }
                val feedbackPackage = feedbackLogExporter.createPackage()
                _feedbackExportUiState.update { it.copy(pendingPackage = feedbackPackage) }
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_package_create_failed", error)
                _feedbackExportUiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "生成日志包失败")
                }
            } finally {
                _feedbackExportUiState.update { it.copy(isExporting = false) }
                feedbackActionLock.unlock()
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            if (!feedbackActionLock.tryLock()) {
                return@launch
            }
            try {
                _feedbackExportUiState.update {
                    it.copy(
                        isClearing = true,
                        isExporting = false,
                        errorMessage = null,
                    )
                }
                feedbackLogExporter.clearLogs()
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_logs_clear_failed", error)
                _feedbackExportUiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "清空日志失败")
                }
            } finally {
                _feedbackExportUiState.update { it.copy(isClearing = false) }
                feedbackActionLock.unlock()
            }
        }
    }

    fun onFeedbackPackageShared() {
        _feedbackExportUiState.update { it.copy(pendingPackage = null) }
    }

    fun onFeedbackExportError(errorMessage: String) {
        _feedbackExportUiState.update { it.copy(errorMessage = errorMessage) }
    }

    fun clearFeedbackError() {
        _feedbackExportUiState.update { it.copy(errorMessage = null) }
    }
}
