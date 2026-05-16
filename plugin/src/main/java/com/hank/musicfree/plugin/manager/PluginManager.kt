package com.hank.musicfree.plugin.manager

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.repository.CachedPluginMetadata
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginUserVariable
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.plugin.engine.BootstrapShim
import com.hank.musicfree.plugin.engine.JsEngine
import com.hank.musicfree.plugin.engine.MusicItemBridgeProjector
import com.hank.musicfree.plugin.engine.RequireShim
import com.hank.musicfree.plugin.engine.WebDavShim
import com.hank.musicfree.plugin.di.PluginModule
import com.hank.musicfree.plugin.local.LocalFilePlugin
import com.hank.musicfree.plugin.local.LocalFilePluginConstants
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import com.hank.musicfree.plugin.runtime.PluginErrorReason
import com.hank.musicfree.plugin.runtime.PluginState
import com.hank.musicfree.plugin.runtime.PluginStateKeys
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.timedSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Manages the lifecycle of JS plugins: loading from disk, installing from file/URL,
 * and uninstalling. Exposes loaded plugins as a [StateFlow].
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val pluginMetaStore: PluginMetaStore,
    private val mediaCacheRepository: MediaCacheRepository,
    private val lyricRepository: LyricRepository,
    private val downloadedTrackDao: DownloadedTrackDao,
    private val localFilePlugin: LocalFilePlugin,
    private val appVersionGate: PluginAppVersionGate,
    @Named(PluginModule.APP_VERSION_NAMED) private val currentAppVersion: String,
    private val metadataCache: PluginMetadataCacheGateway,
    private val appPreferences: AppPreferences,
) {

    companion object {
        private const val PLUGINS_DIR_NAME = "plugins"
        private const val PLUGIN_META_SUFFIX = ".meta.properties"
        private val CORE_PLUGIN_METHODS = setOf(
            "search",
            "getMediaSource",
            "getMusicInfo",
            "getLyric",
            "getAlbumInfo",
            "getArtistWorks",
            "importMusicSheet",
            "importMusicItem",
            "getTopLists",
            "getTopListDetail",
            "getMusicSheetInfo",
            "getRecommendSheetTags",
            "getRecommendSheetsByTag",
            "getMusicComments",
        )
    }

    internal val pluginsDir: File by lazy {
        File(context.filesDir, PLUGINS_DIR_NAME).also { it.mkdirs() }
    }

    /**
     * Phase F bridge: every [JsLoadedPlugin] built by this manager shares a
     * single [MusicItemBridgeProjector] instance so DownloadedTrack / LyricCache
     * state is consistently layered onto plugin-bound MusicItem maps. Built
     * from the existing DAO + repository fields to avoid widening the
     * constructor signature (and the dozen test fixtures that mirror it).
     */
    private val bridgeProjector: MusicItemBridgeProjector by lazy {
        MusicItemBridgeProjector(
            downloadedTrackDao = downloadedTrackDao,
            lyricRepository = lyricRepository,
        )
    }

    /**
     * Scope for the background coroutines that evaluate cached plugins after a
     * lazy-load cold start. Uses [SupervisorJob] so one failing background load
     * doesn't cancel the rest, and [Dispatchers.IO] because the actual work is
     * a JS evaluation (QuickJS itself dispatches onto its own
     * single-thread context internally — see [JsEngine.create]).
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins.asStateFlow()

    /**
     * Source of truth for plugin lifecycle state. Each row identifies one
     * file on disk (or the built-in local entry) plus its current
     * [PluginState]. [plugins] is a backwards-compatible projection that
     * includes only Mounted entries' [LoadedPlugin]s.
     *
     * Phase C invariant: every mutation MUST update [_plugins] AND
     * [_entries] together (see [mutatePlugins]).
     */
    private val _entries = MutableStateFlow<List<PluginEntry>>(emptyList())
    val allEntries: StateFlow<List<PluginEntry>> = _entries.asStateFlow()

    private val mutex = Mutex()
    private val _loaded = AtomicBoolean(false)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Test hook: when non-null, replaces real OkHttp downloads in
     * [downloadOutsideLock]. Lets unit tests inject a slow / failing /
     * scripted response without standing up a `MockWebServer` for every
     * call site. Production callers MUST NOT set this.
     */
    @VisibleForTesting
    internal var downloadOverride: (suspend (String) -> ByteArray?)? = null

    /**
     * Run an HTTP plugin/subscription download on [Dispatchers.IO] WITHOUT
     * holding [mutex]. Every install/update entry point MUST route its
     * network IO through this helper so that the lock is only acquired to
     * mutate `_plugins` / `_entries` / the filesystem — long-tail GitHub
     * timeouts under GFW must not block concurrent lazy-load attachments
     * (see `completeLazyLoad`).
     */
    private suspend fun downloadOutsideLock(url: String): ByteArray? {
        downloadOverride?.let { return it(url) }
        return withContext(Dispatchers.IO) { downloadUrlBytes(url) }
    }

    /**
     * Prefetch every subscription entry's plugin bytes sequentially, all
     * OUTSIDE the lock. Returns `url -> bytes-or-null` preserving order so
     * the locked install loop can call `installFromBytesLocked` without
     * doing any HTTP.
     */
    private suspend fun prefetchSubscriptionEntries(
        entries: List<SubscriptionPluginEntry>,
    ): Map<String, ByteArray?> {
        val results = LinkedHashMap<String, ByteArray?>(entries.size)
        for (entry in entries) {
            results[entry.url] = downloadOutsideLock(entry.url)
        }
        return results
    }

    /**
     * Idempotent plugin loading: only loads from disk on the first call.
     * Subsequent calls return immediately. Use [loadAllPlugins] to force a reload.
     */
    suspend fun ensurePluginsLoaded() {
        if (_loaded.get()) return
        loadAllPlugins()
    }

    /**
     * Load all .js plugin files from the plugins directory and register the
     * built-in "本地" plugin (see [buildLocalEntry]).
     *
     * Phase E lazy-load:
     *  - When `lazyLoadPlugins=true` (default) AND a metadata-cache row matches
     *    the on-disk file's mtime AND current app version, the entry is added
     *    as [PluginState.Loading] with the cached [PluginInfo]; the actual JS
     *    evaluation runs on [backgroundScope] and transitions the entry to
     *    Mounted (or Failed). Cache misses still load synchronously.
     *  - When `lazyLoadPlugins=false`, all plugins load synchronously (the
     *    pre-Phase-E behaviour).
     */
    suspend fun loadAllPlugins() = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Destroy any previously loaded plugins (the local entry is a no-op).
            _plugins.value.forEach { it.destroy() }

            val lazy = appPreferences.lazyLoadPlugins.first()
            val cacheByPath = if (lazy) {
                metadataCache.getAll().associateBy { it.filePath }
            } else {
                emptyMap()
            }

            val loadedPlugins = mutableListOf<LoadedPlugin>()
            val newEntries = mutableListOf<PluginEntry>()
            val backgroundLoads = mutableListOf<PluginEntry>()
            val files = pluginsDir.listFiles { _, name -> name.endsWith(".js") } ?: emptyArray()
            for (file in files) {
                val installSource = readInstallMetadata(file)
                val cached = cacheByPath[file.absolutePath]

                // Cache hit: emit a Loading entry from the cached metadata so
                // the UI has something to show, then queue the JS evaluation
                // to run on the background scope.
                if (cached != null && isCacheFresh(cached, file)) {
                    val info = cached.toPluginInfo()
                    val loadingEntry = PluginEntry(
                        filePath = file.absolutePath,
                        state = PluginState.Loading,
                        info = info,
                        loaded = null,
                        installSource = installSource,
                        attemptedPlatform = info.platform,
                    )
                    newEntries.add(loadingEntry)
                    backgroundLoads.add(loadingEntry)
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "plugin_load_cache_hit",
                        fields = mapOf(
                            "operation" to "load",
                            "status" to "deferred",
                            "fileName" to file.name,
                            "platform" to info.platform,
                            "state" to PluginStateKeys.STATE_LOADING,
                        ),
                    )
                    continue
                }

                // Cache miss / lazy disabled / stale cache: load synchronously.
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_load_start",
                    fields = mapOf(
                        "operation" to "load",
                        "status" to "start",
                        "fileName" to file.name,
                    ),
                )
                try {
                    val (plugin, durationMs) = timedSuspend {
                        loadPluginFromFile(file, installSource)
                    }
                    if (plugin != null) {
                        loadedPlugins.add(plugin)
                        newEntries.add(
                            PluginEntry(
                                filePath = plugin.filePath,
                                state = PluginState.Mounted,
                                info = plugin.info,
                                loaded = plugin,
                                installSource = plugin.installSource,
                                attemptedPlatform = plugin.info.platform,
                            ),
                        )
                        // Phase E: persist the freshly-loaded metadata so the
                        // next cold start can skip JS evaluation.
                        upsertCacheRow(file, plugin.info)
                        MfLog.detail(
                            category = LogCategory.PLUGIN,
                            event = "plugin_load_success",
                            fields = mapOf(
                                "operation" to "load",
                                "status" to "success",
                                "platform" to plugin.info.platform,
                                "fileName" to file.name,
                                "durationMs" to durationMs,
                                "state" to PluginStateKeys.STATE_MOUNTED,
                            ),
                        )
                    } else {
                        newEntries.add(
                            PluginEntry(
                                filePath = file.absolutePath,
                                state = PluginState.Failed(
                                    PluginErrorReason.CannotParse,
                                    "loadPluginFromFile returned null",
                                ),
                                info = null,
                                loaded = null,
                                installSource = installSource,
                                attemptedPlatform = null,
                            ),
                        )
                        MfLog.error(
                            category = LogCategory.PLUGIN,
                            event = "plugin_load_failed",
                            fields = mapOf(
                                "operation" to "load",
                                "status" to "failed",
                                "fileName" to file.name,
                                "state" to PluginStateKeys.STATE_FAILED,
                                "reason" to PluginStateKeys.REASON_CANNOT_PARSE,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    newEntries.add(
                        PluginEntry(
                            filePath = file.absolutePath,
                            state = PluginState.Failed(
                                PluginErrorReason.CannotParse,
                                e.message ?: e::class.qualifiedName,
                            ),
                            info = null,
                            loaded = null,
                            installSource = installSource,
                            attemptedPlatform = null,
                        ),
                    )
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "plugin_load_failed",
                        throwable = e,
                        fields = mapOf(
                            "operation" to "load",
                            "status" to "failed",
                            "fileName" to file.name,
                            "state" to PluginStateKeys.STATE_FAILED,
                            "reason" to PluginStateKeys.REASON_CANNOT_PARSE,
                        ),
                    )
                }
            }
            val local = buildLocalEntry()
            loadedPlugins += local
            newEntries += PluginEntry(
                filePath = null,
                state = PluginState.Mounted,
                info = local.info,
                loaded = local,
                installSource = null,
                attemptedPlatform = LocalFilePluginConstants.PLATFORM,
            )
            _plugins.value = loadedPlugins
            _entries.value = newEntries
            _loaded.set(true)

            // Phase E: schedule background evaluation for any cache-hit entries
            // we emitted as Loading. The coroutine acquires [mutex] again so it
            // doesn't race with concurrent install/uninstall.
            backgroundLoads.forEach { entry ->
                backgroundScope.launch {
                    completeLazyLoad(entry)
                }
            }
        }
    }

    /**
     * Returns true when the cached metadata is safe to use for a quick cold
     * start: the on-disk file's mtime matches what we recorded AND the host
     * app version hasn't changed since we wrote the cache row.
     */
    private fun isCacheFresh(cached: CachedPluginMetadata, file: File): Boolean {
        return cached.sourceMtimeMs == file.lastModified() &&
            cached.cachedAtAppVersion == currentAppVersion
    }

    /**
     * Background evaluation of a [PluginState.Loading] entry created from a
     * cache hit. Transitions to Mounted on success, Failed on any error. If
     * the entry has been replaced (e.g. user re-installed or uninstalled while
     * the JS evaluation was in flight), the result is dropped.
     */
    private suspend fun completeLazyLoad(entry: PluginEntry) {
        val filePath = entry.filePath ?: return
        val file = File(filePath)
        if (!file.exists()) {
            replaceEntryIfStillThere(entry) {
                it.copy(
                    state = PluginState.Failed(
                        PluginErrorReason.CannotParse,
                        "file not found: $filePath",
                    ),
                    loaded = null,
                )
            }
            return
        }
        val installSource = entry.installSource ?: readInstallMetadata(file)
        val startedAt = System.currentTimeMillis()
        try {
            val plugin = loadPluginFromFile(file, installSource)
            if (plugin == null) {
                replaceEntryIfStillThere(entry) {
                    it.copy(
                        state = PluginState.Failed(
                            PluginErrorReason.CannotParse,
                            "loadPluginFromFile returned null",
                        ),
                        loaded = null,
                    )
                }
                return
            }
            // Re-run the appVersion gate in case the cached metadata was
            // written against an older app version that satisfied a constraint
            // the new app version no longer satisfies.
            val versionRejection = evaluateAppVersionGate(
                plugin = plugin,
                operation = "load_lazy",
                fileName = file.name,
            )
            if (versionRejection != null) {
                plugin.destroy()
                replaceEntryIfStillThere(entry) {
                    it.copy(
                        state = PluginState.Failed(
                            PluginErrorReason.VersionNotMatch,
                            versionRejection.detail,
                        ),
                        loaded = null,
                    )
                }
                return
            }
            mutex.withLock {
                // Defensive: only attach the LoadedPlugin if our entry is still
                // present in the source-of-truth list and still Loading.
                val current = _entries.value
                val idx = current.indexOfFirst { it.filePath == filePath && it.state is PluginState.Loading }
                if (idx < 0) {
                    plugin.destroy()
                    return@withLock
                }
                _plugins.value = _plugins.value + plugin
                _entries.value = current.mapIndexed { i, e ->
                    if (i == idx) {
                        e.copy(
                            state = PluginState.Mounted,
                            info = plugin.info,
                            loaded = plugin,
                            installSource = plugin.installSource,
                            attemptedPlatform = plugin.info.platform,
                        )
                    } else {
                        e
                    }
                }
                upsertCacheRow(file, plugin.info)
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_load_success",
                    fields = mapOf(
                        "operation" to "load_lazy",
                        "status" to "success",
                        "platform" to plugin.info.platform,
                        "fileName" to file.name,
                        "durationMs" to System.currentTimeMillis() - startedAt,
                        "state" to PluginStateKeys.STATE_MOUNTED,
                    ),
                )
            }
        } catch (e: Exception) {
            replaceEntryIfStillThere(entry) {
                it.copy(
                    state = PluginState.Failed(
                        PluginErrorReason.CannotParse,
                        e.message ?: e::class.qualifiedName,
                    ),
                    loaded = null,
                )
            }
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_load_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "load_lazy",
                    "status" to "failed",
                    "fileName" to file.name,
                    "state" to PluginStateKeys.STATE_FAILED,
                ),
            )
        }
    }

    /**
     * Atomic in-place replacement of [original] inside [_entries] iff a row
     * with the same filePath is still present. No-op when the row has been
     * removed (uninstalled while the background eval was in flight).
     */
    private suspend fun replaceEntryIfStillThere(
        original: PluginEntry,
        mutator: (PluginEntry) -> PluginEntry,
    ) {
        mutex.withLock {
            _entries.update { current ->
                current.map {
                    if (it.filePath == original.filePath && it.state is PluginState.Loading) {
                        mutator(it)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private suspend fun upsertCacheRow(file: File, info: PluginInfo) {
        try {
            metadataCache.upsert(
                CachedPluginMetadata(
                    filePath = file.absolutePath,
                    platform = info.platform,
                    version = info.version,
                    hash = info.hash.orEmpty(),
                    srcUrl = info.srcUrl,
                    appVersion = info.appVersion,
                    supportedMethods = info.supportedMethods,
                    supportedSearchTypes = info.supportedSearchType,
                    userVariableKeys = info.userVariables.map { it.key },
                    sourceMtimeMs = file.lastModified(),
                    cachedAtAppVersion = currentAppVersion,
                ),
            )
        } catch (e: Exception) {
            // Cache writes are best-effort: a failure here only means the next
            // cold start re-evaluates the plugin (correctness-preserving).
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_cache_upsert_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "metadata_cache_upsert",
                    "status" to "failed",
                    "platform" to info.platform,
                    "fileName" to file.name,
                ),
            )
        }
    }

    /**
     * Materialise the cached metadata row into a [PluginInfo] consumable by
     * the UI list. `supportedSearchTypeDeclared` is set to the same condition
     * used by [extractPluginInfo] (a non-empty supportedSearchType means the
     * plugin declared the field).
     */
    private fun CachedPluginMetadata.toPluginInfo(): PluginInfo = PluginInfo(
        platform = platform,
        version = version,
        author = null,
        description = null,
        srcUrl = srcUrl,
        supportedSearchType = supportedSearchTypes,
        supportedSearchTypeDeclared = supportedSearchTypes.isNotEmpty(),
        appVersion = appVersion,
        primaryKey = null,
        defaultSearchType = null,
        cacheControl = null,
        hints = null,
        supportedMethods = supportedMethods,
        userVariables = userVariableKeys.map {
            com.hank.musicfree.plugin.api.PluginUserVariable(key = it)
        },
        hash = hash.takeIf { it.isNotEmpty() },
    )

    /**
     * Constructs the singleton in-process "本地" plugin entry. The plugin
     * declares the four PluginApi methods that [LocalFilePlugin] supports
     * (getMusicInfo / getLyric / importMusicItem / getMediaSource) and uses
     * `cacheControl = "no-store"` because local file URLs are always fresh.
     *
     * `supportedSearchType = emptyList()` with `supportedSearchTypeDeclared = true`
     * means the plugin opts OUT of any search type — so it does not appear in
     * search/lyric-search candidate lists.
     */
    private fun buildLocalEntry(): LoadedPlugin {
        val info = PluginInfo(
            platform = LocalFilePluginConstants.PLATFORM,
            version = null,
            author = null,
            description = null,
            srcUrl = null,
            supportedSearchType = emptyList(),
            supportedSearchTypeDeclared = true,
            cacheControl = "no-store",
            supportedMethods = LocalFilePluginConstants.SUPPORTED_METHODS,
            hash = LocalFilePluginConstants.HASH,
        )
        return LocalLoadedPlugin(info = info, delegate = localFilePlugin)
    }

    /**
     * Install a plugin from a local file by copying it to the plugins directory.
     */
    suspend fun installFromFile(sourceFile: File): LoadedPlugin? {
        val flowId = newFlowId()
        val operation = "install_from_file"
        logOperationStart(
            flowId = flowId,
            operation = operation,
            targetCount = 1,
            extraFields = mapOf("fileName" to sourceFile.name),
        )
        val plugin = mutex.withLock {
            withContext(Dispatchers.IO) {
                installFromFileLocked(sourceFile)
            }
        }
        logSinglePluginResult(
            flowId = flowId,
            operation = operation,
            plugin = plugin,
            extraFields = mapOf("fileName" to sourceFile.name),
        )
        return plugin
    }

    /**
     * Install a plugin by downloading it from a URL.
     *
     * Network IO runs OUTSIDE [mutex] — only the file write / state mutation
     * is locked. This keeps `completeLazyLoad` unblocked when a download
     * stalls (e.g. GitHub timeouts on GFW networks).
     */
    suspend fun installFromUrl(url: String, fileName: String): LoadedPlugin? {
        val flowId = newFlowId()
        val operation = "install_from_url"
        val installSource = PluginInstallSource(
            type = PluginInstallSourceType.PLUGIN_URL,
            value = url,
        )
        logOperationStart(
            flowId = flowId,
            operation = operation,
            url = url,
            targetCount = 1,
            extraFields = mapOf("fileName" to fileName),
        )
        val bytes = downloadOutsideLock(url)
        val plugin = if (bytes == null) {
            recordFailedEntry(
                targetPath = File(pluginsDir, fileName).absolutePath,
                reason = PluginErrorReason.DownloadFailed,
                detail = "HTTP/IO download failed for $url",
                installSource = installSource,
                attemptedPlatform = null,
            )
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                fields = mapOf(
                    "operation" to "install_from_url",
                    "status" to "failed",
                    "url" to url,
                    "fileName" to fileName,
                    "state" to PluginStateKeys.STATE_FAILED,
                    "reason" to PluginStateKeys.REASON_DOWNLOAD_FAILED,
                ),
            )
            null
        } else {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    installFromBytesLocked(bytes, fileName, installSource)
                }
            }
        }
        logSinglePluginResult(
            flowId = flowId,
            operation = operation,
            url = url,
            plugin = plugin,
            extraFields = mapOf("fileName" to fileName),
        )
        return plugin
    }

    /**
     * Install from a network URL and return structured operation feedback.
     *
     * A `.json` URL is treated as a MusicFree subscription payload and each
     * `plugins[].url` entry is installed. Other URLs are treated as plugin JS.
     */
    suspend fun installFromNetworkUrl(url: String): PluginOperationResult {
        val flowId = newFlowId()
        val operation = "install_from_network_url"
        val startedAt = System.currentTimeMillis()
        val trimmed = url.trim()
        logOperationStart(
            flowId = flowId,
            operation = operation,
            url = trimmed,
            targetCount = 1,
        )
        fun finish(result: PluginOperationResult): PluginOperationResult {
            logPluginOperationResult(
                flowId = flowId,
                operation = operation,
                url = trimmed,
                result = result,
            )
            return result
        }
        if (trimmed.isBlank()) {
            return finish(PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = emptyList(),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = url,
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "URL 不能为空",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = startedAt,
            ))
        }

        val normalizedPath = trimmed.substringBefore("#").substringBefore("?")
        if (normalizedPath.endsWith(".json", ignoreCase = true)) {
            // Subscription path: prefetch JSON + every entry's plugin bytes
            // OUTSIDE the lock so GitHub / GFW timeouts can't stall lazy
            // load attachments.
            val rawJson = downloadOutsideLock(trimmed)
                ?: return finish(PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(trimmed),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = trimmed,
                            errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                            message = "订阅下载失败",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                ))

            val parsed = SubscriptionParser.parse(rawJson.toString(StandardCharsets.UTF_8))
            if (parsed.isMalformed) {
                return finish(PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(trimmed),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = trimmed,
                            errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                            message = "订阅格式无效",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                ))
            }

            val entryBytes = prefetchSubscriptionEntries(parsed.installableEntries)
            val targets = parsed.installableEntries.map { it.url }
            val result = mutex.withLock {
                withContext(Dispatchers.IO) {
                    updateSubscriptionEntriesLocked(
                        subscriptionUrl = trimmed,
                        entries = parsed.installableEntries,
                        entryBytes = entryBytes,
                        totalEntries = parsed.totalEntries,
                        startedAt = startedAt,
                        targets = targets,
                        operationType = PluginOperationType.ADD,
                        installSourceType = PluginInstallSourceType.SUBSCRIPTION_URL,
                    )
                }
            }
            return finish(result)
        }

        // Single-plugin .js path: prefetch bytes OUTSIDE the lock.
        val fileName = SubscriptionFileNames.networkPluginFileName(trimmed)
        val installSource = PluginInstallSource(
            type = PluginInstallSourceType.PLUGIN_URL,
            value = trimmed,
        )
        val bytes = downloadOutsideLock(trimmed)
        if (bytes == null) {
            // Phase C: record a structured Failed entry so the UI can
            // surface the download failure with reason DownloadFailed.
            // `recordFailedEntry` mutates `_entries` via atomic CAS, so it
            // does not need the mutex.
            recordFailedEntry(
                targetPath = File(pluginsDir, fileName).absolutePath,
                reason = PluginErrorReason.DownloadFailed,
                detail = "HTTP/IO download failed for $trimmed",
                installSource = installSource,
                attemptedPlatform = null,
            )
            return finish(PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf(trimmed),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = trimmed,
                        errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                        message = "下载失败",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            ))
        }

        val installed = mutex.withLock {
            withContext(Dispatchers.IO) {
                installFromBytesLocked(
                    bytes = bytes,
                    fileName = fileName,
                    installSource = installSource,
                )
            }
        }

        return if (installed != null) {
            finish(PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf(installed.info.platform),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            ))
        } else {
            val errorCode = inferFailureErrorCode(
                targetPath = File(pluginsDir, fileName).absolutePath,
            )
            val message = errorCode.toUiMessage()
            finish(PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf(trimmed),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = trimmed,
                        errorCode = errorCode,
                        message = message,
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    /**
     * Install plugins listed in a subscription JSON URL.
     */
    suspend fun installFromSubscriptionUrl(subscriptionUrl: String): SubscriptionInstallResult {
        val flowId = newFlowId()
        val operation = "install_from_subscription"
        logOperationStart(
            flowId = flowId,
            operation = operation,
            url = subscriptionUrl,
            targetCount = 1,
        )
        val prefetch = prefetchSubscriptionForInstall(subscriptionUrl)
        val result = when (prefetch) {
            is SubscriptionPrefetchOutcome.BlankUrl -> SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅地址不能为空",
            )
            is SubscriptionPrefetchOutcome.DownloadFailed -> SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅下载失败",
            )
            is SubscriptionPrefetchOutcome.Malformed -> SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅格式无效",
            )
            is SubscriptionPrefetchOutcome.Ready -> mutex.withLock {
                withContext(Dispatchers.IO) {
                    installFromSubscriptionUrlLocked(
                        subscriptionUrl = subscriptionUrl,
                        parsed = prefetch.parsed,
                        entryBytes = prefetch.entryBytes,
                    )
                }
            }
        }
        logSubscriptionInstallResult(
            flowId = flowId,
            operation = operation,
            url = subscriptionUrl,
            result = result,
        )
        return result
    }

    /**
     * Result of [prefetchSubscriptionForInstall]: capture every early-exit
     * reason as data so callers can map them to either
     * [SubscriptionInstallResult] (for `install_from_subscription`) or
     * [PluginOperationResult] (for `install_from_network_url` /
     * `update_from_subscription`) without re-doing the network IO.
     */
    private sealed interface SubscriptionPrefetchOutcome {
        object BlankUrl : SubscriptionPrefetchOutcome
        object DownloadFailed : SubscriptionPrefetchOutcome
        object Malformed : SubscriptionPrefetchOutcome
        data class Ready(
            val parsed: SubscriptionParseResult,
            val entryBytes: Map<String, ByteArray?>,
        ) : SubscriptionPrefetchOutcome
    }

    private suspend fun prefetchSubscriptionForInstall(
        subscriptionUrl: String,
    ): SubscriptionPrefetchOutcome {
        if (subscriptionUrl.isBlank()) return SubscriptionPrefetchOutcome.BlankUrl
        val rawJson = downloadOutsideLock(subscriptionUrl) ?: return SubscriptionPrefetchOutcome.DownloadFailed
        val parsed = SubscriptionParser.parse(rawJson.toString(StandardCharsets.UTF_8))
        if (parsed.isMalformed) return SubscriptionPrefetchOutcome.Malformed
        val entryBytes = prefetchSubscriptionEntries(parsed.installableEntries)
        return SubscriptionPrefetchOutcome.Ready(parsed, entryBytes)
    }

    /**
     * Update a single installed plugin using its source URL.
     */
    suspend fun updatePlugin(platform: String): PluginOperationResult {
        val flowId = newFlowId()
        val operation = "update_plugin"
        logOperationStart(flowId = flowId, operation = operation, platform = platform, targetCount = 1)
        val startedAt = System.currentTimeMillis()

        // Snapshot the target plugin + its source URL OUTSIDE the lock so we
        // can run the HTTP download with the mutex released.
        val plugin = _plugins.value.find { it.info.platform == platform }
        if (plugin == null) {
            val result = PluginOperationResult(
                operationType = PluginOperationType.UPDATE_SINGLE,
                targetPlugins = listOf(platform),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = platform,
                        errorCode = PluginOperationErrorCode.INTERNAL_ERROR,
                        message = "插件未找到",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = startedAt,
            )
            logPluginOperationResult(flowId = flowId, operation = operation, platform = platform, result = result)
            return result
        }

        val sourceUrl = plugin.info.srcUrl?.trim().orEmpty()
        val bytes = if (sourceUrl.isBlank() || plugin.filePath == null) null else downloadOutsideLock(sourceUrl)

        val result = mutex.withLock {
            withContext(Dispatchers.IO) {
                updateInstalledPluginLocked(
                    targetPlugin = plugin,
                    prefetchedBytes = bytes,
                    operationType = PluginOperationType.UPDATE_SINGLE,
                    startedAt = startedAt,
                )
            }
        }
        logPluginOperationResult(flowId = flowId, operation = operation, platform = platform, result = result)
        return result
    }

    /**
     * Update all installed plugins that expose an update source. The built-in
     * "本地" plugin is excluded because it has no source URL.
     */
    suspend fun updateAllPlugins(): PluginOperationResult {
        val flowId = newFlowId()
        val operation = "update_all_plugins"
        val targets = _plugins.value
            .filter { it.info.platform != LocalFilePluginConstants.PLATFORM }
        logOperationStart(flowId = flowId, operation = operation, targetCount = targets.size)
        val startedAt = System.currentTimeMillis()

        // Prefetch every plugin's bytes OUTSIDE the lock — a single slow
        // source can no longer stall the rest of the update or any
        // unrelated plugin lifecycle work.
        val byPlatform = LinkedHashMap<String, ByteArray?>(targets.size)
        for (target in targets) {
            val sourceUrl = target.info.srcUrl?.trim().orEmpty()
            byPlatform[target.info.platform] =
                if (sourceUrl.isBlank() || target.filePath == null) null else downloadOutsideLock(sourceUrl)
        }

        val result = mutex.withLock {
            withContext(Dispatchers.IO) {
                updateInstalledPluginsLocked(
                    targets = targets,
                    prefetchedBytesByPlatform = byPlatform,
                    operationType = PluginOperationType.UPDATE_ALL,
                    startedAt = startedAt,
                )
            }
        }
        logPluginOperationResult(flowId = flowId, operation = operation, result = result)
        return result
    }

    /**
     * Update plugins listed in a subscription JSON URL.
     */
    suspend fun updateFromSubscriptionUrl(subscriptionUrl: String): PluginOperationResult {
        val flowId = newFlowId()
        val operation = "update_from_subscription"
        val startedAt = System.currentTimeMillis()
        logOperationStart(
            flowId = flowId,
            operation = operation,
            url = subscriptionUrl,
            targetCount = 1,
        )
        fun finish(result: PluginOperationResult): PluginOperationResult {
            logPluginOperationResult(
                flowId = flowId,
                operation = operation,
                url = subscriptionUrl,
                result = result,
            )
            return result
        }
        val prefetch = prefetchSubscriptionForInstall(subscriptionUrl)
        return when (prefetch) {
            is SubscriptionPrefetchOutcome.BlankUrl -> finish(PluginOperationResult(
                operationType = PluginOperationType.UPDATE_SUBSCRIPTION,
                targetPlugins = emptyList(),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = subscriptionUrl,
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "订阅地址不能为空",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = startedAt,
            ))
            is SubscriptionPrefetchOutcome.DownloadFailed -> finish(PluginOperationResult(
                operationType = PluginOperationType.UPDATE_SUBSCRIPTION,
                targetPlugins = listOf(subscriptionUrl),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = subscriptionUrl,
                        errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                        message = "订阅下载失败",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            ))
            is SubscriptionPrefetchOutcome.Malformed -> finish(PluginOperationResult(
                operationType = PluginOperationType.UPDATE_SUBSCRIPTION,
                targetPlugins = listOf(subscriptionUrl),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = subscriptionUrl,
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "订阅格式无效",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            ))
            is SubscriptionPrefetchOutcome.Ready -> {
                val targets = prefetch.parsed.installableEntries.map { it.url }
                val result = mutex.withLock {
                    withContext(Dispatchers.IO) {
                        updateSubscriptionEntriesLocked(
                            subscriptionUrl = subscriptionUrl,
                            entries = prefetch.parsed.installableEntries,
                            entryBytes = prefetch.entryBytes,
                            totalEntries = prefetch.parsed.totalEntries,
                            startedAt = startedAt,
                            targets = targets,
                        )
                    }
                }
                finish(result)
            }
        }
    }

    /**
     * Uninstall a plugin by platform name. The built-in "本地" plugin cannot be
     * uninstalled — calls for it are silently ignored (no log, no cache cleanup).
     */
    suspend fun uninstall(platform: String) {
        val flowId = newFlowId()
        val operation = "uninstall"
        logOperationStart(flowId = flowId, operation = operation, platform = platform, targetCount = 1)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (platform == LocalFilePluginConstants.PLATFORM) {
                    // Built-in plugin: defensive no-op. Plugin management UI
                    // already filters this platform out, so this branch is just
                    // belt-and-braces.
                    return@withContext
                }
            val current = _plugins.value.toMutableList()
            val plugin = current.find { it.info.platform == platform }
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_uninstall_start",
                fields = mapOf(
                    "operation" to "uninstall",
                    "flowId" to flowId,
                    "status" to "start",
                    "platform" to platform,
                ),
            )
            try {
                if (plugin != null) {
                    plugin.destroy()
                    val filePath = plugin.filePath
                    if (filePath != null) {
                        val file = File(filePath)
                        file.delete()
                        deleteInstallMetadata(file)
                    }
                    current.remove(plugin)
                    _plugins.value = current
                    // Phase C: also drop the matching PluginEntry so the new
                    // state machine reflects the uninstall. Match on platform
                    // (primary) OR filePath (defensive fallback).
                    _entries.update { existing ->
                        existing.filterNot {
                            it.info?.platform == platform ||
                                it.attemptedPlatform == platform ||
                                (filePath != null && it.filePath == filePath)
                        }
                    }
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "plugin_uninstall_success",
                        fields = mapOf(
                            "operation" to "uninstall",
                            "flowId" to flowId,
                            "status" to "success",
                            "platform" to platform,
                            "fileName" to (filePath?.substringAfterLast('/') ?: ""),
                            "successCount" to 1,
                            "failureCount" to 0,
                            "targetCount" to 1,
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                    // Clean platform-keyed caches (media cache, lyric cache, downloaded track rows)
                    // so a re-install of the same platform starts with a clean slate.
                    val cleanupStartedAt = System.currentTimeMillis()
                    runCatching {
                        mediaCacheRepository.deleteByPlatform(platform)
                        lyricRepository.deleteByPlatform(platform)
                        downloadedTrackDao.deleteByPlatform(platform)
                        // Phase E: drop any cached metadata snapshot so a
                        // re-install with different bytes doesn't pick up the
                        // stale row by file path.
                        if (filePath != null) {
                            metadataCache.deleteByPath(filePath)
                        }
                    }.onSuccess {
                        MfLog.detail(
                            category = LogCategory.PLUGIN,
                            event = "plugin_uninstall_cache_cleared",
                            fields = mapOf(
                                "operation" to "uninstall",
                                "status" to "success",
                                "platform" to platform,
                                "durationMs" to System.currentTimeMillis() - cleanupStartedAt,
                            ),
                        )
                    }.onFailure { error ->
                        MfLog.error(
                            category = LogCategory.PLUGIN,
                            event = "plugin_uninstall_cache_cleanup_failed",
                            throwable = error,
                            fields = mapOf(
                                "operation" to "uninstall",
                                "status" to "failed",
                                "platform" to platform,
                                "durationMs" to System.currentTimeMillis() - cleanupStartedAt,
                            ),
                        )
                    }
                } else {
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "plugin_uninstall_skipped",
                        fields = mapOf(
                            "operation" to "uninstall",
                            "flowId" to flowId,
                            "status" to "skipped",
                            "platform" to platform,
                            "successCount" to 0,
                            "failureCount" to 0,
                            "targetCount" to 1,
                            "result" to LogFields.Result.SKIPPED,
                            "reason" to LogFields.Reason.NOT_FOUND,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_uninstall_cancelled",
                    fields = mapOf(
                        "operation" to "uninstall",
                        "flowId" to flowId,
                        "status" to "cancelled",
                        "platform" to platform,
                        "successCount" to 0,
                        "failureCount" to 0,
                        "targetCount" to 1,
                        "result" to LogFields.Result.CANCELLED,
                        "reason" to LogFields.Reason.CANCELLED,
                    ),
                )
                throw e
            } catch (e: Exception) {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_uninstall_failed",
                    throwable = e,
                    fields = mapOf(
                        "operation" to "uninstall",
                        "flowId" to flowId,
                        "status" to "failed",
                        "platform" to platform,
                        "successCount" to 0,
                        "failureCount" to 1,
                        "targetCount" to 1,
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "internal_error",
                    ),
                )
            }
            }
        }
    }

    /**
     * Get a loaded plugin by platform name.
     */
    fun getPlugin(platform: String): LoadedPlugin? {
        return _plugins.value.find { it.info.platform == platform }
    }

    /**
     * Force a reload — destroy currently mounted plugins and reset the
     * `_loaded` flag so the next call to [ensurePluginsLoaded] re-runs
     * [loadAllPlugins]. Used by the "lazy load plugins" settings toggle in
     * `feature/settings` so flipping the switch takes effect immediately.
     */
    suspend fun reload() {
        mutex.withLock {
            _plugins.value.forEach { it.destroy() }
            _plugins.value = emptyList()
            _entries.value = emptyList()
            _loaded.set(false)
        }
        loadAllPlugins()
    }

    /**
     * Sorted list of enabled plugins. Combines loaded plugins with
     * disabled set and order from [PluginMetaStore].
     * Plugins not in the order list are appended at the end.
     *
     * The built-in "本地" plugin is filtered out here: it should not appear
     * in any user-facing enabled-plugins list (plugin management, sort order,
     * searchable candidates). Direct lookup via [getPlugin] still returns it.
     */
    fun getSortedEnabledPlugins(): Flow<List<LoadedPlugin>> =
        combine(
            plugins,
            pluginMetaStore.disabledPlugins,
            pluginMetaStore.pluginOrder,
        ) { allPlugins, disabled, order ->
            val enabled = allPlugins.filter {
                it.info.platform !in disabled &&
                    it.info.platform != LocalFilePluginConstants.PLATFORM
            }
            if (order.isEmpty()) return@combine enabled
            val orderMap = order.withIndex().associate { (i, p) -> p to i }
            enabled.sortedBy { orderMap[it.info.platform] ?: Int.MAX_VALUE }
        }

    /**
     * Enabled plugins that support the `search` method, sorted by user-defined order.
     */
    fun getSearchablePlugins(type: String = "music"): Flow<List<LoadedPlugin>> =
        getSortedEnabledPlugins().map { plugins ->
            plugins.filter { it.info.supportsSearchType(type) }
        }

    /**
     * Enabled plugins that support lyric search, sorted by user-defined order.
     *
     * An undeclared supportedSearchType means the plugin did not declare the field,
     * which RN treats as compatible with any requested search type.
     */
    fun getLyricSearchablePlugins(): Flow<List<LoadedPlugin>> =
        getSortedEnabledPlugins().map { plugins ->
            plugins.filter { it.info.supportsSearchType("lyric") }
        }

    // Convenience delegates to PluginMetaStore
    suspend fun setPluginEnabled(platform: String, enabled: Boolean) {
        val flowId = newFlowId()
        val operation = "set_plugin_enabled"
        logOperationStart(
            flowId = flowId,
            operation = operation,
            platform = platform,
            targetCount = 1,
            extraFields = mapOf("enabled" to enabled),
        )
        try {
            pluginMetaStore.setPluginEnabled(platform, enabled)
            logOperationSuccess(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = 1,
                extraFields = mapOf("enabled" to enabled),
            )
        } catch (e: CancellationException) {
            logOperationCancelled(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = 1,
                extraFields = mapOf("enabled" to enabled),
            )
            throw e
        } catch (e: Exception) {
            logOperationFailure(
                flowId = flowId,
                operation = operation,
                throwable = e,
                platform = platform,
                targetCount = 1,
                reason = "metadata_write_failed",
                extraFields = mapOf("enabled" to enabled),
            )
            throw e
        }
    }

    suspend fun setPluginOrder(order: List<String>) {
        val flowId = newFlowId()
        val operation = "set_plugin_order"
        logOperationStart(flowId = flowId, operation = operation, targetCount = order.size)
        try {
            pluginMetaStore.setPluginOrder(order)
            logOperationSuccess(
                flowId = flowId,
                operation = operation,
                targetCount = order.size,
                extraFields = mapOf("count" to order.size),
            )
        } catch (e: CancellationException) {
            logOperationCancelled(
                flowId = flowId,
                operation = operation,
                targetCount = order.size,
                extraFields = mapOf("count" to order.size),
            )
            throw e
        } catch (e: Exception) {
            logOperationFailure(
                flowId = flowId,
                operation = operation,
                throwable = e,
                targetCount = order.size,
                reason = "metadata_write_failed",
            )
            throw e
        }
    }

    suspend fun setUserVariables(platform: String, variables: Map<String, String>) {
        val flowId = newFlowId()
        val operation = "set_user_variables"
        logOperationStart(
            flowId = flowId,
            operation = operation,
            platform = platform,
            targetCount = 1,
            extraFields = mapOf("count" to variables.size),
        )
        try {
            mutex.withLock {
                _plugins.value.firstOrNull { it.info.platform == platform }
                    ?.updateUserVariables(variables)
                pluginMetaStore.setUserVariables(platform, variables)
            }
            logOperationSuccess(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = 1,
                extraFields = mapOf("count" to variables.size),
            )
        } catch (e: CancellationException) {
            logOperationCancelled(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = 1,
                extraFields = mapOf("count" to variables.size),
            )
            throw e
        } catch (e: Exception) {
            logOperationFailure(
                flowId = flowId,
                operation = operation,
                throwable = e,
                platform = platform,
                targetCount = 1,
                reason = "user_variables_write_failed",
                extraFields = mapOf("count" to variables.size),
            )
            throw e
        }
    }

    suspend fun uninstallAllPlugins() {
        val flowId = newFlowId()
        // The built-in "本地" plugin is skipped by [uninstall], but filter here
        // too to avoid the unnecessary mutex round-trip and noisy logs.
        val platforms = _plugins.value
            .map { it.info.platform }
            .filter { it != LocalFilePluginConstants.PLATFORM }
        logOperationStart(
            flowId = flowId,
            operation = "uninstall_all_plugins",
            targetCount = platforms.size,
        )
        try {
            for (platform in platforms) {
                uninstall(platform)
            }
            logOperationSuccess(
                flowId = flowId,
                operation = "uninstall_all_plugins",
                targetCount = platforms.size,
                extraFields = mapOf("successCount" to platforms.size),
            )
        } catch (e: CancellationException) {
            logOperationCancelled(
                flowId = flowId,
                operation = "uninstall_all_plugins",
                targetCount = platforms.size,
            )
            throw e
        }
    }

    /**
     * Retry a previously Failed entry by re-loading its file from disk.
     *
     * Transitions: Failed → Loading → (Mounted | Failed). If [filePath] is
     * not present in [allEntries] the call returns a structured failure
     * without touching state. On success the returned [PluginOperationResult]
     * has `successCount = 1`; on failure the failure list carries either
     * MISSING_PLATFORM (when the JS lacked a `platform` field) or
     * SOURCE_INVALID (catch-all for parse / engine errors).
     */
    suspend fun retryEntry(filePath: String): PluginOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val entry = _entries.value.firstOrNull { it.filePath == filePath }
            if (entry == null) {
                return@withContext PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(filePath),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = filePath,
                            errorCode = PluginOperationErrorCode.INTERNAL_ERROR,
                            message = "Entry not found",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            }

            val file = File(filePath)
            if (!file.exists()) {
                replaceEntry(filePath) {
                    it.copy(
                        state = PluginState.Failed(
                            PluginErrorReason.CannotParse,
                            "file not found: $filePath",
                        ),
                        loaded = null,
                    )
                }
                return@withContext PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(filePath),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = filePath,
                            errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                            message = "插件文件已不存在",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            }

            replaceEntry(filePath) { it.copy(state = PluginState.Loading, loaded = null) }
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_state_transition",
                fields = mapOf(
                    "operation" to "retry_entry",
                    "filePath" to filePath,
                    "to" to PluginStateKeys.STATE_LOADING,
                ),
            )

            val installSource = entry.installSource ?: readInstallMetadata(file)
            return@withContext try {
                val plugin = loadPluginFromFile(file, installSource)
                if (plugin == null) {
                    replaceEntry(filePath) {
                        it.copy(
                            state = PluginState.Failed(
                                PluginErrorReason.CannotParse,
                                "loadPluginFromFile returned null",
                            ),
                            loaded = null,
                        )
                    }
                    PluginOperationResult(
                        operationType = PluginOperationType.ADD,
                        targetPlugins = listOf(filePath),
                        successCount = 0,
                        failureCount = 1,
                        failures = listOf(
                            PluginOperationFailure(
                                sourceRef = filePath,
                                errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                                message = "插件加载失败",
                            ),
                        ),
                        startedAtEpochMs = startedAt,
                        finishedAtEpochMs = System.currentTimeMillis(),
                    )
                } else {
                    addOrReplacePlugin(plugin)
                    PluginOperationResult(
                        operationType = PluginOperationType.ADD,
                        targetPlugins = listOf(plugin.info.platform),
                        successCount = 1,
                        failureCount = 0,
                        failures = emptyList(),
                        startedAtEpochMs = startedAt,
                        finishedAtEpochMs = System.currentTimeMillis(),
                    )
                }
            } catch (e: MissingPlatformException) {
                replaceEntry(filePath) {
                    it.copy(
                        state = PluginState.Failed(
                            PluginErrorReason.MissingPlatform,
                            e.message ?: "platform field is blank",
                        ),
                        loaded = null,
                    )
                }
                PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(filePath),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = filePath,
                            errorCode = PluginOperationErrorCode.MISSING_PLATFORM,
                            message = "插件缺少 platform 字段",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                replaceEntry(filePath) {
                    it.copy(
                        state = PluginState.Failed(
                            PluginErrorReason.CannotParse,
                            e.message ?: e::class.qualifiedName,
                        ),
                        loaded = null,
                    )
                }
                PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(filePath),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = filePath,
                            errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                            message = e.message ?: "插件加载失败",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
    }

    /**
     * Lookup a [LoadedPlugin] in [_plugins] (i.e., a Mounted entry) by its
     * source byte hash. Used by the install pipeline to silently dedup
     * idempotent re-installs (Phase C5).
     */
    private fun findMountedByHash(hash: String): LoadedPlugin? {
        if (hash.isEmpty()) return null
        return _plugins.value.firstOrNull { it.info.hash == hash }
    }

    /**
     * Push a [PluginState.Failed] entry into `_entries`, replacing any prior
     * row with the same `filePath`. Used by every install/load failure branch
     * so the UI can render the error and offer retry/uninstall actions.
     *
     * Failed entries are NOT added to `_plugins` — only Mounted ones are.
     */
    private fun recordFailedEntry(
        targetPath: String,
        reason: PluginErrorReason,
        detail: String?,
        installSource: PluginInstallSource?,
        attemptedPlatform: String?,
    ) {
        _entries.update { existing ->
            val withoutPrior = existing.filterNot { it.filePath == targetPath }
            withoutPrior + PluginEntry(
                filePath = targetPath,
                state = PluginState.Failed(reason, detail),
                info = null,
                loaded = null,
                installSource = installSource,
                attemptedPlatform = attemptedPlatform,
            )
        }
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_state_transition",
            fields = mapOf(
                "operation" to "record_failed_entry",
                "filePath" to targetPath,
                "to" to PluginStateKeys.STATE_FAILED,
                "reason" to PluginStateKeys.reasonKey(reason),
                "detail" to (detail ?: ""),
            ),
        )
    }

    private fun newFlowId(): String = UUID.randomUUID().toString()

    internal suspend fun evaluateAppVersionGate(
        plugin: LoadedPlugin,
        operation: String,
        fileName: String,
    ): PluginState.Failed? {
        val constraint = plugin.info.appVersion
        val skipVersionCheck = appPreferences.skipPluginVersionCheck.first()
        if (skipVersionCheck) {
            if (!constraint.isNullOrBlank()) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_appversion_gate_skipped",
                    fields = mapOf(
                        "operation" to operation,
                        "fileName" to fileName,
                        "platform" to plugin.info.platform,
                        "requiredAppVersion" to constraint,
                        "currentAppVersion" to currentAppVersion,
                        "reason" to "user_setting_skip_plugin_version_check",
                    ),
                )
            }
            return null
        }
        return appVersionGate.evaluate(
            constraint = constraint,
            appVersion = currentAppVersion,
        )
    }

    private fun logOperationStart(
        flowId: String,
        operation: String,
        platform: String? = null,
        url: String? = null,
        targetCount: Int? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_operation_start",
            fields = pluginOperationFields(
                flowId = flowId,
                operation = operation,
                platform = platform,
                url = url,
                targetCount = targetCount,
                result = null,
                extraFields = extraFields,
            ),
        )
    }

    private fun logOperationSuccess(
        flowId: String,
        operation: String,
        platform: String? = null,
        targetCount: Int? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_operation_success",
            fields = pluginOperationFields(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = targetCount,
                result = LogFields.Result.SUCCESS,
                extraFields = extraFields,
            ),
        )
    }

    private fun logOperationFailure(
        flowId: String,
        operation: String,
        throwable: Throwable,
        platform: String? = null,
        targetCount: Int? = null,
        reason: String = LogFields.Reason.UNKNOWN,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.error(
            category = LogCategory.PLUGIN,
            event = "plugin_operation_failed",
            throwable = throwable,
            fields = pluginOperationFields(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = targetCount,
                result = LogFields.Result.FAILURE,
                reason = reason,
                extraFields = extraFields,
            ),
        )
    }

    private fun logOperationCancelled(
        flowId: String,
        operation: String,
        platform: String? = null,
        targetCount: Int? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_operation_cancelled",
            fields = pluginOperationFields(
                flowId = flowId,
                operation = operation,
                platform = platform,
                targetCount = targetCount,
                result = LogFields.Result.CANCELLED,
                reason = LogFields.Reason.CANCELLED,
                extraFields = extraFields,
            ),
        )
    }

    private fun logSinglePluginResult(
        flowId: String,
        operation: String,
        url: String? = null,
        plugin: LoadedPlugin?,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val fields = pluginOperationFields(
            flowId = flowId,
            operation = operation,
            platform = plugin?.info?.platform,
            url = url,
            targetCount = 1,
            result = if (plugin != null) LogFields.Result.SUCCESS else LogFields.Result.FAILURE,
            reason = if (plugin == null) LogFields.Reason.UNKNOWN else null,
            extraFields = extraFields,
        )
        if (plugin != null) {
            MfLog.detail(LogCategory.PLUGIN, "plugin_operation_success", fields)
        } else {
            MfLog.error(LogCategory.PLUGIN, "plugin_operation_failed", fields = fields)
        }
    }

    private fun logSubscriptionInstallResult(
        flowId: String,
        operation: String,
        url: String,
        result: SubscriptionInstallResult,
    ) {
        val isSuccess = result.failedInstalls == 0 && result.errorMessage == null
        val fields = pluginOperationFields(
            flowId = flowId,
            operation = operation,
            url = url,
            targetCount = result.totalEntries,
            result = if (isSuccess) LogFields.Result.SUCCESS else LogFields.Result.FAILURE,
            reason = if (isSuccess) null else LogFields.Reason.UNKNOWN,
            extraFields = mapOf(
                "successCount" to result.successfulInstalls,
                "failureCount" to result.failedInstalls,
                "errorMessage" to result.errorMessage,
            ),
        )
        if (isSuccess) {
            MfLog.detail(LogCategory.PLUGIN, "plugin_operation_success", fields)
        } else {
            MfLog.error(LogCategory.PLUGIN, "plugin_operation_failed", fields = fields)
        }
    }

    private fun logPluginOperationResult(
        flowId: String,
        operation: String,
        platform: String? = null,
        url: String? = null,
        result: PluginOperationResult,
    ) {
        val firstFailure = result.failures.firstOrNull()
        val isSuccess = result.isSuccess
        val fields = pluginOperationFields(
            flowId = flowId,
            operation = operation,
            platform = platform ?: firstFailure?.targetPlugin,
            url = url ?: firstFailure?.sourceRef,
            targetCount = result.targetPlugins.size,
            result = if (isSuccess) LogFields.Result.SUCCESS else LogFields.Result.FAILURE,
            reason = if (isSuccess) null else firstFailure?.errorCode?.name?.lowercase().orEmpty()
                .ifBlank { LogFields.Reason.UNKNOWN },
            extraFields = mapOf(
                "operationType" to result.operationType.name.lowercase(),
                "successCount" to result.successCount,
                "failureCount" to result.failureCount,
                "durationMs" to (result.finishedAtEpochMs - result.startedAtEpochMs).coerceAtLeast(0),
                "firstFailureMessage" to firstFailure?.message,
                "targetPlugins" to result.targetPlugins.take(8),
            ),
        )
        if (isSuccess) {
            MfLog.detail(LogCategory.PLUGIN, "plugin_operation_success", fields)
        } else {
            MfLog.error(LogCategory.PLUGIN, "plugin_operation_failed", fields = fields)
        }
    }

    private fun pluginOperationFields(
        flowId: String,
        operation: String,
        platform: String? = null,
        url: String? = null,
        targetCount: Int? = null,
        result: String? = null,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>(
            "flowId" to flowId,
            "operation" to operation,
        )
        if (!platform.isNullOrBlank()) fields["platform"] = platform
        if (!url.isNullOrBlank()) {
            fields["url"] = url
            fields["host"] = LogFields.host(url)
        }
        if (targetCount != null) fields["targetCount"] = targetCount
        if (result != null) fields["result"] = result
        if (reason != null) fields["reason"] = reason
        fields.putAll(extraFields.filterValues { it != null })
        return fields
    }

    /**
     * Map the most-recently recorded Failed entry at [targetPath] to a
     * [PluginOperationErrorCode] suitable for the install-pipeline result. Used
     * by [installFromNetworkUrl] (and helpers) which only see a nullable
     * [LoadedPlugin] from [installWithStagedFile] and would otherwise have to
     * report every failure as the generic SOURCE_INVALID. Defaults to
     * SOURCE_INVALID when no matching Failed entry is found.
     */
    private fun inferFailureErrorCode(targetPath: String): PluginOperationErrorCode {
        val failedEntry = _entries.value
            .lastOrNull { it.filePath == targetPath && it.state is PluginState.Failed }
            ?: return PluginOperationErrorCode.SOURCE_INVALID
        val reason = (failedEntry.state as PluginState.Failed).reason
        return when (reason) {
            PluginErrorReason.VersionNotMatch -> PluginOperationErrorCode.VERSION_REJECTED
            PluginErrorReason.MissingPlatform -> PluginOperationErrorCode.MISSING_PLATFORM
            PluginErrorReason.DownloadFailed -> PluginOperationErrorCode.SOURCE_UNREACHABLE
            PluginErrorReason.CannotParse -> PluginOperationErrorCode.SOURCE_INVALID
            PluginErrorReason.UserVariableSyncFailed -> PluginOperationErrorCode.INTERNAL_ERROR
        }
    }

    private fun PluginOperationErrorCode.toUiMessage(): String = when (this) {
        PluginOperationErrorCode.VERSION_REJECTED -> "插件不兼容当前应用版本"
        PluginOperationErrorCode.MISSING_PLATFORM -> "插件缺少 platform 字段"
        PluginOperationErrorCode.SOURCE_UNREACHABLE -> "下载失败"
        PluginOperationErrorCode.SOURCE_INVALID -> "插件格式无效"
        PluginOperationErrorCode.MISSING_UPDATE_SOURCE -> "没有更新源"
        PluginOperationErrorCode.VERSION_NOT_UPGRADABLE -> "版本不可升级"
        @Suppress("DEPRECATION")
        PluginOperationErrorCode.DUPLICATE_PLUGIN -> "插件已存在"
        PluginOperationErrorCode.INTERNAL_ERROR -> "未知错误"
    }

    /**
     * Atomic in-place replacement of a single entry. Used by [retryEntry] for
     * transitions like Failed → Loading → Mounted/Failed.
     */
    private fun replaceEntry(filePath: String, mutator: (PluginEntry) -> PluginEntry) {
        _entries.update { existing ->
            existing.map { if (it.filePath == filePath) mutator(it) else it }
        }
    }

    private suspend fun installFromFileLocked(sourceFile: File): LoadedPlugin? {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_install_start",
            fields = mapOf(
                "operation" to "install_from_file",
                "status" to "start",
                "fileName" to sourceFile.name,
            ),
        )
        val installSource = PluginInstallSource(
            type = PluginInstallSourceType.LOCAL_FILE,
            value = sourceFile.absolutePath,
        )
        return try {
            val sourceBytes = sourceFile.readBytes()
            val sourceHash = sha256Hex(sourceBytes)

            // Phase C5: hash-collision silent idempotent. If a mounted plugin
            // with this exact byte hash exists, do not re-install — return the
            // existing entry. This matches RN's installPlugin behaviour where
            // duplicate installs are a no-op rather than a "DUPLICATE" error.
            val existing = findMountedByHash(sourceHash)
            if (existing != null) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_idempotent",
                    fields = mapOf(
                        "operation" to "install_from_file",
                        "status" to "success",
                        "platform" to existing.info.platform,
                        "fileName" to sourceFile.name,
                        "hash" to sourceHash,
                    ),
                )
                return existing
            }

            val (plugin, durationMs) = timedSuspend {
                installWithStagedFile(
                    fileName = sourceFile.name,
                    installSource = installSource,
                    sourceHash = sourceHash,
                ) { stagedFile ->
                    stagedFile.writeBytes(sourceBytes)
                    true
                }
            }

            if (plugin != null) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_success",
                    fields = mapOf(
                        "operation" to "install_from_file",
                        "status" to "success",
                        "platform" to plugin.info.platform,
                        "fileName" to sourceFile.name,
                        "durationMs" to durationMs,
                    ),
                )
            } else {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_failed",
                    fields = mapOf(
                        "operation" to "install_from_file",
                        "status" to "failed",
                        "fileName" to sourceFile.name,
                    ),
                )
            }
            plugin
        } catch (e: Exception) {
            recordFailedEntry(
                targetPath = File(pluginsDir, sourceFile.name).absolutePath,
                reason = PluginErrorReason.CannotParse,
                detail = e.message ?: e::class.qualifiedName,
                installSource = installSource,
                attemptedPlatform = null,
            )
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "install_from_file",
                    "status" to "failed",
                    "fileName" to sourceFile.name,
                    "state" to PluginStateKeys.STATE_FAILED,
                    "reason" to PluginStateKeys.REASON_CANNOT_PARSE,
                ),
            )
            null
        }
    }

    private suspend fun installFromBytesLocked(
        bytes: ByteArray,
        fileName: String,
        installSource: PluginInstallSource,
    ): LoadedPlugin? {
        val sourceHash = sha256Hex(bytes)

        // Phase C5: hash-collision silent idempotent. Return existing if
        // bytes already correspond to a mounted plugin.
        val existing = findMountedByHash(sourceHash)
        if (existing != null) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_install_idempotent",
                fields = mapOf(
                    "operation" to "install_from_bytes",
                    "status" to "success",
                    "platform" to existing.info.platform,
                    "fileName" to fileName,
                    "hash" to sourceHash,
                ),
            )
            return existing
        }

        return installWithStagedFile(
            fileName = fileName,
            installSource = installSource,
            sourceHash = sourceHash,
        ) { stagedFile ->
            stagedFile.writeBytes(bytes)
            true
        }
    }

    /**
     * Install every subscription entry from already-downloaded bytes. The
     * caller is responsible for fetching the subscription JSON and each
     * entry's plugin bytes OUTSIDE the lock (see
     * [prefetchSubscriptionForInstall]); this helper only mutates state
     * and writes files while [mutex] is held.
     */
    private suspend fun installFromSubscriptionUrlLocked(
        subscriptionUrl: String,
        parsed: SubscriptionParseResult,
        entryBytes: Map<String, ByteArray?>,
    ): SubscriptionInstallResult {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_install_start",
            fields = mapOf(
                "operation" to "install_from_subscription",
                "status" to "start",
                "url" to subscriptionUrl,
            ),
        )

        var successfulInstalls = 0
        for (entry in parsed.installableEntries) {
            val fileName = SubscriptionFileNames.pluginFileName(entry)
            val bytes = entryBytes[entry.url]
            val installSource = PluginInstallSource(
                type = PluginInstallSourceType.SUBSCRIPTION_URL,
                value = subscriptionUrl,
            )
            if (bytes == null) {
                recordFailedEntry(
                    targetPath = File(pluginsDir, fileName).absolutePath,
                    reason = PluginErrorReason.DownloadFailed,
                    detail = "HTTP/IO download failed for ${entry.url}",
                    installSource = installSource,
                    attemptedPlatform = null,
                )
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_failed",
                    fields = mapOf(
                        "operation" to "install_from_subscription",
                        "status" to "failed",
                        "url" to entry.url,
                        "fileName" to fileName,
                        "state" to PluginStateKeys.STATE_FAILED,
                        "reason" to PluginStateKeys.REASON_DOWNLOAD_FAILED,
                    ),
                )
                continue
            }
            if (installFromBytesLocked(bytes, fileName, installSource) != null) {
                successfulInstalls += 1
            }
        }

        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_install_success",
            fields = mapOf(
                "operation" to "install_from_subscription",
                "status" to "success",
                "url" to subscriptionUrl,
                "totalEntries" to parsed.totalEntries,
                "successfulInstalls" to successfulInstalls,
                "failedInstalls" to parsed.totalEntries - successfulInstalls,
            ),
        )
        return SubscriptionInstallResult(
            totalEntries = parsed.totalEntries,
            successfulInstalls = successfulInstalls,
            failedInstalls = parsed.totalEntries - successfulInstalls,
        )
    }

    private suspend fun installWithStagedFile(
        fileName: String,
        installSource: PluginInstallSource,
        sourceHash: String?,
        populateStagedFile: (File) -> Boolean,
    ): JsLoadedPlugin? {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_replace_file_start",
            fields = mapOf(
                "operation" to "replace_plugin_file",
                "status" to "start",
                "fileName" to fileName,
            ),
        )

        val startedAt = System.currentTimeMillis()
        val targetFile = File(pluginsDir, fileName)
        val stagedFile = createStagedPluginFile(fileName)

        try {
            val populated = populateStagedFile(stagedFile)
            if (!populated) {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_replace_file_failed",
                    fields = mapOf(
                        "operation" to "replace_plugin_file",
                        "status" to "failed",
                        "fileName" to fileName,
                        "message" to "staged_file_population_failed",
                    ),
                )
                recordFailedEntry(
                    targetPath = targetFile.absolutePath,
                    reason = PluginErrorReason.CannotParse,
                    detail = "staged_file_population_failed",
                    installSource = installSource,
                    attemptedPlatform = null,
                )
                return null
            }

            val plugin = try {
                loadPluginFromFile(stagedFile, installSource)
            } catch (e: MissingPlatformException) {
                // Phase C: record structured Failed entry so UI can show the
                // MissingPlatform reason and offer retry/uninstall actions.
                recordFailedEntry(
                    targetPath = targetFile.absolutePath,
                    reason = PluginErrorReason.MissingPlatform,
                    detail = e.message ?: "platform field is blank in $fileName",
                    installSource = installSource,
                    attemptedPlatform = null,
                )
                return null
            }

            if (plugin == null) {
                recordFailedEntry(
                    targetPath = targetFile.absolutePath,
                    reason = PluginErrorReason.CannotParse,
                    detail = "loadPluginFromFile returned null for $fileName",
                    installSource = installSource,
                    attemptedPlatform = null,
                )
                return null
            }

            // Phase E: appVersion semver gate. Plugin declared `appVersion` must
            // be satisfied by the host's versionName, otherwise we refuse to
            // install — destroy the engine that just got built up, record a
            // Failed(VersionNotMatch) entry and DO NOT atomically move the
            // staged file into pluginsDir (no littering of dead .js plugin files).
            val versionRejection = evaluateAppVersionGate(
                plugin = plugin,
                operation = "appversion_gate",
                fileName = fileName,
            )
            if (versionRejection != null) {
                plugin.destroy()
                recordFailedEntry(
                    targetPath = targetFile.absolutePath,
                    reason = PluginErrorReason.VersionNotMatch,
                    detail = versionRejection.detail,
                    installSource = installSource,
                    attemptedPlatform = plugin.info.platform,
                )
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_failed",
                    fields = mapOf(
                        "operation" to "appversion_gate",
                        "status" to "failed",
                        "fileName" to fileName,
                        "platform" to plugin.info.platform,
                        "requiredAppVersion" to plugin.info.appVersion.orEmpty(),
                        "currentAppVersion" to currentAppVersion,
                        "state" to PluginStateKeys.STATE_FAILED,
                        "reason" to PluginStateKeys.REASON_VERSION_NOT_MATCH,
                    ),
                )
                return null
            }

            val replaced = replaceFileAtomically(source = stagedFile, target = targetFile)
            if (!replaced) {
                plugin.destroy()
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_replace_file_failed",
                    fields = mapOf(
                        "operation" to "replace_plugin_file",
                        "status" to "failed",
                        "fileName" to fileName,
                    ),
                )
                recordFailedEntry(
                    targetPath = targetFile.absolutePath,
                    reason = PluginErrorReason.CannotParse,
                    detail = "atomic_replace_failed",
                    installSource = installSource,
                    attemptedPlatform = plugin.info.platform,
                )
                return null
            }

            plugin.filePath = targetFile.absolutePath
            addOrReplacePlugin(plugin)
            writeInstallMetadata(targetFile, plugin.installSource)
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_replace_file_success",
                fields = mapOf(
                    "operation" to "replace_plugin_file",
                    "status" to "success",
                    "platform" to plugin.info.platform,
                    "fileName" to fileName,
                    "durationMs" to System.currentTimeMillis() - startedAt,
                    "state" to PluginStateKeys.STATE_MOUNTED,
                    "hash" to (sourceHash ?: plugin.info.hash ?: ""),
                ),
            )
            return plugin
        } finally {
            // Staged file is always cleaned up — covers BOTH success path
            // (already moved by replaceFileAtomically) and failure paths
            // (download succeeded but parse/missing-platform failed; we must
            // not leave a half-installed file on disk per Phase C5 contract).
            if (stagedFile.exists()) {
                stagedFile.delete()
            }
        }
    }

    private fun downloadUrlBytes(url: String): ByteArray? {
        val startedAt = System.currentTimeMillis()
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_download_start",
            fields = mapOf(
                "operation" to "download",
                "status" to "start",
                "url" to url,
            ),
        )
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "plugin_download_failed",
                        fields = mapOf(
                            "operation" to "download",
                            "status" to "failed",
                            "url" to url,
                            "statusCode" to response.code,
                            "durationMs" to System.currentTimeMillis() - startedAt,
                        ),
                    )
                    return null
                }
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_download_success",
                    fields = mapOf(
                        "operation" to "download",
                        "status" to "success",
                        "url" to url,
                        "statusCode" to response.code,
                        "durationMs" to System.currentTimeMillis() - startedAt,
                    ),
                )
                response.body.bytes()
            }
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_download_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "download",
                    "status" to "failed",
                    "url" to url,
                    "durationMs" to System.currentTimeMillis() - startedAt,
                ),
            )
            null
        }
    }

    private suspend fun addOrReplacePlugin(plugin: JsLoadedPlugin) {
        val current = _plugins.value.toMutableList()
        // JS plugin replacement compares platform name or file path. The
        // built-in local plugin has filePath == null, so the filePath
        // equality check below is gated on non-null values.
        val replaced = current.filter {
            it.info.platform == plugin.info.platform ||
                (it.filePath != null && it.filePath == plugin.filePath)
        }

        val replacedFilePaths = mutableSetOf<String>()
        replaced.forEach { existing ->
            existing.destroy()
            current.remove(existing)
            val existingPath = existing.filePath
            if (existingPath != null) {
                replacedFilePaths += existingPath
            }
            if (existingPath != null && existingPath != plugin.filePath) {
                val existingFile = File(existingPath)
                existingFile.delete()
                deleteInstallMetadata(existingFile)
            }
        }

        current += plugin
        _plugins.value = current

        // Phase C: mirror the same change in `_entries` (source of truth for
        // the new state machine). Drop entries whose filePath/platform was
        // replaced, and also drop any pre-existing Failed entry whose
        // attemptedPlatform now corresponds to the freshly-mounted plugin
        // (otherwise the UI would still show the prior error badge).
        val newPath = plugin.filePath
        _entries.update { existing ->
            val filtered = existing.filter { entry ->
                val byFilePath = entry.filePath != null &&
                    (entry.filePath in replacedFilePaths || entry.filePath == newPath)
                val byPlatform = (entry.info?.platform == plugin.info.platform) ||
                    (entry.attemptedPlatform == plugin.info.platform)
                !(byFilePath || byPlatform)
            }
            filtered + PluginEntry(
                filePath = plugin.filePath,
                state = PluginState.Mounted,
                info = plugin.info,
                loaded = plugin,
                installSource = plugin.installSource,
                attemptedPlatform = plugin.info.platform,
            )
        }
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_state_transition",
            fields = mapOf(
                "operation" to "add_or_replace_plugin",
                "platform" to plugin.info.platform,
                "filePath" to (plugin.filePath ?: ""),
                "to" to PluginStateKeys.STATE_MOUNTED,
            ),
        )
        // Phase E: persist the freshly-mounted plugin's metadata so the next
        // cold start can render it from cache without re-evaluating the JS.
        // Done after the StateFlow mutations so a cache write failure (which
        // we tolerate) does not interrupt the install pipeline.
        val pluginFilePath = plugin.filePath
        if (pluginFilePath != null) {
            upsertCacheRow(File(pluginFilePath), plugin.info)
        }
    }

    /**
     * Apply an update to a single plugin from already-downloaded bytes.
     * Callers MUST run the HTTP fetch via [downloadOutsideLock] BEFORE
     * acquiring [mutex] and pass the resulting bytes (or `null` for a
     * download failure) via [prefetchedBytes].
     */
    private suspend fun updateInstalledPluginLocked(
        targetPlugin: LoadedPlugin,
        prefetchedBytes: ByteArray?,
        operationType: PluginOperationType,
        startedAt: Long,
    ): PluginOperationResult {
        val sourceUrl = targetPlugin.info.srcUrl?.trim().orEmpty()
        if (sourceUrl.isBlank()) {
            return PluginOperationResult(
                operationType = operationType,
                targetPlugins = listOf(targetPlugin.info.platform),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = targetPlugin.info.platform,
                        errorCode = PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
                        message = "没有更新源",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        }

        // Built-in plugins (LocalLoadedPlugin) have no filePath; they should
        // never reach this branch because they are filtered out of update
        // candidate lists, but we still treat a null filePath as a missing
        // update source defensively.
        val targetFilePath = targetPlugin.filePath
        if (targetFilePath == null) {
            return PluginOperationResult(
                operationType = operationType,
                targetPlugins = listOf(targetPlugin.info.platform),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = targetPlugin.info.platform,
                        errorCode = PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
                        message = "没有更新源",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        }

        if (prefetchedBytes == null) {
            return PluginOperationResult(
                operationType = operationType,
                targetPlugins = listOf(targetPlugin.info.platform),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = targetPlugin.info.platform,
                        sourceRef = sourceUrl,
                        errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                        message = "插件更新失败",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        }

        val updated = installFromBytesLocked(
            bytes = prefetchedBytes,
            fileName = File(targetFilePath).name,
            installSource = PluginInstallSource(
                type = operationType.toInstallSourceType(),
                value = sourceUrl,
            ),
        )

        return if (updated != null) {
            PluginOperationResult(
                operationType = operationType,
                targetPlugins = listOf(targetPlugin.info.platform),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        } else {
            PluginOperationResult(
                operationType = operationType,
                targetPlugins = listOf(targetPlugin.info.platform),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = targetPlugin.info.platform,
                        sourceRef = sourceUrl,
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "插件更新失败",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Apply updates to multiple plugins from a `platform -> bytes-or-null`
     * map prefetched OUTSIDE the lock (see [updateAllPlugins]).
     */
    private suspend fun updateInstalledPluginsLocked(
        targets: List<LoadedPlugin>,
        prefetchedBytesByPlatform: Map<String, ByteArray?>,
        operationType: PluginOperationType,
        startedAt: Long,
    ): PluginOperationResult {
        val failures = mutableListOf<PluginOperationFailure>()
        var successCount = 0
        val targetNames = targets.map { it.info.platform }

        targets.forEach { plugin ->
            val result = updateInstalledPluginLocked(
                targetPlugin = plugin,
                prefetchedBytes = prefetchedBytesByPlatform[plugin.info.platform],
                operationType = operationType,
                startedAt = startedAt,
            )
            successCount += result.successCount
            failures += result.failures
        }

        return PluginOperationResult(
            operationType = operationType,
            targetPlugins = targetNames,
            successCount = successCount,
            failureCount = failures.size,
            failures = failures,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = System.currentTimeMillis(),
        )
    }

    /**
     * Apply subscription updates from already-downloaded bytes. Callers
     * MUST prefetch each entry's bytes OUTSIDE the lock (see
     * [prefetchSubscriptionEntries]) so this loop only does file writes
     * and state mutation under [mutex].
     */
    private suspend fun updateSubscriptionEntriesLocked(
        subscriptionUrl: String,
        entries: List<SubscriptionPluginEntry>,
        entryBytes: Map<String, ByteArray?>,
        totalEntries: Int,
        startedAt: Long,
        targets: List<String>,
        operationType: PluginOperationType = PluginOperationType.UPDATE_SUBSCRIPTION,
        installSourceType: PluginInstallSourceType = PluginInstallSourceType.UPDATE_SUBSCRIPTION,
    ): PluginOperationResult {
        val failures = mutableListOf<PluginOperationFailure>()
        var successCount = 0
        val invalidEntryCount = (totalEntries - entries.size).coerceAtLeast(0)

        repeat(invalidEntryCount) {
            failures += PluginOperationFailure(
                sourceRef = subscriptionUrl,
                errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                message = "订阅条目缺少插件地址",
            )
        }

        entries.forEach { entry ->
            val fileName = SubscriptionFileNames.pluginFileName(entry)
            val bytes = entryBytes[entry.url]
            val installed = if (bytes != null) {
                installFromBytesLocked(
                    bytes = bytes,
                    fileName = fileName,
                    installSource = PluginInstallSource(
                        type = installSourceType,
                        value = subscriptionUrl,
                    ),
                )
            } else {
                null
            }

            if (installed != null) {
                successCount += 1
            } else {
                failures += PluginOperationFailure(
                    targetPlugin = entry.name ?: entry.url,
                    sourceRef = entry.url,
                    errorCode = if (bytes == null) {
                        PluginOperationErrorCode.SOURCE_UNREACHABLE
                    } else {
                        PluginOperationErrorCode.SOURCE_INVALID
                    },
                    message = "订阅插件更新失败",
                )
            }
        }

        return PluginOperationResult(
            operationType = operationType,
            targetPlugins = targets,
            successCount = successCount,
            failureCount = failures.size,
            failures = failures,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun PluginOperationType.toInstallSourceType(): PluginInstallSourceType {
        return when (this) {
            PluginOperationType.UPDATE_SINGLE -> PluginInstallSourceType.UPDATE_SINGLE
            PluginOperationType.UPDATE_ALL -> PluginInstallSourceType.UPDATE_ALL
            PluginOperationType.UPDATE_SUBSCRIPTION -> PluginInstallSourceType.UPDATE_SUBSCRIPTION
            PluginOperationType.ADD -> PluginInstallSourceType.PLUGIN_URL
        }
    }

    private fun metadataFileFor(pluginFile: File): File {
        return File(pluginFile.parentFile, "${pluginFile.nameWithoutExtension}$PLUGIN_META_SUFFIX")
    }

    private fun writeInstallMetadata(pluginFile: File, installSource: PluginInstallSource) {
        runCatching {
            val props = Properties().apply {
                setProperty("sourceType", installSource.type.name)
                setProperty("sourceValue", installSource.value.orEmpty())
            }
            FileOutputStream(metadataFileFor(pluginFile)).use { output ->
                props.store(output, null)
            }
        }.onFailure {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_metadata_save_failed",
                throwable = it,
                fields = mapOf(
                    "operation" to "save_install_metadata",
                    "status" to "failed",
                    "fileName" to pluginFile.name,
                ),
            )
        }
    }

    private fun readInstallMetadata(pluginFile: File): PluginInstallSource {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_read_start",
            fields = mapOf(
                "operation" to "read_install_metadata",
                "status" to "start",
                "fileName" to pluginFile.name,
            ),
        )

        val metaFile = metadataFileFor(pluginFile)
        if (!metaFile.exists()) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_metadata_read_success",
                fields = mapOf(
                    "operation" to "read_install_metadata",
                    "status" to "success",
                    "fileName" to pluginFile.name,
                    "metadataExists" to false,
                    "type" to PluginInstallSourceType.LOCAL_FILE.name,
                ),
            )
            return PluginInstallSource(
                type = PluginInstallSourceType.LOCAL_FILE,
                value = pluginFile.absolutePath,
            )
        }

        return runCatching {
            val props = Properties()
            FileInputStream(metaFile).use { input ->
                props.load(input)
            }

            val type = runCatching {
                PluginInstallSourceType.valueOf(props.getProperty("sourceType"))
            }.getOrElse {
                PluginInstallSourceType.LOCAL_FILE
            }

            PluginInstallSource(
                type = type,
                value = props.getProperty("sourceValue").takeUnless { it.isNullOrBlank() },
            )
        }.getOrElse {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_metadata_read_failed",
                throwable = it,
                fields = mapOf(
                    "operation" to "read_install_metadata",
                    "status" to "failed",
                    "fileName" to pluginFile.name,
                ),
            )
            PluginInstallSource(
                type = PluginInstallSourceType.LOCAL_FILE,
                value = pluginFile.absolutePath,
            )
        }
    }

    private fun String.escapeForJsString(): String =
        replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun deleteInstallMetadata(pluginFile: File) {
        runCatching {
            val metaFile = metadataFileFor(pluginFile)
            if (metaFile.exists()) {
                metaFile.delete()
            }
        }
    }

    private fun createStagedPluginFile(fileName: String): File {
        val safeBaseName = fileName.removeSuffix(".js").ifBlank { "plugin" }
        return File(pluginsDir, "$safeBaseName-${UUID.randomUUID()}.tmp")
    }

    private fun replaceFileAtomically(source: File, target: File): Boolean {
        return try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_replace_file_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "replace_plugin_file",
                    "status" to "failed",
                    "fileName" to target.name,
                ),
            )
            false
        }
    }

    /**
     * Load a single plugin from a JS file.
     *
     * Steps:
     * 1. Read JS source code
     * 2. Create a JsEngine and register shims (axios, require, console)
     * 3. Wrap the plugin code in a CommonJS-style module wrapper
     * 4. Extract plugin metadata from `__plugin`
     * 5. Return a [JsLoadedPlugin] (always a JS-backed plugin; the built-in
     *    local plugin is registered separately by [buildLocalEntry]).
     */
    private suspend fun loadPluginFromFile(
        file: File,
        installSource: PluginInstallSource,
    ): JsLoadedPlugin? {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_load_parse_start",
            fields = mapOf(
                "operation" to "load",
                "status" to "start",
                "fileName" to file.name,
            ),
        )

        val jsBytes = file.readBytes()
        val jsCode = String(jsBytes, StandardCharsets.UTF_8)
        val sourceHash = sha256Hex(jsBytes)
        val engine = JsEngine.create()

        // Gather env values.
        // `lang` is hardcoded to "zh-CN" to match RN MusicFree behavior — many
        // plugins branch on `env.lang === "zh-CN"` and previously misbehaved when
        // we passed through `Locale.getDefault().toLanguageTag()` (e.g.
        // `zh-Hans-CN` on some emulators). See plugin-engine-alignment design §8.2.
        // [currentAppVersion] is injected so tests can override it.
        val appVersion = currentAppVersion
        val lang = "zh-CN"

        val info = try {
            // Bootstrap polyfills (must run before user code or require shim).
            BootstrapShim.register(appContext = context, engine = engine)

            // Register shims
            AxiosShim.register(engine)
            WebDavShim.register(engine)
            RequireShim.register(appContext = context, engine = engine)

            // Inject env object + process global.
            // `process.platform` / `process.env` mirrors Node.js conventions that RN
            // plugins (and shared libraries like `axios`) probe on entry.
            engine.evaluate<Any?>("""
                globalThis.__env = {
                    os: 'android',
                    appVersion: '${appVersion.escapeForJsString()}',
                    lang: '${lang.escapeForJsString()}',
                    getUserVariables: function() { return globalThis.__userVariables || {}; }
                };
                globalThis.process = {
                    platform: 'android',
                    version: globalThis.__env.appVersion,
                    env: globalThis.__env
                };
            """.trimIndent())

            // Wrap plugin code in CommonJS-style module pattern and assign to __plugin
                val wrappedCode = """
                var module = {exports: {}};
                var exports = module.exports;
                (function(require, module, exports, console, env) {
                    $jsCode
                })(globalThis.__require, module, exports, console, globalThis.__env);
                globalThis.__plugin = module.exports;
            """.trimIndent()

            try {
                engine.evaluate<Any?>(wrappedCode)
            } catch (e: Exception) {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_error",
                    throwable = e,
                    fields = mapOf(
                        "operation" to "load",
                        "status" to "failed",
                        "fileName" to file.name,
                        "phase" to "evaluate",
                    ),
                )
                engine.close()
                return null
            }

            // Extract metadata from __plugin. MissingPlatformException is
            // propagated to the outer catch so callers can classify it as
            // PluginErrorReason.MissingPlatform rather than the generic
            // CannotParse bucket.
            try {
                extractPluginInfo(engine, sourceHash)
            } catch (e: Exception) {
                if (e is MissingPlatformException) {
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "plugin_error",
                        throwable = e,
                        fields = mapOf(
                            "operation" to "load",
                            "status" to "failed",
                            "fileName" to file.name,
                            "phase" to "extract_metadata",
                            "state" to PluginStateKeys.STATE_FAILED,
                            "reason" to PluginStateKeys.REASON_MISSING_PLATFORM,
                        ),
                    )
                    engine.close()
                    throw e
                }
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_error",
                    throwable = e,
                    fields = mapOf(
                        "operation" to "load",
                        "status" to "failed",
                        "fileName" to file.name,
                        "phase" to "extract_metadata",
                    ),
                )
                engine.close()
                return null
            }
        } catch (e: Exception) {
            if (e is MissingPlatformException) {
                // Already closed engine + logged inside inner catch.
                throw e
            }
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_error",
                throwable = e,
                fields = mapOf(
                    "operation" to "load",
                    "status" to "failed",
                    "fileName" to file.name,
                ),
            )
            engine.close()
            return null
        }

        // Inject userVariables snapshot for this plugin
        val userVars = pluginMetaStore.getUserVariables(info.platform).first()
        if (userVars.isNotEmpty()) {
            val jsonStr = Json.encodeToString(userVars)
            engine.evaluate<Any?>("globalThis.__userVariables = JSON.parse('${jsonStr.escapeForJsString()}')")
        }

        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_load_parse_success",
            fields = mapOf(
                "operation" to "load",
                "status" to "success",
                "platform" to info.platform,
                "fileName" to file.name,
            ),
        )

        return JsLoadedPlugin(
            info = info,
            engine = engine,
            filePath = file.absolutePath,
            projector = bridgeProjector,
            installSource = installSource,
        )
    }

    /**
     * Extract [PluginInfo] from the loaded `__plugin` global object.
     *
     * @param sourceHash SHA-256 hex of the plugin's raw JS bytes, used by the
     *   install pipeline for idempotent dedup (Phase C5). Null for sources
     *   where bytes are unavailable.
     */
    private suspend fun extractPluginInfo(engine: JsEngine, sourceHash: String?): PluginInfo {
        suspend fun prop(name: String): String? {
            val result = engine.evaluate<Any?>("__plugin.$name")
            val str = result?.toString()
            return if (str == "undefined" || str == "null" || str.isNullOrBlank()) null else str
        }

        suspend fun supportedMethods(): Set<String> {
            return CORE_PLUGIN_METHODS.filter { method ->
                try {
                    engine.evaluate<Boolean>("typeof __plugin.$method === 'function'")
                } catch (_: Exception) {
                    false
                }
            }.toSet()
        }

        suspend fun userVariables(): List<PluginUserVariable> {
            val raw = runCatching {
                engine.evaluate<Any?>("JSON.stringify(__plugin.userVariables)")?.toString()
            }.onFailure {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_user_variables_stringify_failed",
                    throwable = it,
                    fields = mapOf("status" to "failed"),
                )
            }.getOrNull()
            return parsePluginUserVariables(raw)
        }

        val platform = prop("platform")
            ?: throw MissingPlatformException(
                "platform field is blank or missing on module.exports",
            )

        val supportedSearchTypeStr = prop("supportedSearchType")
        val supportedSearchTypeDeclared = !supportedSearchTypeStr.isNullOrBlank()
        val supportedSearchType = if (!supportedSearchTypeStr.isNullOrBlank()) {
            try {
                val json = engine.evaluate<Any?>("JSON.stringify(__plugin.supportedSearchType)")?.toString()
                if (json != null && json.startsWith("[")) {
                    json.removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }
                        .filter { it.isNotBlank() }
                } else {
                    listOf("music")
                }
            } catch (_: Exception) {
                listOf("music")
            }
        } else {
            emptyList()
        }

        val hintsJson = try {
            val raw = engine.evaluate<Any?>("JSON.stringify(__plugin.hints)")?.toString()
            if (raw != null && raw != "undefined" && raw != "null" && raw.startsWith("{")) {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, List<String>>>(raw)
            } else null
        } catch (e: Exception) { null }

        return PluginInfo(
            platform = platform,
            version = prop("version"),
            author = prop("author"),
            description = prop("description"),
            srcUrl = prop("srcUrl"),
            supportedSearchType = supportedSearchType,
            supportedSearchTypeDeclared = supportedSearchTypeDeclared,
            appVersion = prop("appVersion"),
            primaryKey = prop("primaryKey"),
            defaultSearchType = prop("defaultSearchType"),
            cacheControl = prop("cacheControl"),
            hints = hintsJson,
            supportedMethods = supportedMethods(),
            userVariables = userVariables(),
            hash = sourceHash,
        )
    }

    /**
     * SHA-256 of the given bytes, hex-encoded lowercase. Used by the install
     * pipeline to compute [PluginInfo.hash] and for the hash-collision
     * silent-idempotent check (Phase C5).
     */
    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val v = byte.toInt() and 0xFF
            builder.append(HEX_CHARS[v ushr 4])
            builder.append(HEX_CHARS[v and 0x0F])
        }
        return builder.toString()
    }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

/**
 * Thrown by [PluginManager.extractPluginInfo] when `module.exports.platform` is
 * blank or missing. Distinguishes a structural-parse problem from a JS runtime
 * error so the install pipeline can map it to [PluginErrorReason.MissingPlatform].
 */
internal class MissingPlatformException(message: String) : RuntimeException(message)

private fun PluginInfo.supportsSearchType(type: String): Boolean =
    type in supportedSearchType || !supportedSearchTypeDeclared

internal fun parsePluginUserVariables(raw: String?): List<PluginUserVariable> {
    if (raw == null || raw == "undefined" || raw == "null" || !raw.startsWith("[")) {
        return emptyList()
    }
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.optString("key").trim()
                if (key.isBlank()) continue
                add(
                    PluginUserVariable(
                        key = key,
                        name = item.optString("name").trim().takeIf { it.isNotBlank() },
                        hint = item.optString("hint").trim().takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }.onFailure {
        MfLog.error(
            category = LogCategory.PLUGIN,
            event = "plugin_user_variables_parse_failed",
            throwable = it,
            fields = mapOf("status" to "failed"),
        )
    }.getOrDefault(emptyList())
}
