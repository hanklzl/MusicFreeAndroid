package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.core.local.Mp3Metadata
import com.zili.android.musicfreeandroid.core.local.Mp3MetadataReader
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.dao.LyricCacheDao
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import com.zili.android.musicfreeandroid.data.repository.CachedPluginMetadata
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.data.repository.MediaCacheRepository
import com.zili.android.musicfreeandroid.data.repository.PluginMetadataCacheGateway
import com.zili.android.musicfreeandroid.plugin.local.LocalFilePlugin
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory no-op DAOs / repositories for instrumentation tests that exercise
 * PluginManager orchestration. PluginManager.uninstall() now invokes
 * platform-scoped delete on a MediaCacheRepository, LyricRepository and
 * DownloadedTrackDao; these stubs satisfy the contract without depending on
 * a Room database in instrumentation channel tests.
 */
internal class StubMediaCacheDao : MediaCacheDao {
    override suspend fun get(platform: String, id: String): MediaCacheEntity? = null
    override suspend fun upsert(entity: MediaCacheEntity) = Unit
    override suspend fun count(): Int = 0
    override suspend fun deleteOldest(n: Int) = Unit
    override suspend fun deleteByPlatform(platform: String) = Unit
    override suspend fun delete(platform: String, id: String) = Unit
}

internal class StubLyricCacheDao : LyricCacheDao {
    override fun observeByKey(platform: String, id: String): Flow<LyricCacheEntity?> = flowOf(null)
    override suspend fun getByKey(platform: String, id: String): LyricCacheEntity? = null
    override suspend fun upsert(entity: LyricCacheEntity) = Unit
    override suspend fun insertIgnore(entity: LyricCacheEntity): Long = -1L
    override suspend fun saveRemoteLyric(
        platform: String,
        id: String,
        remoteRawLrc: String?,
        remoteRawLrcTxt: String?,
        remoteTranslation: String?,
        remoteSourceType: String?,
        remoteSourcePlatform: String?,
        remoteSourceMusicId: String?,
        remoteSourceTitle: String?,
        updatedAt: Long,
    ) = Unit
    override suspend fun setAssociation(
        platform: String,
        id: String,
        associatedMusicJson: String?,
        updatedAt: Long,
    ) = Unit
    override suspend fun clearAssociation(platform: String, id: String, updatedAt: Long) = Unit
    override suspend fun deleteLocalLyrics(platform: String, id: String, updatedAt: Long) = Unit
    override suspend fun setLocalRawLyric(platform: String, id: String, raw: String, updatedAt: Long) = Unit
    override suspend fun setLocalTranslation(platform: String, id: String, translation: String, updatedAt: Long) = Unit
    override suspend fun setOffset(platform: String, id: String, offsetMs: Long, updatedAt: Long) = Unit
    override suspend fun deleteByPlatform(platform: String) = Unit
}

internal class StubDownloadedTrackDao : DownloadedTrackDao {
    override suspend fun insert(entity: DownloadedTrackEntity) = Unit
    override suspend fun exists(id: String, platform: String): Boolean = false
    override suspend fun findUri(id: String, platform: String): String? = null
    override suspend fun get(id: String, platform: String): DownloadedTrackEntity? = null
    override suspend fun deleteByKey(id: String, platform: String) = Unit
    override suspend fun deleteByPlatform(platform: String) = Unit
    override fun observeKeys(): Flow<List<String>> = flowOf(emptyList())
}

internal fun stubMediaCacheRepository(): MediaCacheRepository =
    MediaCacheRepository(StubMediaCacheDao())

internal fun stubLyricRepository(): LyricRepository =
    LyricRepository(StubLyricCacheDao(), Converters())

internal fun stubDownloadedTrackDao(): DownloadedTrackDao = StubDownloadedTrackDao()

internal class StubMp3MetadataReader : Mp3MetadataReader {
    override suspend fun read(path: String): Mp3Metadata? = null
}

internal fun stubLocalFilePlugin(): LocalFilePlugin =
    LocalFilePlugin(StubMp3MetadataReader())

/**
 * In-memory [PluginMetadataCacheGateway] for instrumentation tests. Backed by a
 * thread-safe map keyed by `filePath`. Tests inspect the map via [snapshot].
 */
internal class InMemoryPluginMetadataCacheGateway : PluginMetadataCacheGateway {
    private val store = ConcurrentHashMap<String, CachedPluginMetadata>()
    override suspend fun getAll(): List<CachedPluginMetadata> = store.values.toList()
    override suspend fun getByPath(filePath: String): CachedPluginMetadata? = store[filePath]
    override suspend fun upsert(meta: CachedPluginMetadata) { store[meta.filePath] = meta }
    override suspend fun deleteByPath(filePath: String) { store.remove(filePath) }
    override suspend fun deleteAll() { store.clear() }
    fun snapshot(): Map<String, CachedPluginMetadata> = store.toMap()
    fun seed(meta: CachedPluginMetadata) { store[meta.filePath] = meta }
}

/**
 * Single canonical factory for instrumentation tests that need a real
 * [PluginManager] with stub data layer + a per-test DataStore. Keeps the
 * constructor cascade contained to one helper so adding new dependencies to
 * [PluginManager] doesn't require touching every androidTest class.
 *
 * Each call returns a fresh manager with an isolated `PreferenceDataStore` so
 * tests can't observe writes from sibling tests (rule-datastore-per-instance-isolation).
 */
internal fun makeTestManager(
    prefix: String = "plugin-it",
    appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
    appVersion: String = "1.0.0",
    metadataCache: PluginMetadataCacheGateway = InMemoryPluginMetadataCacheGateway(),
): PluginManager {
    val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb") },
    )
    val prefsDataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(appContext.cacheDir, "$prefix-prefs-${UUID.randomUUID()}.preferences_pb") },
    )
    return PluginManager(
        appContext,
        PluginMetaStore(dataStore),
        stubMediaCacheRepository(),
        stubLyricRepository(),
        stubDownloadedTrackDao(),
        stubLocalFilePlugin(),
        PluginAppVersionGate(),
        appVersion,
        metadataCache,
        AppPreferences(prefsDataStore),
    )
}
