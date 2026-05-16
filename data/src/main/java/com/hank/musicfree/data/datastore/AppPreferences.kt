package com.hank.musicfree.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.DesktopLyricAlignment
import com.hank.musicfree.core.model.LyricAssociationType
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.MusicDetailDefaultPage
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val repeatMode: Flow<RepeatMode> = dataStore.data.map { prefs ->
        prefs[KEY_REPEAT_MODE]?.let { RepeatMode.valueOf(it) } ?: RepeatMode.OFF
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        dataStore.edit { it[KEY_REPEAT_MODE] = mode.name }
    }

    val playQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs[KEY_PLAY_QUALITY]?.let { PlayQuality.valueOf(it) } ?: PlayQuality.STANDARD
    }

    suspend fun setPlayQuality(quality: PlayQuality) {
        dataStore.edit { it[KEY_PLAY_QUALITY] = quality.name }
    }

    val playRate: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_PLAY_RATE] ?: PlaybackSpeeds.DEFAULT
    }

    suspend fun setPlayRate(rate: Float) {
        dataStore.edit { it[KEY_PLAY_RATE] = rate }
    }

    val shuffleEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHUFFLE_ENABLED] ?: false
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SHUFFLE_ENABLED] = enabled }
    }

    val darkMode: Flow<Boolean?> = dataStore.data.map { prefs ->
        if (prefs.contains(KEY_DARK_MODE)) prefs[KEY_DARK_MODE] else null
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        dataStore.edit {
            if (enabled == null) it.remove(KEY_DARK_MODE) else it[KEY_DARK_MODE] = enabled
        }
    }

    val currentMusicIndex: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_MUSIC_INDEX] ?: -1
    }

    suspend fun setCurrentMusicIndex(index: Int) {
        dataStore.edit { it[KEY_CURRENT_MUSIC_INDEX] = index }
    }

    val currentMusicPositionMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_MUSIC_POSITION_MS] ?: 0L
    }

    suspend fun setCurrentMusicPositionMs(positionMs: Long) {
        dataStore.edit { it[KEY_CURRENT_MUSIC_POSITION_MS] = positionMs.coerceAtLeast(0L) }
    }

    val currentMusicDurationMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_MUSIC_DURATION_MS] ?: 0L
    }

    suspend fun setCurrentMusicDurationMs(durationMs: Long) {
        dataStore.edit { it[KEY_CURRENT_MUSIC_DURATION_MS] = durationMs.coerceAtLeast(0L) }
    }

    val storageDirectoryUri: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_STORAGE_DIRECTORY_URI]
    }

    suspend fun setStorageDirectoryUri(uri: String?) {
        writeRuntimeSetting(KEY_STORAGE_DIRECTORY_URI, uri) {
            if (uri == null) {
                it.remove(KEY_STORAGE_DIRECTORY_URI)
            } else {
                it[KEY_STORAGE_DIRECTORY_URI] = uri
            }
        }
    }

    val lyricShowTranslation: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_SHOW_TRANSLATION] ?: false
    }

    suspend fun setLyricShowTranslation(enabled: Boolean) {
        dataStore.edit { it[KEY_LYRIC_SHOW_TRANSLATION] = enabled }
    }

    val lyricDetailFontSize: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_LYRIC_DETAIL_FONT_SIZE] ?: 1).coerceIn(0, 3)
    }

    suspend fun setLyricDetailFontSize(size: Int) {
        dataStore.edit { it[KEY_LYRIC_DETAIL_FONT_SIZE] = size.coerceIn(0, 3) }
    }

    val lyricAutoSearchEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_AUTO_SEARCH_ENABLED] ?: true
    }

    suspend fun setLyricAutoSearchEnabled(enabled: Boolean) {
        writeRuntimeSetting(KEY_LYRIC_AUTO_SEARCH_ENABLED, enabled) {
            it[KEY_LYRIC_AUTO_SEARCH_ENABLED] = enabled
        }
    }

    val desktopLyricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DESKTOP_LYRIC_ENABLED] ?: false
    }

    suspend fun setDesktopLyricEnabled(enabled: Boolean) {
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_ENABLED, enabled) {
            it[KEY_DESKTOP_LYRIC_ENABLED] = enabled
        }
    }

    val desktopLyricAlignment: Flow<DesktopLyricAlignment> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_DESKTOP_LYRIC_ALIGNMENT, DesktopLyricAlignment.Center)
    }

    suspend fun setDesktopLyricAlignment(value: DesktopLyricAlignment) {
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_ALIGNMENT, value.name) {
            it[KEY_DESKTOP_LYRIC_ALIGNMENT] = value.name
        }
    }

    val desktopLyricTopPercent: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_DESKTOP_LYRIC_TOP_PERCENT] ?: 0.08f).coerceIn(0f, 1f)
    }

    suspend fun setDesktopLyricTopPercent(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_TOP_PERCENT, coerced) {
            it[KEY_DESKTOP_LYRIC_TOP_PERCENT] = coerced
        }
    }

    val desktopLyricLeftPercent: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_DESKTOP_LYRIC_LEFT_PERCENT] ?: 0.08f).coerceIn(0f, 1f)
    }

    suspend fun setDesktopLyricLeftPercent(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_LEFT_PERCENT, coerced) {
            it[KEY_DESKTOP_LYRIC_LEFT_PERCENT] = coerced
        }
    }

    val desktopLyricWidthPercent: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_DESKTOP_LYRIC_WIDTH_PERCENT] ?: 0.84f).coerceIn(0.2f, 1f)
    }

    suspend fun setDesktopLyricWidthPercent(value: Float) {
        val coerced = value.coerceIn(0.2f, 1f)
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_WIDTH_PERCENT, coerced) {
            it[KEY_DESKTOP_LYRIC_WIDTH_PERCENT] = coerced
        }
    }

    val desktopLyricFontSizeSp: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_DESKTOP_LYRIC_FONT_SIZE_SP] ?: 18).coerceIn(12, 32)
    }

    suspend fun setDesktopLyricFontSizeSp(value: Int) {
        val coerced = value.coerceIn(12, 32)
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_FONT_SIZE_SP, coerced) {
            it[KEY_DESKTOP_LYRIC_FONT_SIZE_SP] = coerced
        }
    }

    val desktopLyricTextColor: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DESKTOP_LYRIC_TEXT_COLOR] ?: "#FFFFFFFF"
    }

    suspend fun setDesktopLyricTextColor(value: String) {
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_TEXT_COLOR, value) {
            it[KEY_DESKTOP_LYRIC_TEXT_COLOR] = value
        }
    }

    val desktopLyricBackgroundColor: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DESKTOP_LYRIC_BACKGROUND_COLOR] ?: "#66000000"
    }

    suspend fun setDesktopLyricBackgroundColor(value: String) {
        writeRuntimeSetting(KEY_DESKTOP_LYRIC_BACKGROUND_COLOR, value) {
            it[KEY_DESKTOP_LYRIC_BACKGROUND_COLOR] = value
        }
    }

    // ── Search History ──

    val maxSearchHistoryLength: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_MAX_SEARCH_HISTORY_LENGTH] ?: DEFAULT_MAX_SEARCH_HISTORY).coerceIn(1, 500)
    }

    suspend fun setMaxSearchHistoryLength(value: Int) {
        val coerced = value.coerceIn(1, 500)
        writeRuntimeSetting(KEY_MAX_SEARCH_HISTORY_LENGTH, coerced) {
            it[KEY_MAX_SEARCH_HISTORY_LENGTH] = coerced
        }
    }

    val searchHistory: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_HISTORY]
            ?.split("\u001F")  // Unit Separator 作为分隔符，避免逗号在搜索词中出现
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_SEARCH_HISTORY]
                ?.split("\u001F")
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
            current.remove(query)  // 去重
            current.add(0, query)  // 最新置顶
            val maxHistory = (prefs[KEY_MAX_SEARCH_HISTORY_LENGTH] ?: DEFAULT_MAX_SEARCH_HISTORY)
                .coerceIn(1, 500)
            if (current.size > maxHistory) {
                current.subList(maxHistory, current.size).clear()
            }
            prefs[KEY_SEARCH_HISTORY] = current.joinToString("\u001F")
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { it.remove(KEY_SEARCH_HISTORY) }
    }

    // ── Download Settings ──

    val maxDownload: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_MAX_DOWNLOAD] ?: 3).coerceIn(1, 10)
    }

    suspend fun setMaxDownload(value: Int) {
        val coerced = value.coerceIn(1, 10)
        writeRuntimeSetting(KEY_MAX_DOWNLOAD, coerced) {
            it[KEY_MAX_DOWNLOAD] = coerced
        }
    }

    val useCellularDownload: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_CELLULAR_DOWNLOAD] ?: false
    }

    suspend fun setUseCellularDownload(value: Boolean) {
        writeRuntimeSetting(KEY_USE_CELLULAR_DOWNLOAD, value) {
            it[KEY_USE_CELLULAR_DOWNLOAD] = value
        }
    }

    val defaultDownloadQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_DOWNLOAD_QUALITY]?.let { runCatching { PlayQuality.valueOf(it) }.getOrNull() }
            ?: PlayQuality.STANDARD
    }

    suspend fun setDefaultDownloadQuality(quality: PlayQuality) {
        writeRuntimeSetting(KEY_DEFAULT_DOWNLOAD_QUALITY, quality.name) {
            it[KEY_DEFAULT_DOWNLOAD_QUALITY] = quality.name
        }
    }

    val downloadQualityOrder: Flow<QualityFallbackOrder> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_DOWNLOAD_QUALITY_ORDER, QualityFallbackOrder.Asc)
    }

    suspend fun setDownloadQualityOrder(order: QualityFallbackOrder) {
        writeRuntimeSetting(KEY_DOWNLOAD_QUALITY_ORDER, order.name) {
            it[KEY_DOWNLOAD_QUALITY_ORDER] = order.name
        }
    }

    val downloadDirRelative: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_DIR_RELATIVE] ?: "Music/MusicFree/"
    }

    suspend fun setDownloadDirRelative(value: String) {
        writeRuntimeSetting(KEY_DOWNLOAD_DIR_RELATIVE, value) {
            it[KEY_DOWNLOAD_DIR_RELATIVE] = value
        }
    }

    // ── Basic Settings ──

    val musicDetailDefaultPage: Flow<MusicDetailDefaultPage> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_MUSIC_DETAIL_DEFAULT_PAGE, MusicDetailDefaultPage.Album)
    }

    suspend fun setMusicDetailDefaultPage(value: MusicDetailDefaultPage) {
        writeRuntimeSetting(KEY_MUSIC_DETAIL_DEFAULT_PAGE, value.name) {
            it[KEY_MUSIC_DETAIL_DEFAULT_PAGE] = value.name
        }
    }

    val musicDetailAwake: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MUSIC_DETAIL_AWAKE] ?: false
    }

    suspend fun setMusicDetailAwake(value: Boolean) {
        writeRuntimeSetting(KEY_MUSIC_DETAIL_AWAKE, value) {
            it[KEY_MUSIC_DETAIL_AWAKE] = value
        }
    }

    val lyricAssociationType: Flow<LyricAssociationType> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_LYRIC_ASSOCIATION_TYPE, LyricAssociationType.Search)
    }

    suspend fun setLyricAssociationType(value: LyricAssociationType) {
        writeRuntimeSetting(KEY_LYRIC_ASSOCIATION_TYPE, value.name) {
            it[KEY_LYRIC_ASSOCIATION_TYPE] = value.name
        }
    }

    val showExitOnNotification: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_EXIT_ON_NOTIFICATION] ?: false
    }

    suspend fun setShowExitOnNotification(value: Boolean) {
        writeRuntimeSetting(KEY_SHOW_EXIT_ON_NOTIFICATION, value) {
            it[KEY_SHOW_EXIT_ON_NOTIFICATION] = value
        }
    }

    val clickMusicInSearch: Flow<SearchResultClickAction> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_CLICK_MUSIC_IN_SEARCH, SearchResultClickAction.PlayMusic)
    }

    suspend fun setClickMusicInSearch(value: SearchResultClickAction) {
        writeRuntimeSetting(KEY_CLICK_MUSIC_IN_SEARCH, value.name) {
            it[KEY_CLICK_MUSIC_IN_SEARCH] = value.name
        }
    }

    val clickMusicInAlbum: Flow<AlbumMusicClickAction> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_CLICK_MUSIC_IN_ALBUM, AlbumMusicClickAction.PlayAlbum)
    }

    suspend fun setClickMusicInAlbum(value: AlbumMusicClickAction) {
        writeRuntimeSetting(KEY_CLICK_MUSIC_IN_ALBUM, value.name) {
            it[KEY_CLICK_MUSIC_IN_ALBUM] = value.name
        }
    }

    val musicOrderInLocalSheet: Flow<SortMode> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_MUSIC_ORDER_IN_LOCAL_SHEET, SortMode.Manual)
    }

    suspend fun setMusicOrderInLocalSheet(value: SortMode) {
        writeRuntimeSetting(KEY_MUSIC_ORDER_IN_LOCAL_SHEET, value.name) {
            it[KEY_MUSIC_ORDER_IN_LOCAL_SHEET] = value.name
        }
    }

    val defaultPlayQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_DEFAULT_PLAY_QUALITY, PlayQuality.STANDARD)
    }

    suspend fun setDefaultPlayQuality(quality: PlayQuality) {
        writeRuntimeSetting(KEY_DEFAULT_PLAY_QUALITY, quality.name) {
            it[KEY_DEFAULT_PLAY_QUALITY] = quality.name
        }
    }

    val playQualityOrder: Flow<QualityFallbackOrder> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_PLAY_QUALITY_ORDER, QualityFallbackOrder.Asc)
    }

    suspend fun setPlayQualityOrder(order: QualityFallbackOrder) {
        writeRuntimeSetting(KEY_PLAY_QUALITY_ORDER, order.name) {
            it[KEY_PLAY_QUALITY_ORDER] = order.name
        }
    }

    val useCellularPlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_CELLULAR_PLAY] ?: false
    }

    suspend fun setUseCellularPlay(value: Boolean) {
        writeRuntimeSetting(KEY_USE_CELLULAR_PLAY, value) {
            it[KEY_USE_CELLULAR_PLAY] = value
        }
    }

    val allowConcurrentPlayback: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_CONCURRENT_PLAYBACK] ?: false
    }

    suspend fun setAllowConcurrentPlayback(value: Boolean) {
        writeRuntimeSetting(KEY_ALLOW_CONCURRENT_PLAYBACK, value) {
            it[KEY_ALLOW_CONCURRENT_PLAYBACK] = value
        }
    }

    val autoPlayWhenAppStart: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PLAY_WHEN_APP_START] ?: false
    }

    suspend fun setAutoPlayWhenAppStart(value: Boolean) {
        writeRuntimeSetting(KEY_AUTO_PLAY_WHEN_APP_START, value) {
            it[KEY_AUTO_PLAY_WHEN_APP_START] = value
        }
    }

    val tryChangeSourceWhenPlayFail: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TRY_CHANGE_SOURCE_WHEN_PLAY_FAIL] ?: false
    }

    suspend fun setTryChangeSourceWhenPlayFail(value: Boolean) {
        writeRuntimeSetting(KEY_TRY_CHANGE_SOURCE_WHEN_PLAY_FAIL, value) {
            it[KEY_TRY_CHANGE_SOURCE_WHEN_PLAY_FAIL] = value
        }
    }

    val autoStopWhenError: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_STOP_WHEN_ERROR] ?: false
    }

    suspend fun setAutoStopWhenError(value: Boolean) {
        writeRuntimeSetting(KEY_AUTO_STOP_WHEN_ERROR, value) {
            it[KEY_AUTO_STOP_WHEN_ERROR] = value
        }
    }

    val audioInterruptionAction: Flow<AudioInterruptionAction> = dataStore.data.map { prefs ->
        prefs.enumValue(KEY_AUDIO_INTERRUPTION_ACTION, AudioInterruptionAction.Pause)
    }

    suspend fun setAudioInterruptionAction(value: AudioInterruptionAction) {
        writeRuntimeSetting(KEY_AUDIO_INTERRUPTION_ACTION, value.name) {
            it[KEY_AUDIO_INTERRUPTION_ACTION] = value.name
        }
    }

    val audioInterruptionDuckVolume: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_AUDIO_INTERRUPTION_DUCK_VOLUME] ?: 0.5f).coerceIn(0.1f, 1.0f)
    }

    suspend fun setAudioInterruptionDuckVolume(value: Float) {
        val coerced = value.coerceIn(0.1f, 1.0f)
        writeRuntimeSetting(KEY_AUDIO_INTERRUPTION_DUCK_VOLUME, coerced) {
            it[KEY_AUDIO_INTERRUPTION_DUCK_VOLUME] = coerced
        }
    }

    val maxMusicCacheSizeBytes: Flow<Long> = dataStore.data.map { prefs ->
        (prefs[KEY_MAX_MUSIC_CACHE_SIZE_BYTES] ?: DEFAULT_MAX_MUSIC_CACHE_SIZE_BYTES)
            .coerceIn(MIN_MUSIC_CACHE_SIZE_BYTES, MAX_MUSIC_CACHE_SIZE_BYTES)
    }

    suspend fun setMaxMusicCacheSizeBytes(value: Long) {
        val coerced = value.coerceIn(MIN_MUSIC_CACHE_SIZE_BYTES, MAX_MUSIC_CACHE_SIZE_BYTES)
        writeRuntimeSetting(KEY_MAX_MUSIC_CACHE_SIZE_BYTES, coerced) {
            it[KEY_MAX_MUSIC_CACHE_SIZE_BYTES] = coerced
        }
    }

    // ── Plugin Settings ──

    val autoUpdatePlugins: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_UPDATE_PLUGINS] ?: false
    }

    suspend fun setAutoUpdatePlugins(value: Boolean) {
        writeRuntimeSetting(KEY_AUTO_UPDATE_PLUGINS, value) {
            it[KEY_AUTO_UPDATE_PLUGINS] = value
        }
    }

    val skipPluginVersionCheck: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SKIP_PLUGIN_VERSION_CHECK] ?: false
    }

    suspend fun setSkipPluginVersionCheck(value: Boolean) {
        writeRuntimeSetting(KEY_SKIP_PLUGIN_VERSION_CHECK, value) {
            it[KEY_SKIP_PLUGIN_VERSION_CHECK] = value
        }
    }

    val pluginAutoUpdateLastAtEpochMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_PLUGIN_AUTO_UPDATE_LAST_AT_EPOCH_MS] ?: 0L
    }

    suspend fun setPluginAutoUpdateLastAtEpochMs(value: Long) {
        writeRuntimeSetting(KEY_PLUGIN_AUTO_UPDATE_LAST_AT_EPOCH_MS, value) {
            it[KEY_PLUGIN_AUTO_UPDATE_LAST_AT_EPOCH_MS] = value
        }
    }

    /**
     * When true, `PluginManager.loadAllPlugins()` seeds the entry list
     * from the [PluginMetadataCacheGateway] snapshot and defers JS evaluation
     * to a background coroutine — cold-start cost is bounded by file metadata
     * read instead of N × QuickJS context creation. When false, plugins load
     * synchronously like the pre-Phase-E behaviour. RN keeps this disabled by
     * default, so Android exposes the same default in basic settings.
     */
    val lazyLoadPlugins: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LAZY_LOAD_PLUGINS] ?: false
    }

    suspend fun setLazyLoadPlugins(value: Boolean) {
        writeRuntimeSetting(KEY_LAZY_LOAD_PLUGINS, value) {
            it[KEY_LAZY_LOAD_PLUGINS] = value
        }
    }

    val debugErrorLogEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_ERROR_LOG_ENABLED] ?: true
    }

    suspend fun setDebugErrorLogEnabled(value: Boolean) {
        writeRuntimeSetting(KEY_DEBUG_ERROR_LOG_ENABLED, value) {
            it[KEY_DEBUG_ERROR_LOG_ENABLED] = value
        }
    }

    val debugTraceLogEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_TRACE_LOG_ENABLED] ?: true
    }

    suspend fun setDebugTraceLogEnabled(value: Boolean) {
        writeRuntimeSetting(KEY_DEBUG_TRACE_LOG_ENABLED, value) {
            it[KEY_DEBUG_TRACE_LOG_ENABLED] = value
        }
    }

    val debugDevLogEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_DEV_LOG_ENABLED] ?: false
    }

    suspend fun setDebugDevLogEnabled(value: Boolean) {
        writeRuntimeSetting(KEY_DEBUG_DEV_LOG_ENABLED, value) {
            it[KEY_DEBUG_DEV_LOG_ENABLED] = value
        }
    }

    // ── Theme ──

    val selectedTheme: Flow<SelectedTheme> = dataStore.data.map { prefs ->
        SelectedTheme.fromStorageKey(prefs[KEY_SELECTED_THEME])
    }

    suspend fun setSelectedTheme(value: SelectedTheme) {
        writeRuntimeSetting(KEY_SELECTED_THEME, value.storageKey) {
            it[KEY_SELECTED_THEME] = value.storageKey
        }
    }

    val customColorsJson: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_COLORS_JSON]
    }

    suspend fun setCustomColorsJson(value: String?) {
        writeRuntimeSetting(KEY_CUSTOM_COLORS_JSON, value) {
            if (value.isNullOrBlank()) {
                it.remove(KEY_CUSTOM_COLORS_JSON)
            } else {
                it[KEY_CUSTOM_COLORS_JSON] = value
            }
        }
    }

    val themeBackgroundUrl: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_BACKGROUND_URL]
    }

    suspend fun setThemeBackgroundUrl(value: String?) {
        writeRuntimeSetting(KEY_THEME_BACKGROUND_URL, value) {
            if (value.isNullOrBlank()) {
                it.remove(KEY_THEME_BACKGROUND_URL)
            } else {
                it[KEY_THEME_BACKGROUND_URL] = value
            }
        }
    }

    val themeBackgroundBlur: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_THEME_BACKGROUND_BLUR] ?: DEFAULT_THEME_BACKGROUND_BLUR)
            .coerceIn(MIN_THEME_BACKGROUND_BLUR, MAX_THEME_BACKGROUND_BLUR)
    }

    suspend fun setThemeBackgroundBlur(value: Float) {
        val coerced = value.coerceIn(MIN_THEME_BACKGROUND_BLUR, MAX_THEME_BACKGROUND_BLUR)
        writeRuntimeSetting(KEY_THEME_BACKGROUND_BLUR, coerced) {
            it[KEY_THEME_BACKGROUND_BLUR] = coerced
        }
    }

    val themeBackgroundOpacity: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_THEME_BACKGROUND_OPACITY] ?: DEFAULT_THEME_BACKGROUND_OPACITY)
            .coerceIn(MIN_THEME_BACKGROUND_OPACITY, MAX_THEME_BACKGROUND_OPACITY)
    }

    suspend fun setThemeBackgroundOpacity(value: Float) {
        val coerced = value.coerceIn(MIN_THEME_BACKGROUND_OPACITY, MAX_THEME_BACKGROUND_OPACITY)
        writeRuntimeSetting(KEY_THEME_BACKGROUND_OPACITY, coerced) {
            it[KEY_THEME_BACKGROUND_OPACITY] = coerced
        }
    }

    val themeFollowSystem: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_FOLLOW_SYSTEM] ?: false
    }

    suspend fun setThemeFollowSystem(value: Boolean) {
        writeRuntimeSetting(KEY_THEME_FOLLOW_SYSTEM, value) {
            it[KEY_THEME_FOLLOW_SYSTEM] = value
        }
    }

    private suspend fun writeRuntimeSetting(
        key: Preferences.Key<*>,
        value: Any?,
        block: (MutablePreferences) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        try {
            dataStore.edit { prefs -> block(prefs) }
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "settings_write",
                fields = mapOf(
                    "key" to key.name,
                    "value" to value,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "settings_write",
                fields = mapOf(
                    "key" to key.name,
                    "value" to value,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SETTINGS,
                event = "settings_write",
                throwable = error,
                fields = mapOf(
                    "key" to key.name,
                    "value" to value,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw error
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_PLAY_QUALITY = stringPreferencesKey("play_quality")
        val KEY_PLAY_RATE = floatPreferencesKey("play_rate")
        val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_CURRENT_MUSIC_INDEX = intPreferencesKey("current_music_index")
        val KEY_CURRENT_MUSIC_POSITION_MS = longPreferencesKey("current_music_position_ms")
        val KEY_CURRENT_MUSIC_DURATION_MS = longPreferencesKey("current_music_duration_ms")
        val KEY_STORAGE_DIRECTORY_URI = stringPreferencesKey("storage_directory_uri")
        val KEY_LYRIC_SHOW_TRANSLATION = booleanPreferencesKey("lyric_show_translation")
        val KEY_LYRIC_DETAIL_FONT_SIZE = intPreferencesKey("lyric_detail_font_size")
        val KEY_LYRIC_AUTO_SEARCH_ENABLED = booleanPreferencesKey("lyric_auto_search_enabled")
        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_DESKTOP_LYRIC_ALIGNMENT = stringPreferencesKey("desktop_lyric_alignment")
        val KEY_DESKTOP_LYRIC_TOP_PERCENT = floatPreferencesKey("desktop_lyric_top_percent")
        val KEY_DESKTOP_LYRIC_LEFT_PERCENT = floatPreferencesKey("desktop_lyric_left_percent")
        val KEY_DESKTOP_LYRIC_WIDTH_PERCENT = floatPreferencesKey("desktop_lyric_width_percent")
        val KEY_DESKTOP_LYRIC_FONT_SIZE_SP = intPreferencesKey("desktop_lyric_font_size_sp")
        val KEY_DESKTOP_LYRIC_TEXT_COLOR = stringPreferencesKey("desktop_lyric_text_color")
        val KEY_DESKTOP_LYRIC_BACKGROUND_COLOR = stringPreferencesKey("desktop_lyric_background_color")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_MAX_SEARCH_HISTORY_LENGTH = intPreferencesKey("max_search_history_length")
        const val DEFAULT_MAX_SEARCH_HISTORY = 50
        val KEY_MAX_DOWNLOAD = intPreferencesKey("max_download")
        val KEY_USE_CELLULAR_DOWNLOAD = booleanPreferencesKey("use_cellular_download")
        val KEY_DEFAULT_DOWNLOAD_QUALITY = stringPreferencesKey("default_download_quality")
        val KEY_DOWNLOAD_QUALITY_ORDER = stringPreferencesKey("download_quality_order")
        val KEY_DOWNLOAD_DIR_RELATIVE = stringPreferencesKey("download_dir_relative")
        val KEY_MUSIC_DETAIL_DEFAULT_PAGE = stringPreferencesKey("music_detail_default_page")
        val KEY_MUSIC_DETAIL_AWAKE = booleanPreferencesKey("music_detail_awake")
        val KEY_LYRIC_ASSOCIATION_TYPE = stringPreferencesKey("lyric_association_type")
        val KEY_SHOW_EXIT_ON_NOTIFICATION = booleanPreferencesKey("show_exit_on_notification")
        val KEY_CLICK_MUSIC_IN_SEARCH = stringPreferencesKey("click_music_in_search")
        val KEY_CLICK_MUSIC_IN_ALBUM = stringPreferencesKey("click_music_in_album")
        val KEY_MUSIC_ORDER_IN_LOCAL_SHEET = stringPreferencesKey("music_order_in_local_sheet")
        val KEY_DEFAULT_PLAY_QUALITY = stringPreferencesKey("default_play_quality")
        val KEY_PLAY_QUALITY_ORDER = stringPreferencesKey("play_quality_order")
        val KEY_USE_CELLULAR_PLAY = booleanPreferencesKey("use_cellular_play")
        val KEY_ALLOW_CONCURRENT_PLAYBACK = booleanPreferencesKey("allow_concurrent_playback")
        val KEY_AUTO_PLAY_WHEN_APP_START = booleanPreferencesKey("auto_play_when_app_start")
        val KEY_TRY_CHANGE_SOURCE_WHEN_PLAY_FAIL = booleanPreferencesKey("try_change_source_when_play_fail")
        val KEY_AUTO_STOP_WHEN_ERROR = booleanPreferencesKey("auto_stop_when_error")
        val KEY_AUDIO_INTERRUPTION_ACTION = stringPreferencesKey("audio_interruption_action")
        val KEY_AUDIO_INTERRUPTION_DUCK_VOLUME = floatPreferencesKey("audio_interruption_duck_volume")
        val KEY_MAX_MUSIC_CACHE_SIZE_BYTES = longPreferencesKey("max_music_cache_size_bytes")
        const val DEFAULT_MAX_MUSIC_CACHE_SIZE_BYTES = 512L * 1024L * 1024L
        const val MIN_MUSIC_CACHE_SIZE_BYTES = 100L * 1024L * 1024L
        const val MAX_MUSIC_CACHE_SIZE_BYTES = 8192L * 1024L * 1024L
        val KEY_AUTO_UPDATE_PLUGINS = booleanPreferencesKey("auto_update_plugins")
        val KEY_SKIP_PLUGIN_VERSION_CHECK = booleanPreferencesKey("skip_plugin_version_check")
        val KEY_PLUGIN_AUTO_UPDATE_LAST_AT_EPOCH_MS = longPreferencesKey("plugin_auto_update_last_at_epoch_ms")
        val KEY_LAZY_LOAD_PLUGINS = booleanPreferencesKey("pref_lazy_load_plugins")
        val KEY_DEBUG_ERROR_LOG_ENABLED = booleanPreferencesKey("debug_error_log_enabled")
        val KEY_DEBUG_TRACE_LOG_ENABLED = booleanPreferencesKey("debug_trace_log_enabled")
        val KEY_DEBUG_DEV_LOG_ENABLED = booleanPreferencesKey("debug_dev_log_enabled")
        val KEY_SELECTED_THEME = stringPreferencesKey("selected_theme")
        val KEY_CUSTOM_COLORS_JSON = stringPreferencesKey("custom_colors_json")
        val KEY_THEME_BACKGROUND_URL = stringPreferencesKey("theme_background_url")
        val KEY_THEME_BACKGROUND_BLUR = floatPreferencesKey("theme_background_blur")
        val KEY_THEME_BACKGROUND_OPACITY = floatPreferencesKey("theme_background_opacity")
        val KEY_THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")
        const val DEFAULT_THEME_BACKGROUND_BLUR = 20f
        const val MIN_THEME_BACKGROUND_BLUR = 0f
        const val MAX_THEME_BACKGROUND_BLUR = 30f
        const val DEFAULT_THEME_BACKGROUND_OPACITY = 0.7f
        const val MIN_THEME_BACKGROUND_OPACITY = 0.3f
        const val MAX_THEME_BACKGROUND_OPACITY = 1f
    }
}

private inline fun <reified T : Enum<T>> Preferences.enumValue(
    key: Preferences.Key<String>,
    defaultValue: T,
): T = this[key]?.let { raw ->
    runCatching { enumValueOf<T>(raw) }.getOrNull()
} ?: defaultValue
