package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.AudioInterruptionAction
import com.zili.android.musicfreeandroid.core.model.DesktopLyricAlignment
import com.zili.android.musicfreeandroid.core.model.LyricAssociationType
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
import com.zili.android.musicfreeandroid.logging.ReadableLogStore
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class BasicSettingsUiState(
    val maxSearchHistoryLength: Int = 50,
    val musicDetailDefaultPage: MusicDetailDefaultPage = MusicDetailDefaultPage.Album,
    val musicDetailAwake: Boolean = false,
    val lyricAssociationType: LyricAssociationType = LyricAssociationType.Search,
    val showExitOnNotification: Boolean = false,
    val clickMusicInSearch: SearchResultClickAction = SearchResultClickAction.PlayMusic,
    val clickMusicInAlbum: AlbumMusicClickAction = AlbumMusicClickAction.PlayAlbum,
    val musicOrderInLocalSheet: SortMode = SortMode.Manual,
    val defaultPlayQuality: PlayQuality = PlayQuality.STANDARD,
    val playQualityOrder: QualityFallbackOrder = QualityFallbackOrder.Asc,
    val allowConcurrentPlayback: Boolean = false,
    val autoPlayWhenAppStart: Boolean = false,
    val tryChangeSourceWhenPlayFail: Boolean = false,
    val autoStopWhenError: Boolean = false,
    val audioInterruptionAction: AudioInterruptionAction = AudioInterruptionAction.Pause,
    val audioInterruptionDuckVolume: Float = 0.5f,
    val maxDownload: Int = 3,
    val defaultDownloadQuality: PlayQuality = PlayQuality.STANDARD,
    val downloadQualityOrder: QualityFallbackOrder = QualityFallbackOrder.Asc,
    val useCellularPlay: Boolean = false,
    val useCellularDownload: Boolean = false,
    val lyricAutoSearchEnabled: Boolean = true,
    val desktopLyricEnabled: Boolean = false,
    val desktopLyricAlignment: DesktopLyricAlignment = DesktopLyricAlignment.Center,
    val desktopLyricTopPercent: Float = 0.08f,
    val desktopLyricLeftPercent: Float = 0.08f,
    val desktopLyricWidthPercent: Float = 0.84f,
    val desktopLyricFontSizeSp: Int = 18,
    val desktopLyricTextColor: String = "#FFFFFFFF",
    val desktopLyricBackgroundColor: String = "#66000000",
    val autoUpdatePlugins: Boolean = false,
    val skipPluginVersionCheck: Boolean = false,
    val lazyLoadPlugins: Boolean = false,
    val maxMusicCacheSizeMb: Int = 512,
    val debugErrorLogEnabled: Boolean = true,
    val debugTraceLogEnabled: Boolean = true,
    val debugDevLogEnabled: Boolean = false,
    val cacheActionInProgress: Boolean = false,
    val cacheActionMessage: String? = null,
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

data class ErrorLogUiState(
    val visible: Boolean = false,
    val content: String = "",
)

private data class CommonBasicSettingsState(
    val maxSearchHistoryLength: Int,
    val musicDetailDefaultPage: MusicDetailDefaultPage,
    val musicDetailAwake: Boolean,
    val lyricAssociationType: LyricAssociationType,
    val showExitOnNotification: Boolean,
)

private data class SheetAlbumBasicSettingsState(
    val clickMusicInSearch: SearchResultClickAction,
    val clickMusicInAlbum: AlbumMusicClickAction,
    val musicOrderInLocalSheet: SortMode,
)

private data class PlaybackBasicSettingsState(
    val defaultPlayQuality: PlayQuality,
    val playQualityOrder: QualityFallbackOrder,
    val allowConcurrentPlayback: Boolean,
    val autoPlayWhenAppStart: Boolean,
    val tryChangeSourceWhenPlayFail: Boolean,
    val autoStopWhenError: Boolean,
    val audioInterruptionAction: AudioInterruptionAction,
    val audioInterruptionDuckVolume: Float,
)

private data class PlaybackQualitySettingsState(
    val defaultPlayQuality: PlayQuality,
    val playQualityOrder: QualityFallbackOrder,
)

private data class PlaybackFailureSettingsState(
    val allowConcurrentPlayback: Boolean,
    val autoPlayWhenAppStart: Boolean,
    val tryChangeSourceWhenPlayFail: Boolean,
    val autoStopWhenError: Boolean,
)

private data class AudioInterruptionSettingsState(
    val action: AudioInterruptionAction,
    val duckVolume: Float,
)

private data class PluginBasicSettingsState(
    val autoUpdatePlugins: Boolean,
    val skipPluginVersionCheck: Boolean,
    val lazyLoadPlugins: Boolean,
)

private data class CacheBasicSettingsState(
    val maxMusicCacheSizeMb: Int,
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

private data class LyricBasicSettingsState(
    val autoSearchEnabled: Boolean,
    val desktopEnabled: Boolean,
    val desktopAlignment: DesktopLyricAlignment,
    val desktopTopPercent: Float,
    val desktopLeftPercent: Float,
    val desktopWidthPercent: Float,
    val desktopFontSizeSp: Int,
    val desktopTextColor: String,
    val desktopBackgroundColor: String,
)

private data class DeveloperBasicSettingsState(
    val errorLogEnabled: Boolean,
    val traceLogEnabled: Boolean,
    val devLogEnabled: Boolean,
)

private data class RuntimeBasicSettingsState(
    val common: CommonBasicSettingsState,
    val sheetAlbum: SheetAlbumBasicSettingsState,
    val playback: PlaybackBasicSettingsState,
    val download: DownloadBasicSettingsState,
    val network: NetworkBasicSettingsState,
    val plugin: PluginBasicSettingsState,
    val cache: CacheBasicSettingsState,
)

private data class RuntimeCoreBasicSettingsState(
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
    private val pluginManager: PluginManager,
    private val cacheCleaner: SettingsCacheCleaner,
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    private val feedbackActionLock = Mutex()
    private val cacheActionLock = Mutex()

    private val _feedbackExportUiState = MutableStateFlow(FeedbackExportUiState())
    val feedbackExportUiState: StateFlow<FeedbackExportUiState> = _feedbackExportUiState.asStateFlow()

    private val _errorLogUiState = MutableStateFlow(ErrorLogUiState())
    val errorLogUiState: StateFlow<ErrorLogUiState> = _errorLogUiState.asStateFlow()

    private val _cacheActionState = MutableStateFlow(CacheActionState())

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

    val lyricAssociationType: StateFlow<LyricAssociationType> = appPreferences.lyricAssociationType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricAssociationType.Search)

    val showExitOnNotification: StateFlow<Boolean> = appPreferences.showExitOnNotification
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

    val allowConcurrentPlayback: StateFlow<Boolean> = appPreferences.allowConcurrentPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPlayWhenAppStart: StateFlow<Boolean> = appPreferences.autoPlayWhenAppStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tryChangeSourceWhenPlayFail: StateFlow<Boolean> = appPreferences.tryChangeSourceWhenPlayFail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStopWhenError: StateFlow<Boolean> = appPreferences.autoStopWhenError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val audioInterruptionAction: StateFlow<AudioInterruptionAction> = appPreferences.audioInterruptionAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioInterruptionAction.Pause)

    val audioInterruptionDuckVolume: StateFlow<Float> = appPreferences.audioInterruptionDuckVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

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

    val desktopLyricEnabled: StateFlow<Boolean> = appPreferences.desktopLyricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val desktopLyricAlignment: StateFlow<DesktopLyricAlignment> = appPreferences.desktopLyricAlignment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DesktopLyricAlignment.Center)

    val desktopLyricTopPercent: StateFlow<Float> = appPreferences.desktopLyricTopPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.08f)

    val desktopLyricLeftPercent: StateFlow<Float> = appPreferences.desktopLyricLeftPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.08f)

    val desktopLyricWidthPercent: StateFlow<Float> = appPreferences.desktopLyricWidthPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.84f)

    val desktopLyricFontSizeSp: StateFlow<Int> = appPreferences.desktopLyricFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18)

    val desktopLyricTextColor: StateFlow<String> = appPreferences.desktopLyricTextColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#FFFFFFFF")

    val desktopLyricBackgroundColor: StateFlow<String> = appPreferences.desktopLyricBackgroundColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#66000000")

    val autoUpdatePlugins: StateFlow<Boolean> = appPreferences.autoUpdatePlugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val skipPluginVersionCheck: StateFlow<Boolean> = appPreferences.skipPluginVersionCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lazyLoadPlugins: StateFlow<Boolean> = appPreferences.lazyLoadPlugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val maxMusicCacheSizeMb: StateFlow<Int> = appPreferences.maxMusicCacheSizeBytes
        .map { bytes -> (bytes / BYTES_PER_MB).toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 512)

    val debugErrorLogEnabled: StateFlow<Boolean> = appPreferences.debugErrorLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val debugTraceLogEnabled: StateFlow<Boolean> = appPreferences.debugTraceLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val debugDevLogEnabled: StateFlow<Boolean> = appPreferences.debugDevLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val commonBasicSettingsState = combine(
        maxSearchHistoryLength,
        musicDetailDefaultPage,
        musicDetailAwake,
        lyricAssociationType,
        showExitOnNotification,
    ) { maxSearchHistoryLength, musicDetailDefaultPage, musicDetailAwake, lyricAssociationType, showExitOnNotification ->
        CommonBasicSettingsState(
            maxSearchHistoryLength = maxSearchHistoryLength,
            musicDetailDefaultPage = musicDetailDefaultPage,
            musicDetailAwake = musicDetailAwake,
            lyricAssociationType = lyricAssociationType,
            showExitOnNotification = showExitOnNotification,
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

    private val playbackQualitySettingsState = combine(
        defaultPlayQuality,
        playQualityOrder,
    ) { defaultPlayQuality, playQualityOrder ->
        PlaybackQualitySettingsState(
            defaultPlayQuality = defaultPlayQuality,
            playQualityOrder = playQualityOrder,
        )
    }

    private val playbackFailureSettingsState = combine(
        allowConcurrentPlayback,
        autoPlayWhenAppStart,
        tryChangeSourceWhenPlayFail,
        autoStopWhenError,
    ) { allowConcurrentPlayback, autoPlayWhenAppStart, tryChangeSourceWhenPlayFail, autoStopWhenError ->
        PlaybackFailureSettingsState(
            allowConcurrentPlayback = allowConcurrentPlayback,
            autoPlayWhenAppStart = autoPlayWhenAppStart,
            tryChangeSourceWhenPlayFail = tryChangeSourceWhenPlayFail,
            autoStopWhenError = autoStopWhenError,
        )
    }

    private val audioInterruptionSettingsState = combine(
        audioInterruptionAction,
        audioInterruptionDuckVolume,
    ) { action, duckVolume ->
        AudioInterruptionSettingsState(
            action = action,
            duckVolume = duckVolume,
        )
    }

    private val playbackBasicSettingsState = combine(
        playbackQualitySettingsState,
        playbackFailureSettingsState,
        audioInterruptionSettingsState,
    ) { quality, failure, interruption ->
        PlaybackBasicSettingsState(
            defaultPlayQuality = quality.defaultPlayQuality,
            playQualityOrder = quality.playQualityOrder,
            allowConcurrentPlayback = failure.allowConcurrentPlayback,
            autoPlayWhenAppStart = failure.autoPlayWhenAppStart,
            tryChangeSourceWhenPlayFail = failure.tryChangeSourceWhenPlayFail,
            autoStopWhenError = failure.autoStopWhenError,
            audioInterruptionAction = interruption.action,
            audioInterruptionDuckVolume = interruption.duckVolume,
        )
    }

    private val pluginBasicSettingsState = combine(
        autoUpdatePlugins,
        skipPluginVersionCheck,
        lazyLoadPlugins,
    ) { autoUpdatePlugins, skipPluginVersionCheck, lazyLoadPlugins ->
        PluginBasicSettingsState(
            autoUpdatePlugins = autoUpdatePlugins,
            skipPluginVersionCheck = skipPluginVersionCheck,
            lazyLoadPlugins = lazyLoadPlugins,
        )
    }

    private val cacheBasicSettingsState = maxMusicCacheSizeMb.map { maxMusicCacheSizeMb ->
        CacheBasicSettingsState(maxMusicCacheSizeMb = maxMusicCacheSizeMb)
    }

    private val desktopLyricPositionSettingsState = combine(
        desktopLyricTopPercent,
        desktopLyricLeftPercent,
        desktopLyricWidthPercent,
    ) { topPercent, leftPercent, widthPercent ->
        Triple(topPercent, leftPercent, widthPercent)
    }

    private val desktopLyricStyleSettingsState = combine(
        desktopLyricFontSizeSp,
        desktopLyricTextColor,
        desktopLyricBackgroundColor,
    ) { fontSizeSp, textColor, backgroundColor ->
        Triple(fontSizeSp, textColor, backgroundColor)
    }

    private val lyricBasicSettingsState = combine(
        lyricAutoSearchEnabled,
        desktopLyricEnabled,
        desktopLyricAlignment,
        desktopLyricPositionSettingsState,
        desktopLyricStyleSettingsState,
    ) { autoSearchEnabled, desktopEnabled, alignment, position, style ->
        LyricBasicSettingsState(
            autoSearchEnabled = autoSearchEnabled,
            desktopEnabled = desktopEnabled,
            desktopAlignment = alignment,
            desktopTopPercent = position.first,
            desktopLeftPercent = position.second,
            desktopWidthPercent = position.third,
            desktopFontSizeSp = style.first,
            desktopTextColor = style.second,
            desktopBackgroundColor = style.third,
        )
    }

    private val developerBasicSettingsState = combine(
        debugErrorLogEnabled,
        debugTraceLogEnabled,
        debugDevLogEnabled,
    ) { errorLogEnabled, traceLogEnabled, devLogEnabled ->
        DeveloperBasicSettingsState(
            errorLogEnabled = errorLogEnabled,
            traceLogEnabled = traceLogEnabled,
            devLogEnabled = devLogEnabled,
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

    private val runtimeCoreBasicSettingsState = combine(
        commonBasicSettingsState,
        sheetAlbumBasicSettingsState,
        playbackBasicSettingsState,
        downloadBasicSettingsState,
        networkBasicSettingsState,
    ) { common, sheetAlbum, playback, download, network ->
        RuntimeCoreBasicSettingsState(
            common = common,
            sheetAlbum = sheetAlbum,
            playback = playback,
            download = download,
            network = network,
        )
    }

    private val runtimeBasicSettingsState = combine(
        runtimeCoreBasicSettingsState,
        pluginBasicSettingsState,
        cacheBasicSettingsState,
    ) { core, plugin, cache ->
        RuntimeBasicSettingsState(
            common = core.common,
            sheetAlbum = core.sheetAlbum,
            playback = core.playback,
            download = core.download,
            network = core.network,
            plugin = plugin,
            cache = cache,
        )
    }

    val basicSettingsUiState: StateFlow<BasicSettingsUiState> = combine(
        runtimeBasicSettingsState,
        lyricBasicSettingsState,
        developerBasicSettingsState,
        storageAccessState,
        _cacheActionState,
    ) { runtime, lyric, developer, storageAccessState, cacheActionState ->
        BasicSettingsUiState(
            maxSearchHistoryLength = runtime.common.maxSearchHistoryLength,
            musicDetailDefaultPage = runtime.common.musicDetailDefaultPage,
            musicDetailAwake = runtime.common.musicDetailAwake,
            lyricAssociationType = runtime.common.lyricAssociationType,
            showExitOnNotification = runtime.common.showExitOnNotification,
            clickMusicInSearch = runtime.sheetAlbum.clickMusicInSearch,
            clickMusicInAlbum = runtime.sheetAlbum.clickMusicInAlbum,
            musicOrderInLocalSheet = runtime.sheetAlbum.musicOrderInLocalSheet,
            defaultPlayQuality = runtime.playback.defaultPlayQuality,
            playQualityOrder = runtime.playback.playQualityOrder,
            allowConcurrentPlayback = runtime.playback.allowConcurrentPlayback,
            autoPlayWhenAppStart = runtime.playback.autoPlayWhenAppStart,
            tryChangeSourceWhenPlayFail = runtime.playback.tryChangeSourceWhenPlayFail,
            autoStopWhenError = runtime.playback.autoStopWhenError,
            audioInterruptionAction = runtime.playback.audioInterruptionAction,
            audioInterruptionDuckVolume = runtime.playback.audioInterruptionDuckVolume,
            maxDownload = runtime.download.maxDownload,
            defaultDownloadQuality = runtime.download.defaultDownloadQuality,
            downloadQualityOrder = runtime.download.downloadQualityOrder,
            useCellularPlay = runtime.network.useCellularPlay,
            useCellularDownload = runtime.network.useCellularDownload,
            lyricAutoSearchEnabled = lyric.autoSearchEnabled,
            desktopLyricEnabled = lyric.desktopEnabled,
            desktopLyricAlignment = lyric.desktopAlignment,
            desktopLyricTopPercent = lyric.desktopTopPercent,
            desktopLyricLeftPercent = lyric.desktopLeftPercent,
            desktopLyricWidthPercent = lyric.desktopWidthPercent,
            desktopLyricFontSizeSp = lyric.desktopFontSizeSp,
            desktopLyricTextColor = lyric.desktopTextColor,
            desktopLyricBackgroundColor = lyric.desktopBackgroundColor,
            autoUpdatePlugins = runtime.plugin.autoUpdatePlugins,
            skipPluginVersionCheck = runtime.plugin.skipPluginVersionCheck,
            lazyLoadPlugins = runtime.plugin.lazyLoadPlugins,
            maxMusicCacheSizeMb = runtime.cache.maxMusicCacheSizeMb,
            debugErrorLogEnabled = developer.errorLogEnabled,
            debugTraceLogEnabled = developer.traceLogEnabled,
            debugDevLogEnabled = developer.devLogEnabled,
            cacheActionInProgress = cacheActionState.inProgress,
            cacheActionMessage = cacheActionState.message,
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

    fun setLyricAssociationType(value: LyricAssociationType) = viewModelScope.launch {
        appPreferences.setLyricAssociationType(value)
    }

    fun setShowExitOnNotification(value: Boolean) = viewModelScope.launch {
        appPreferences.setShowExitOnNotification(value)
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

    fun setAllowConcurrentPlayback(value: Boolean) = viewModelScope.launch {
        appPreferences.setAllowConcurrentPlayback(value)
    }

    fun setAutoPlayWhenAppStart(value: Boolean) = viewModelScope.launch {
        appPreferences.setAutoPlayWhenAppStart(value)
    }

    fun setTryChangeSourceWhenPlayFail(value: Boolean) = viewModelScope.launch {
        appPreferences.setTryChangeSourceWhenPlayFail(value)
    }

    fun setAutoStopWhenError(value: Boolean) = viewModelScope.launch {
        appPreferences.setAutoStopWhenError(value)
    }

    fun setAudioInterruptionAction(value: AudioInterruptionAction) = viewModelScope.launch {
        appPreferences.setAudioInterruptionAction(value)
    }

    fun setAudioInterruptionDuckVolume(value: Float) = viewModelScope.launch {
        appPreferences.setAudioInterruptionDuckVolume(value)
    }

    fun setAutoUpdatePlugins(value: Boolean) = viewModelScope.launch {
        appPreferences.setAutoUpdatePlugins(value)
    }

    fun setSkipPluginVersionCheck(value: Boolean) = viewModelScope.launch {
        appPreferences.setSkipPluginVersionCheck(value)
    }

    fun setLazyLoadPlugins(value: Boolean) = viewModelScope.launch {
        appPreferences.setLazyLoadPlugins(value)
        pluginManager.reload()
    }

    fun setMaxMusicCacheSizeMb(value: Int) = viewModelScope.launch {
        appPreferences.setMaxMusicCacheSizeBytes(value.toLong() * BYTES_PER_MB)
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

    fun setDesktopLyricEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setDesktopLyricEnabled(value)
    }

    fun setDesktopLyricAlignment(value: DesktopLyricAlignment) = viewModelScope.launch {
        appPreferences.setDesktopLyricAlignment(value)
    }

    fun setDesktopLyricTopPercent(value: Float) = viewModelScope.launch {
        appPreferences.setDesktopLyricTopPercent(value)
    }

    fun setDesktopLyricLeftPercent(value: Float) = viewModelScope.launch {
        appPreferences.setDesktopLyricLeftPercent(value)
    }

    fun setDesktopLyricWidthPercent(value: Float) = viewModelScope.launch {
        appPreferences.setDesktopLyricWidthPercent(value)
    }

    fun setDesktopLyricFontSizeSp(value: Int) = viewModelScope.launch {
        appPreferences.setDesktopLyricFontSizeSp(value)
    }

    fun setDesktopLyricTextColor(value: String) = viewModelScope.launch {
        appPreferences.setDesktopLyricTextColor(value)
    }

    fun setDesktopLyricBackgroundColor(value: String) = viewModelScope.launch {
        appPreferences.setDesktopLyricBackgroundColor(value)
    }

    fun setDebugErrorLogEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setDebugErrorLogEnabled(value)
    }

    fun setDebugTraceLogEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setDebugTraceLogEnabled(value)
    }

    fun setDebugDevLogEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setDebugDevLogEnabled(value)
    }

    fun clearMusicCache() {
        runCacheAction(successMessage = "音乐缓存已清理") {
            cacheCleaner.clearMusicCache()
        }
    }

    fun clearLyricCache() {
        runCacheAction(successMessage = "歌词缓存已清理") {
            cacheCleaner.clearLyricCache()
        }
    }

    fun clearImageCache() {
        runCacheAction(successMessage = "图片缓存已清理") {
            cacheCleaner.clearImageCache()
        }
    }

    private fun runCacheAction(
        successMessage: String,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            if (!cacheActionLock.tryLock()) return@launch
            _cacheActionState.update { CacheActionState(inProgress = true, message = "清理中") }
            try {
                action()
                _cacheActionState.update { CacheActionState(message = successMessage) }
            } catch (error: CancellationException) {
                _cacheActionState.update { CacheActionState() }
                throw error
            } catch (error: Throwable) {
                MfLog.error(
                    category = LogCategory.SETTINGS,
                    event = "settings_cache_action_failed",
                    throwable = error,
                    fields = mapOf("message" to successMessage),
                )
                _cacheActionState.update { CacheActionState(message = "清理失败") }
            } finally {
                cacheActionLock.unlock()
            }
        }
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

    fun showErrorLog() {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                ReadableLogStore.readErrorLog()
            }.ifBlank {
                "暂无错误日志"
            }
            _errorLogUiState.update {
                ErrorLogUiState(
                    visible = true,
                    content = content,
                )
            }
        }
    }

    fun dismissErrorLog() {
        _errorLogUiState.update { ErrorLogUiState() }
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

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

private data class CacheActionState(
    val inProgress: Boolean = false,
    val message: String? = null,
)
