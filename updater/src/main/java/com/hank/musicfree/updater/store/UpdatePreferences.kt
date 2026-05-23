package com.hank.musicfree.updater.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hank.musicfree.updater.di.UpdaterDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    @UpdaterDataStore private val store: DataStore<Preferences>,
) {
    suspend fun getSkipVersion(): String? = store.data.first()[KEY_SKIP_VERSION]

    suspend fun setSkipVersion(version: String) {
        store.edit { it[KEY_SKIP_VERSION] = version }
    }

    suspend fun clearSkipVersion() {
        store.edit { it.remove(KEY_SKIP_VERSION) }
    }

    suspend fun getLastCheckedAt(): Long = store.data.first()[KEY_LAST_CHECKED_AT] ?: 0L

    suspend fun setLastCheckedAt(epochMillis: Long) {
        store.edit { it[KEY_LAST_CHECKED_AT] = epochMillis }
    }

    suspend fun getLastSeenVersion(): String? = store.data.first()[KEY_LAST_SEEN_VERSION]

    suspend fun setLastSeenVersion(version: String) {
        store.edit { it[KEY_LAST_SEEN_VERSION] = version }
    }

    val silentUpdateDownloadEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_SILENT_UPDATE_DOWNLOAD_ENABLED] ?: true
    }

    suspend fun setSilentUpdateDownloadEnabled(enabled: Boolean) {
        store.edit { it[KEY_SILENT_UPDATE_DOWNLOAD_ENABLED] = enabled }
    }

    suspend fun getSilentDownloadCanceledVersion(): String? =
        store.data.first()[KEY_SILENT_DOWNLOAD_CANCELED_VERSION]

    suspend fun setSilentDownloadCanceledVersion(version: String) {
        store.edit { it[KEY_SILENT_DOWNLOAD_CANCELED_VERSION] = version }
    }

    suspend fun clearSilentDownloadCanceledVersion() {
        store.edit { it.remove(KEY_SILENT_DOWNLOAD_CANCELED_VERSION) }
    }

    private companion object {
        val KEY_SKIP_VERSION = stringPreferencesKey("skip_version")
        val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val KEY_SILENT_UPDATE_DOWNLOAD_ENABLED = booleanPreferencesKey("silent_update_download_enabled")
        val KEY_SILENT_DOWNLOAD_CANCELED_VERSION = stringPreferencesKey("silent_download_canceled_version")
    }
}
