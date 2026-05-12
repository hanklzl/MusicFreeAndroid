package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.data.db.dao.PluginMetadataCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.PluginMetadataCacheEntity
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [PluginMetadataCacheGateway]. JSON-encodes
 * `supportedMethods` / `supportedSearchTypes` / `userVariableKeys` so the
 * underlying entity stays a plain string-typed row.
 */
@Singleton
class PluginMetadataCacheRepository @Inject constructor(
    private val dao: PluginMetadataCacheDao,
) : PluginMetadataCacheGateway {

    override suspend fun getAll(): List<CachedPluginMetadata> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getByPath(filePath: String): CachedPluginMetadata? =
        dao.getByPath(filePath)?.toDomain()

    override suspend fun upsert(meta: CachedPluginMetadata) {
        dao.upsert(meta.toEntity())
    }

    override suspend fun deleteByPath(filePath: String) {
        dao.deleteByPath(filePath)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun PluginMetadataCacheEntity.toDomain(): CachedPluginMetadata =
        CachedPluginMetadata(
            filePath = filePath,
            platform = platform,
            version = version,
            hash = hash,
            srcUrl = srcUrl,
            appVersion = appVersion,
            supportedMethods = JSONArray(supportedMethodsJson).toStringList().toSet(),
            supportedSearchTypes = JSONArray(supportedSearchTypesJson).toStringList(),
            userVariableKeys = JSONArray(userVariableKeysJson).toStringList(),
            sourceMtimeMs = sourceMtimeMs,
            cachedAtAppVersion = cachedAtAppVersion,
        )

    private fun CachedPluginMetadata.toEntity(): PluginMetadataCacheEntity =
        PluginMetadataCacheEntity(
            filePath = filePath,
            platform = platform,
            version = version,
            hash = hash,
            srcUrl = srcUrl,
            appVersion = appVersion,
            supportedMethodsJson = JSONArray(supportedMethods.toList()).toString(),
            supportedSearchTypesJson = JSONArray(supportedSearchTypes).toString(),
            userVariableKeysJson = JSONArray(userVariableKeys).toString(),
            sourceMtimeMs = sourceMtimeMs,
            cachedAtAppVersion = cachedAtAppVersion,
        )

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
