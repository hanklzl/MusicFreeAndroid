package com.hank.musicfree.plugin.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.plugin.api.musicItems
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.manager.makeTestManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

/**
 * Contract test that the plugin runtime exposes the four globals that RN
 * MusicFree plugins rely on:
 *
 *  1. `process.platform === "android"` (Node-style env probe; many plugins
 *     gate Android-specific code paths on this).
 *  2. `process.env.lang === "zh-CN"` (mirror of `env.lang`).
 *  3. `typeof URL === "function"` (whatwg URL constructor, either built-in
 *     to QuickJS-kt or polyfilled by `assets/jslibs/url-polyfill.js`).
 *  4. `require('webdav').createClient` is a function.
 *  5. `env.lang === "zh-CN"` (hardcoded; not derived from Locale).
 *
 * Implementation strategy: install a probe plugin whose `search()` returns
 * a MusicItem whose `title` is the JSON-encoded probe payload. That payload
 * survives the bridge intact (no string mangling), and a single integration
 * round-trip exercises the entire env injection + require shim + URL probe
 * pipeline as it appears in production.
 *
 * Lives in androidTest because QuickJS-kt-android ships JNI .so binaries
 * that Robolectric can't load on a desktop JVM.
 *
 * Design source: plugin-engine-alignment design §8.2 / §8.3 / §8.4.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeCompatContractTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        cacheDir = appContext.cacheDir
        pluginManager = makeTestManager(prefix = "runtime-compat-it", appContext = appContext)
    }

    @After
    fun tearDown() {
        runBlocking { pluginManager.uninstallAllPlugins() }
    }

    @Test
    fun runtime_exposes_process_url_webdav_and_zh_cn_lang() = runBlocking {
        val pluginFile = File.createTempFile("runtime-compat-", ".js", cacheDir)
        pluginFile.writeText(PROBE_PLUGIN_SCRIPT)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("probe plugin should install", plugin)

        val result = plugin!!.search(query = "compat", page = 1)
        val items = result.musicItems()
        assertEquals("probe plugin returns exactly one item", 1, items.size)

        val payload = JSONObject(items.single().title)

        // (1) process global present
        assertEquals("android", payload.optString("processPlatform"))
        assertTrue(
            "process.env should be defined",
            payload.optBoolean("hasProcessEnv"),
        )

        // (2) process.env.lang mirrors env.lang
        assertEquals("zh-CN", payload.optString("processEnvLang"))

        // (3) URL constructor produces correct parts (built-in or polyfilled)
        assertEquals("https://example.com/path?q=1", payload.optString("urlHref"))
        assertEquals("example.com", payload.optString("urlHost"))
        assertEquals("/path", payload.optString("urlPathname"))
        assertEquals("?q=1", payload.optString("urlSearch"))

        // (4) require('webdav') exposes createClient
        assertEquals("function", payload.optString("webdavCreateClientType"))

        // (5) env.lang is hardcoded zh-CN
        assertEquals("zh-CN", payload.optString("envLang"))

        // process.version exists (sourced from env.appVersion)
        assertTrue(
            "process.version should be a non-empty string, was '${payload.optString("processVersion")}'",
            payload.optString("processVersion").isNotEmpty(),
        )
    }

    private companion object {
        // The probe plugin echoes the runtime globals back through search().
        // Anything thrown here propagates as a plugin error → installFromFile
        // would return null (which the assertions above catch via the null check).
        //
        // We URL-stringify a probe URL because `new URL('...').toString()` must
        // hit the prototype's toString, which polyfills sometimes forget.
        val PROBE_PLUGIN_SCRIPT = """
            module.exports = {
              platform: 'runtime-compat-probe',
              version: '1.0.0',
              supportedSearchType: ['music'],
              async search(query, page, type) {
                const u = new URL('https://example.com/path?q=1');
                const wd = require('webdav');
                const probe = {
                  processPlatform: typeof process !== 'undefined' ? process.platform : null,
                  hasProcessEnv: typeof process !== 'undefined' && !!process.env,
                  processEnvLang: typeof process !== 'undefined' && process.env ? process.env.lang : null,
                  processVersion: typeof process !== 'undefined' ? process.version : null,
                  urlHref: u.href,
                  urlHost: u.host,
                  urlPathname: u.pathname,
                  urlSearch: u.search,
                  webdavCreateClientType: typeof (wd && wd.createClient),
                  envLang: env.lang
                };
                return {
                  isEnd: true,
                  data: [{
                    id: 'probe-1',
                    platform: 'runtime-compat-probe',
                    title: JSON.stringify(probe),
                    artist: 'probe',
                    album: 'probe',
                    duration: 1,
                    url: 'https://example.com'
                  }]
                };
              }
            };
        """.trimIndent()
    }
}
