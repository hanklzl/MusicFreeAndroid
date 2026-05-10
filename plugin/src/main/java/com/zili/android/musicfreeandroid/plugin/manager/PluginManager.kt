package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.PluginUserVariable
import com.zili.android.musicfreeandroid.plugin.engine.AxiosShim
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
import com.zili.android.musicfreeandroid.plugin.engine.RequireShim
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.timedSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import javax.inject.Singleton

/**
 * Manages the lifecycle of JS plugins: loading from disk, installing from file/URL,
 * and uninstalling. Exposes loaded plugins as a [StateFlow].
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val pluginMetaStore: PluginMetaStore,
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

    private val pluginsDir: File by lazy {
        File(context.filesDir, PLUGINS_DIR_NAME).also { it.mkdirs() }
    }

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins.asStateFlow()

    private val mutex = Mutex()
    private val _loaded = AtomicBoolean(false)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
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
     * Load all .js plugin files from the plugins directory.
     */
    suspend fun loadAllPlugins() = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Destroy any previously loaded plugins
            _plugins.value.forEach { it.destroy() }

            val loaded = mutableListOf<LoadedPlugin>()
            val files = pluginsDir.listFiles { _, name -> name.endsWith(".js") } ?: emptyArray()
            for (file in files) {
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
                        loadPluginFromFile(file, readInstallMetadata(file))
                    }
                    if (plugin != null) {
                        loaded.add(plugin)
                        MfLog.detail(
                            category = LogCategory.PLUGIN,
                            event = "plugin_load_success",
                            fields = mapOf(
                                "operation" to "load",
                                "status" to "success",
                                "platform" to plugin.info.platform,
                                "fileName" to file.name,
                                "durationMs" to durationMs,
                            ),
                        )
                    } else {
                        MfLog.error(
                            category = LogCategory.PLUGIN,
                            event = "plugin_load_failed",
                            fields = mapOf(
                                "operation" to "load",
                                "status" to "failed",
                                "fileName" to file.name,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "plugin_load_failed",
                        throwable = e,
                        fields = mapOf(
                            "operation" to "load",
                            "status" to "failed",
                            "fileName" to file.name,
                        ),
                    )
                }
            }
            _plugins.value = loaded
            _loaded.set(true)
        }
    }

    /**
     * Install a plugin from a local file by copying it to the plugins directory.
     */
    suspend fun installFromFile(sourceFile: File): LoadedPlugin? = mutex.withLock {
        withContext(Dispatchers.IO) {
            installFromFileLocked(sourceFile)
        }
    }

    /**
     * Install a plugin by downloading it from a URL.
     */
    suspend fun installFromUrl(url: String, fileName: String): LoadedPlugin? = mutex.withLock {
        withContext(Dispatchers.IO) {
            installFromUrlLocked(url = url, fileName = fileName)
        }
    }

    /**
     * Install from a network URL and return structured operation feedback.
     *
     * A `.json` URL is treated as a MusicFree subscription payload and each
     * `plugins[].url` entry is installed. Other URLs are treated as plugin JS.
     */
    suspend fun installFromNetworkUrl(url: String): PluginOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val trimmed = url.trim()
            if (trimmed.isBlank()) {
                return@withContext PluginOperationResult(
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
                )
            }

            val normalizedPath = trimmed.substringBefore("#").substringBefore("?")
            if (normalizedPath.endsWith(".json", ignoreCase = true)) {
                val rawJson = downloadUrlBytes(trimmed)
                    ?: return@withContext PluginOperationResult(
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
                    )

                val parsed = SubscriptionParser.parse(rawJson.toString(StandardCharsets.UTF_8))
                if (parsed.isMalformed) {
                    return@withContext PluginOperationResult(
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
                    )
                }

                val targets = parsed.installableEntries.map { it.url }
                return@withContext updateSubscriptionEntriesLocked(
                    subscriptionUrl = trimmed,
                    entries = parsed.installableEntries,
                    totalEntries = parsed.totalEntries,
                    startedAt = startedAt,
                    targets = targets,
                    operationType = PluginOperationType.ADD,
                    installSourceType = PluginInstallSourceType.SUBSCRIPTION_URL,
                )
            }

            val fileName = SubscriptionFileNames.networkPluginFileName(trimmed)
            val bytes = downloadUrlBytes(trimmed)
            if (bytes == null) {
                return@withContext PluginOperationResult(
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
                )
            }

            val installed = installFromBytesLocked(
                bytes = bytes,
                fileName = fileName,
                installSource = PluginInstallSource(
                    type = PluginInstallSourceType.PLUGIN_URL,
                    value = trimmed,
                ),
            )

            if (installed != null) {
                PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(installed.info.platform),
                    successCount = 1,
                    failureCount = 0,
                    failures = emptyList(),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            } else {
                PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(trimmed),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = trimmed,
                            errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                            message = "插件格式无效",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
    }

    /**
     * Install plugins listed in a subscription JSON URL.
     */
    suspend fun installFromSubscriptionUrl(subscriptionUrl: String): SubscriptionInstallResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            installFromSubscriptionUrlLocked(subscriptionUrl)
        }
    }

    /**
     * Update a single installed plugin using its source URL.
     */
    suspend fun updatePlugin(platform: String): PluginOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val plugin = _plugins.value.find { it.info.platform == platform }
                ?: return@withContext PluginOperationResult(
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

            updateInstalledPluginLocked(
                targetPlugin = plugin,
                operationType = PluginOperationType.UPDATE_SINGLE,
                startedAt = startedAt,
            )
        }
    }

    /**
     * Update all installed plugins that expose an update source.
     */
    suspend fun updateAllPlugins(): PluginOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            updateInstalledPluginsLocked(
                targets = _plugins.value.toList(),
                operationType = PluginOperationType.UPDATE_ALL,
                startedAt = startedAt,
            )
        }
    }

    /**
     * Update plugins listed in a subscription JSON URL.
     */
    suspend fun updateFromSubscriptionUrl(subscriptionUrl: String): PluginOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            if (subscriptionUrl.isBlank()) {
                return@withContext PluginOperationResult(
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
                )
            }

            val rawJson = downloadUrlBytes(subscriptionUrl)
                ?: return@withContext PluginOperationResult(
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
                )

            val parsed = SubscriptionParser.parse(rawJson.toString(StandardCharsets.UTF_8))
            if (parsed.isMalformed) {
                return@withContext PluginOperationResult(
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
                )
            }

            val targets = parsed.installableEntries.map { it.url }
            updateSubscriptionEntriesLocked(
                subscriptionUrl = subscriptionUrl,
                entries = parsed.installableEntries,
                totalEntries = parsed.totalEntries,
                startedAt = startedAt,
                targets = targets,
            )
        }
    }

    /**
     * Uninstall a plugin by platform name.
     */
    suspend fun uninstall(platform: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = _plugins.value.toMutableList()
            val plugin = current.find { it.info.platform == platform }
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_uninstall_start",
                fields = mapOf(
                    "operation" to "uninstall",
                    "status" to "start",
                    "platform" to platform,
                ),
            )
            try {
                if (plugin != null) {
                    plugin.destroy()
                    File(plugin.filePath).delete()
                    deleteInstallMetadata(File(plugin.filePath))
                    current.remove(plugin)
                    _plugins.value = current
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "plugin_uninstall_success",
                        fields = mapOf(
                            "operation" to "uninstall",
                            "status" to "success",
                            "platform" to platform,
                            "fileName" to plugin.filePath.substringAfterLast('/'),
                        ),
                    )
                } else {
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "plugin_uninstall_failed",
                        fields = mapOf(
                            "operation" to "uninstall",
                            "status" to "failed",
                            "platform" to platform,
                        ),
                    )
                }
            } catch (e: Exception) {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_uninstall_failed",
                    throwable = e,
                    fields = mapOf(
                        "operation" to "uninstall",
                        "status" to "failed",
                        "platform" to platform,
                    ),
                )
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
     * Sorted list of enabled plugins. Combines loaded plugins with
     * disabled set and order from [PluginMetaStore].
     * Plugins not in the order list are appended at the end.
     */
    fun getSortedEnabledPlugins(): Flow<List<LoadedPlugin>> =
        combine(
            plugins,
            pluginMetaStore.disabledPlugins,
            pluginMetaStore.pluginOrder,
        ) { allPlugins, disabled, order ->
            val enabled = allPlugins.filter { it.info.platform !in disabled }
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
        pluginMetaStore.setPluginEnabled(platform, enabled)
    }

    suspend fun setPluginOrder(order: List<String>) {
        pluginMetaStore.setPluginOrder(order)
    }

    suspend fun setUserVariables(platform: String, variables: Map<String, String>) {
        mutex.withLock {
            _plugins.value.firstOrNull { it.info.platform == platform }
                ?.updateUserVariables(variables)
            pluginMetaStore.setUserVariables(platform, variables)
        }
    }

    suspend fun uninstallAllPlugins() {
        val platforms = _plugins.value.map { it.info.platform }
        for (platform in platforms) {
            uninstall(platform)
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
        return try {
            val (plugin, durationMs) = timedSuspend {
                installWithStagedFile(
                    fileName = sourceFile.name,
                    installSource = PluginInstallSource(
                        type = PluginInstallSourceType.LOCAL_FILE,
                        value = sourceFile.absolutePath,
                    ),
                ) { stagedFile ->
                    sourceFile.copyTo(stagedFile, overwrite = true)
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
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "install_from_file",
                    "status" to "failed",
                    "fileName" to sourceFile.name,
                ),
            )
            null
        }
    }

    private suspend fun installFromUrlLocked(
        url: String,
        fileName: String,
        installSource: PluginInstallSource = PluginInstallSource(
            type = PluginInstallSourceType.PLUGIN_URL,
            value = url,
        ),
    ): LoadedPlugin? {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_install_start",
            fields = mapOf(
                "operation" to "install_from_url",
                "status" to "start",
                "url" to url,
                "fileName" to fileName,
            ),
        )

        return try {
            val bytes = downloadUrlBytes(url) ?: return null
            val (plugin, durationMs) = timedSuspend {
                installFromBytesLocked(bytes, fileName, installSource)
            }
            if (plugin != null) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_success",
                    fields = mapOf(
                        "operation" to "install_from_url",
                        "status" to "success",
                        "platform" to plugin.info.platform,
                        "url" to url,
                        "fileName" to fileName,
                        "durationMs" to durationMs,
                    ),
                )
            } else {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_failed",
                    fields = mapOf(
                        "operation" to "install_from_url",
                        "status" to "failed",
                        "url" to url,
                        "fileName" to fileName,
                    ),
                )
            }
            plugin
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "install_from_url",
                    "status" to "failed",
                    "url" to url,
                    "fileName" to fileName,
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
        return installWithStagedFile(fileName = fileName, installSource = installSource) { stagedFile ->
            stagedFile.writeBytes(bytes)
            true
        }
    }

    private suspend fun installFromSubscriptionUrlLocked(subscriptionUrl: String): SubscriptionInstallResult {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_install_start",
            fields = mapOf(
                "operation" to "install_from_subscription",
                "status" to "start",
                "url" to subscriptionUrl,
            ),
        )
        if (subscriptionUrl.isBlank()) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                fields = mapOf(
                    "operation" to "install_from_subscription",
                    "status" to "failed",
                    "url" to subscriptionUrl,
                    "message" to "subscription_url_empty",
                ),
            )
            return SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅地址不能为空",
            )
        }

        val rawJson = downloadUrlBytes(subscriptionUrl)?.toString(StandardCharsets.UTF_8)
            ?: run {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_install_failed",
                    fields = mapOf(
                        "operation" to "install_from_subscription",
                        "status" to "failed",
                        "url" to subscriptionUrl,
                        "message" to "subscription_download_failed",
                    ),
                )
                return SubscriptionInstallResult(
                    totalEntries = 0,
                    successfulInstalls = 0,
                    failedInstalls = 0,
                    errorMessage = "订阅下载失败",
                )
            }

        val parsed = SubscriptionParser.parse(rawJson)
        if (parsed.isMalformed) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_install_failed",
                fields = mapOf(
                    "operation" to "install_from_subscription",
                    "status" to "failed",
                    "url" to subscriptionUrl,
                    "message" to "subscription_json_invalid",
                ),
            )
            return SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅格式无效",
            )
        }

        var successfulInstalls = 0
        for (entry in parsed.installableEntries) {
            val fileName = SubscriptionFileNames.pluginFileName(entry)
            if (
                installFromUrlLocked(
                    url = entry.url,
                    fileName = fileName,
                    installSource = PluginInstallSource(
                        type = PluginInstallSourceType.SUBSCRIPTION_URL,
                        value = subscriptionUrl,
                    ),
                ) != null
            ) {
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
        populateStagedFile: (File) -> Boolean,
    ): LoadedPlugin? {
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
                return null
            }

            val plugin = loadPluginFromFile(stagedFile, installSource) ?: return null
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
                ),
            )
            return plugin
        } finally {
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
                response.body?.bytes()
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

    private suspend fun addOrReplacePlugin(plugin: LoadedPlugin) {
        val current = _plugins.value.toMutableList()
        val replaced = current.filter {
            it.info.platform == plugin.info.platform || it.filePath == plugin.filePath
        }

        replaced.forEach { existing ->
            existing.destroy()
            current.remove(existing)
            if (existing.filePath != plugin.filePath) {
                val existingFile = File(existing.filePath)
                existingFile.delete()
                deleteInstallMetadata(existingFile)
            }
        }

        current += plugin
        _plugins.value = current
    }

    private suspend fun updateInstalledPluginLocked(
        targetPlugin: LoadedPlugin,
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

        val bytes = downloadUrlBytes(sourceUrl)
        if (bytes == null) {
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
            bytes = bytes,
            fileName = File(targetPlugin.filePath).name,
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

    private suspend fun updateInstalledPluginsLocked(
        targets: List<LoadedPlugin>,
        operationType: PluginOperationType,
        startedAt: Long,
    ): PluginOperationResult {
        val failures = mutableListOf<PluginOperationFailure>()
        var successCount = 0
        val targetNames = targets.map { it.info.platform }

        targets.forEach { plugin ->
            val result = updateInstalledPluginLocked(plugin, operationType, startedAt)
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

    private suspend fun updateSubscriptionEntriesLocked(
        subscriptionUrl: String,
        entries: List<SubscriptionPluginEntry>,
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
            val bytes = downloadUrlBytes(entry.url)
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
     * 5. Return a [LoadedPlugin]
     */
    private suspend fun loadPluginFromFile(
        file: File,
        installSource: PluginInstallSource,
    ): LoadedPlugin? {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_load_parse_start",
            fields = mapOf(
                "operation" to "load",
                "status" to "start",
                "fileName" to file.name,
            ),
        )

        val jsCode = file.readText()
        val engine = JsEngine.create()

        // Gather env values
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val lang = java.util.Locale.getDefault().toLanguageTag()

        val info = try {
            // Register shims
            AxiosShim.register(engine)
            RequireShim.register(appContext = context, engine = engine)

            // Inject env object
            engine.evaluate<Any?>("""
                globalThis.__env = {
                    os: 'android',
                    appVersion: '${appVersion.escapeForJsString()}',
                    lang: '${lang.escapeForJsString()}',
                    getUserVariables: function() { return globalThis.__userVariables || {}; }
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

            // Extract metadata from __plugin
            try {
                extractPluginInfo(engine)
            } catch (e: Exception) {
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

        return LoadedPlugin(
            info = info,
            engine = engine,
            filePath = file.absolutePath,
            installSource = installSource,
        )
    }

    /**
     * Extract [PluginInfo] from the loaded `__plugin` global object.
     */
    private suspend fun extractPluginInfo(engine: JsEngine): PluginInfo {
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
            ?: throw IllegalStateException("Plugin missing required 'platform' property")

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
        )
    }
}

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
