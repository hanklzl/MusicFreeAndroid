package com.hank.musicfree.data.db.entity

import androidx.room.Entity

/**
 * Persisted snapshot of a plugin's metadata so the cold-start path can list
 * plugins (and surface their declared platform / supportedMethods / userVariable
 * keys) WITHOUT loading the QuickJS engine for every plugin. The actual JS is
 * still evaluated on first use — see
 * `com.hank.musicfree.plugin.manager.PluginManager` Phase E
 * lazy-load path.
 *
 * Freshness:
 *  - [sourceMtimeMs] is the `lastModified()` of the on-disk .js file when the
 *    cache entry was written. A mismatch against current file mtime invalidates
 *    the cache row (treat it as a miss and re-evaluate the JS).
 *  - [cachedAtAppVersion] is the host `versionName` at write time. If the app
 *    is upgraded the cache row is treated as stale — the new host may bring a
 *    new appVersion gate verdict or new env values for `env.appVersion`.
 *
 * Schema-stored as JSON arrays of strings:
 *  - [supportedMethodsJson]: e.g. `["search","getMediaSource"]`
 *  - [supportedSearchTypesJson]: e.g. `["music","lyric"]`
 *  - [userVariableKeysJson]: e.g. `["cookie","token"]`
 *
 * Primary key is [filePath] — a plugin file in `context.filesDir/plugins/` is
 * uniquely identified by its absolute path.
 */
@Entity(tableName = "plugin_metadata_cache", primaryKeys = ["filePath"])
data class PluginMetadataCacheEntity(
    val filePath: String,
    val platform: String,
    val version: String?,
    val hash: String,
    val srcUrl: String?,
    val appVersion: String?,
    val supportedMethodsJson: String,
    val supportedSearchTypesJson: String,
    val userVariableKeysJson: String,
    val sourceMtimeMs: Long,
    val cachedAtAppVersion: String,
)
