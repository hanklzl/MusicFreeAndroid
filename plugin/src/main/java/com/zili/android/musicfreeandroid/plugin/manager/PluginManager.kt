package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import android.util.Log
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.engine.AxiosShim
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
import com.zili.android.musicfreeandroid.plugin.engine.RequireShim
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
        private const val TAG = "PluginManager"
        private const val PLUGINS_DIR_NAME = "plugins"
        private const val PLUGIN_META_SUFFIX = ".meta.properties"
    }

    private val pluginsDir: File by lazy {
        File(context.filesDir, PLUGINS_DIR_NAME).also { it.mkdirs() }
    }

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins.asStateFlow()

    private val mutex = Mutex()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
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
                try {
                    val plugin = loadPluginFromFile(file, readInstallMetadata(file))
                    if (plugin != null) {
                        loaded.add(plugin)
                        Log.i(TAG, "Loaded plugin: ${plugin.info.platform} from ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load plugin from ${file.name}", e)
                }
            }
            _plugins.value = loaded
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
            if (plugin != null) {
                plugin.destroy()
                File(plugin.filePath).delete()
                deleteInstallMetadata(File(plugin.filePath))
                current.remove(plugin)
                _plugins.value = current
                Log.i(TAG, "Uninstalled plugin: $platform")
            } else {
                Log.w(TAG, "Plugin not found for uninstall: $platform")
            }
        }
    }

    /**
     * Get a loaded plugin by platform name.
     */
    fun getPlugin(platform: String): LoadedPlugin? {
        return _plugins.value.find { it.info.platform == platform }
    }

    private suspend fun installFromFileLocked(sourceFile: File): LoadedPlugin? {
        return try {
            installWithStagedFile(
                fileName = sourceFile.name,
                installSource = PluginInstallSource(
                    type = PluginInstallSourceType.LOCAL_FILE,
                    value = sourceFile.absolutePath,
                ),
            ) { stagedFile ->
                sourceFile.copyTo(stagedFile, overwrite = true)
                true
            }?.also { plugin ->
                Log.i(TAG, "Installed plugin: ${plugin.info.platform} from file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin from file: ${sourceFile.name}", e)
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
        return try {
            val bytes = downloadUrlBytes(url) ?: return null
            installFromBytesLocked(bytes, fileName, installSource)?.also { plugin ->
                Log.i(TAG, "Installed plugin: ${plugin.info.platform} from URL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin from URL: $url", e)
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
        if (subscriptionUrl.isBlank()) {
            return SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅地址不能为空",
            )
        }

        val rawJson = downloadUrlBytes(subscriptionUrl)?.toString(StandardCharsets.UTF_8)
            ?: return SubscriptionInstallResult(
                totalEntries = 0,
                successfulInstalls = 0,
                failedInstalls = 0,
                errorMessage = "订阅下载失败",
            )

        val parsed = SubscriptionParser.parse(rawJson)
        if (parsed.isMalformed) {
            Log.e(TAG, "Malformed subscription JSON from $subscriptionUrl")
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
        val targetFile = File(pluginsDir, fileName)
        val stagedFile = createStagedPluginFile(fileName)

        try {
            val populated = populateStagedFile(stagedFile)
            if (!populated) {
                return null
            }

            val plugin = loadPluginFromFile(stagedFile, installSource) ?: return null
            val replaced = replaceFileAtomically(source = stagedFile, target = targetFile)
            if (!replaced) {
                plugin.destroy()
                return null
            }

            plugin.filePath = targetFile.absolutePath
            addOrReplacePlugin(plugin)
            writeInstallMetadata(targetFile, plugin.installSource)
            return plugin
        } finally {
            if (stagedFile.exists()) {
                stagedFile.delete()
            }
        }
    }

    private fun downloadUrlBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download from $url: ${response.code}")
                    return null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download content from $url", e)
            null
        }
    }

    private fun addOrReplacePlugin(plugin: LoadedPlugin) {
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
        startedAt: Long,
        targets: List<String>,
    ): PluginOperationResult {
        val failures = mutableListOf<PluginOperationFailure>()
        var successCount = 0

        entries.forEach { entry ->
            val fileName = SubscriptionFileNames.pluginFileName(entry)
            val bytes = downloadUrlBytes(entry.url)
            val installed = if (bytes != null) {
                installFromBytesLocked(
                    bytes = bytes,
                    fileName = fileName,
                    installSource = PluginInstallSource(
                        type = PluginInstallSourceType.UPDATE_SUBSCRIPTION,
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
            operationType = PluginOperationType.UPDATE_SUBSCRIPTION,
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
            Log.w(TAG, "Failed to persist install metadata for ${pluginFile.name}", it)
        }
    }

    private fun readInstallMetadata(pluginFile: File): PluginInstallSource {
        val metaFile = metadataFileFor(pluginFile)
        if (!metaFile.exists()) {
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
            Log.e(TAG, "Failed to replace plugin file ${target.name}", e)
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
     *
     * All QuickJS operations run on the engine's dedicated JS thread to
     * satisfy QuickJSContext's thread affinity requirement.
     */
    private suspend fun loadPluginFromFile(
        file: File,
        installSource: PluginInstallSource,
    ): LoadedPlugin? {
        val jsCode = file.readText()
        val engine = JsEngine()

        // Gather env values before entering JS thread
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val lang = java.util.Locale.getDefault().toLanguageTag()

        val info = engine.runOnJsThread {
            engine.create()

            val ctx = engine.context
                ?: throw IllegalStateException("Failed to create QuickJS context")

            // Register axios shim
            AxiosShim.register(ctx, engine)

            // Register CommonJS require shim with built-in modules from assets.
            RequireShim.register(appContext = context, context = ctx)

            // Inject env object
            engine.evaluate("""
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
                engine.evaluate(wrappedCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to evaluate plugin code from ${file.name}", e)
                engine.destroy()
                return@runOnJsThread null
            }

            // Extract metadata from __plugin
            try {
                extractPluginInfo(engine)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract plugin info from ${file.name}", e)
                engine.destroy()
                null
            }
        } ?: return null

        // Inject userVariables snapshot for this plugin (outside JS thread to allow suspend)
        val userVars = pluginMetaStore.getUserVariables(info.platform).first()
        if (userVars.isNotEmpty()) {
            engine.runOnJsThread {
                val jsonStr = Json.encodeToString(userVars)
                engine.evaluate("globalThis.__userVariables = JSON.parse('${jsonStr.escapeForJsString()}')")
            }
        }

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
    private fun extractPluginInfo(engine: JsEngine): PluginInfo {
        fun prop(name: String): String? {
            val result = engine.evaluate("__plugin.$name")
            val str = result?.toString()
            return if (str == "undefined" || str == "null" || str.isNullOrBlank()) null else str
        }

        val platform = prop("platform")
            ?: throw IllegalStateException("Plugin missing required 'platform' property")

        val supportedSearchTypeStr = prop("supportedSearchType")
        val supportedSearchType = if (!supportedSearchTypeStr.isNullOrBlank()) {
            try {
                // Evaluate as JSON array
                val json = engine.evaluate("JSON.stringify(__plugin.supportedSearchType)")?.toString()
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
            listOf("music")
        }

        // Parse hints: { [methodName]: string[] }
        val hintsJson = try {
            val raw = engine.evaluate("JSON.stringify(__plugin.hints)")?.toString()
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
            appVersion = prop("appVersion"),
            primaryKey = prop("primaryKey"),
            defaultSearchType = prop("defaultSearchType"),
            cacheControl = prop("cacheControl"),
            hints = hintsJson,
        )
    }
}
