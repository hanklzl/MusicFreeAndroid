package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.core.model.RepeatMode
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

    val storageDirectoryUri: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_STORAGE_DIRECTORY_URI]
    }

    suspend fun setStorageDirectoryUri(uri: String?) {
        dataStore.edit {
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
        dataStore.edit { it[KEY_LYRIC_AUTO_SEARCH_ENABLED] = enabled }
    }

    // ── Search History ──

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
            if (current.size > MAX_SEARCH_HISTORY) {
                current.subList(MAX_SEARCH_HISTORY, current.size).clear()
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
        dataStore.edit { it[KEY_MAX_DOWNLOAD] = value.coerceIn(1, 10) }
    }

    val useCellularDownload: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_CELLULAR_DOWNLOAD] ?: false
    }

    suspend fun setUseCellularDownload(value: Boolean) {
        dataStore.edit { it[KEY_USE_CELLULAR_DOWNLOAD] = value }
    }

    val defaultDownloadQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_DOWNLOAD_QUALITY]?.let { runCatching { PlayQuality.valueOf(it) }.getOrNull() }
            ?: PlayQuality.STANDARD
    }

    suspend fun setDefaultDownloadQuality(quality: PlayQuality) {
        dataStore.edit { it[KEY_DEFAULT_DOWNLOAD_QUALITY] = quality.name }
    }

    val downloadDirRelative: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_DIR_RELATIVE] ?: "Music/MusicFree/"
    }

    suspend fun setDownloadDirRelative(value: String) {
        dataStore.edit { it[KEY_DOWNLOAD_DIR_RELATIVE] = value }
    }

    private companion object {
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_PLAY_QUALITY = stringPreferencesKey("play_quality")
        val KEY_PLAY_RATE = floatPreferencesKey("play_rate")
        val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_CURRENT_MUSIC_INDEX = intPreferencesKey("current_music_index")
        val KEY_STORAGE_DIRECTORY_URI = stringPreferencesKey("storage_directory_uri")
        val KEY_LYRIC_SHOW_TRANSLATION = booleanPreferencesKey("lyric_show_translation")
        val KEY_LYRIC_DETAIL_FONT_SIZE = intPreferencesKey("lyric_detail_font_size")
        val KEY_LYRIC_AUTO_SEARCH_ENABLED = booleanPreferencesKey("lyric_auto_search_enabled")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        const val MAX_SEARCH_HISTORY = 20
        val KEY_MAX_DOWNLOAD = intPreferencesKey("max_download")
        val KEY_USE_CELLULAR_DOWNLOAD = booleanPreferencesKey("use_cellular_download")
        val KEY_DEFAULT_DOWNLOAD_QUALITY = stringPreferencesKey("default_download_quality")
        val KEY_DOWNLOAD_DIR_RELATIVE = stringPreferencesKey("download_dir_relative")
    }
}
