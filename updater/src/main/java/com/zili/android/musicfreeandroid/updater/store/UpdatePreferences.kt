package com.zili.android.musicfreeandroid.updater.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zili.android.musicfreeandroid.updater.di.UpdaterDataStore
import kotlinx.coroutines.flow.first
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

    private companion object {
        val KEY_SKIP_VERSION = stringPreferencesKey("skip_version")
        val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
    }
}
