package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import android.util.Log
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.engine.AxiosShim
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
import com.zili.android.musicfreeandroid.plugin.engine.RequireShim
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets
import java.util.UUID
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
) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PLUGINS_DIR_NAME = "plugins"
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
                    val plugin = loadPluginFromFile(file)
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
     * Uninstall a plugin by platform name.
     */
    suspend fun uninstall(platform: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = _plugins.value.toMutableList()
            val plugin = current.find { it.info.platform == platform }
            if (plugin != null) {
                plugin.destroy()
                File(plugin.filePath).delete()
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
            installWithStagedFile(fileName = sourceFile.name) { stagedFile ->
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

    private suspend fun installFromUrlLocked(url: String, fileName: String): LoadedPlugin? {
        return try {
            installWithStagedFile(fileName = fileName) { stagedFile ->
                val bytes = downloadUrlBytes(url) ?: return@installWithStagedFile false
                stagedFile.writeBytes(bytes)
                true
            }?.also { plugin ->
                Log.i(TAG, "Installed plugin: ${plugin.info.platform} from URL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin from URL: $url", e)
            null
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
            if (installFromUrlLocked(url = entry.url, fileName = fileName) != null) {
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
        populateStagedFile: (File) -> Boolean,
    ): LoadedPlugin? {
        val targetFile = File(pluginsDir, fileName)
        val stagedFile = createStagedPluginFile(fileName)

        try {
            val populated = populateStagedFile(stagedFile)
            if (!populated) {
                return null
            }

            val plugin = loadPluginFromFile(stagedFile) ?: return null
            val replaced = replaceFileAtomically(source = stagedFile, target = targetFile)
            if (!replaced) {
                plugin.destroy()
                return null
            }

            plugin.filePath = targetFile.absolutePath
            addOrReplacePlugin(plugin)
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
                File(existing.filePath).delete()
            }
        }

        current += plugin
        _plugins.value = current
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
    private suspend fun loadPluginFromFile(file: File): LoadedPlugin? {
        val jsCode = file.readText()
        val engine = JsEngine()

        engine.runOnJsThread {
            engine.create()

            val ctx = engine.context
                ?: throw IllegalStateException("Failed to create QuickJS context")

            // Register axios shim
            AxiosShim.register(ctx, engine)

            // Register CommonJS require shim with built-in modules from assets.
            RequireShim.register(appContext = context, context = ctx)

            // Wrap plugin code in CommonJS-style module pattern and assign to __plugin
            val wrappedCode = """
                var module = {exports: {}};
                var exports = module.exports;
                (function(require, module, exports, console, env) {
                    $jsCode
                })(globalThis.__require, module, exports, console, {os: 'android'});
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
        }?.let { info ->
            return LoadedPlugin(info = info, engine = engine, filePath = file.absolutePath)
        }

        return null
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

        return PluginInfo(
            platform = platform,
            version = prop("version"),
            author = prop("author"),
            description = prop("description"),
            srcUrl = prop("srcUrl"),
            supportedSearchType = supportedSearchType,
        )
    }
}
