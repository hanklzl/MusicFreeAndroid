package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import android.util.Log
import com.whl.quickjs.wrapper.JSCallFunction
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.engine.AxiosShim
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
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
            try {
                val destFile = File(pluginsDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                val plugin = loadPluginFromFile(destFile)
                if (plugin != null) {
                    _plugins.value = _plugins.value + plugin
                    Log.i(TAG, "Installed plugin: ${plugin.info.platform} from file")
                }
                plugin
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install plugin from file: ${sourceFile.name}", e)
                null
            }
        }
    }

    /**
     * Install a plugin by downloading it from a URL.
     */
    suspend fun installFromUrl(url: String, fileName: String): LoadedPlugin? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download plugin from $url: ${response.code}")
                    return@withContext null
                }
                val destFile = File(pluginsDir, fileName)
                destFile.writeBytes(response.body?.bytes() ?: return@withContext null)

                val plugin = loadPluginFromFile(destFile)
                if (plugin != null) {
                    _plugins.value = _plugins.value + plugin
                    Log.i(TAG, "Installed plugin: ${plugin.info.platform} from URL")
                }
                plugin
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install plugin from URL: $url", e)
                null
            }
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

            // Register __require shim: currently only supports 'axios'
            ctx.globalObject.setProperty("__require", JSCallFunction { args ->
                val moduleName = args.getOrNull(0)?.toString() ?: ""
                when (moduleName) {
                    "axios" -> ctx.globalObject.getProperty("axios")
                    else -> {
                        Log.w(TAG, "require('$moduleName') not supported, returning empty object")
                        ctx.createNewJSObject()
                    }
                }
            })

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
