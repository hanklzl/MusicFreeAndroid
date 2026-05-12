package com.zili.android.musicfreeandroid.data.repository

/**
 * Abstraction between `PluginManager` (in `:plugin`) and the Room-backed plugin
 * metadata cache (in `:data`). Lives in `:data` because `:plugin → :data` is
 * the established dependency direction; placing the interface in `:plugin`
 * would force `:data` to depend on `:plugin` to bind the implementation, which
 * would create a module cycle.
 *
 * Lazy-load semantics (Phase E6):
 *  - [getAll] is called once at `PluginManager.setup()` time and the returned
 *    list seeds the in-memory plugin list with Initializing entries.
 *  - [upsert] is called whenever a plugin is fully evaluated (cache miss path,
 *    or post-update). It writes the snapshot keyed by file path.
 *  - [deleteByPath] is called on uninstall.
 */
interface PluginMetadataCacheGateway {
    suspend fun getAll(): List<CachedPluginMetadata>
    suspend fun getByPath(filePath: String): CachedPluginMetadata?
    suspend fun upsert(meta: CachedPluginMetadata)
    suspend fun deleteByPath(filePath: String)
    suspend fun deleteAll()
}

/**
 * Domain-level (non-Room) view of a cached plugin metadata row. Distinct from
 * the entity so callers don't need to JSON-encode list fields themselves.
 */
data class CachedPluginMetadata(
    val filePath: String,
    val platform: String,
    val version: String?,
    val hash: String,
    val srcUrl: String?,
    val appVersion: String?,
    val supportedMethods: Set<String>,
    val supportedSearchTypes: List<String>,
    val userVariableKeys: List<String>,
    val sourceMtimeMs: Long,
    val cachedAtAppVersion: String,
)
