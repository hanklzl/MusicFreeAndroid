package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zili.android.musicfreeandroid.core.model.PlayQuality
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

    private companion object {
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_PLAY_QUALITY = stringPreferencesKey("play_quality")
        val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_CURRENT_MUSIC_INDEX = intPreferencesKey("current_music_index")
    }
}
