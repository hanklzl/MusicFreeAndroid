package com.zili.android.musicfreeandroid.plugin.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probe / regression test for the `URL` constructor in the plugin runtime.
 *
 * Validates that after running [BootstrapShim.register] equivalent (or whatever
 * native support QuickJS-kt provides), `new URL(...)` produces correct
 * `href` / `host` / `pathname` / `search` for a typical RN MusicFree usage.
 *
 * Lives in androidTest because QuickJS-kt-android requires JNI .so binaries.
 *
 * Note: this test exercises the polyfill directly by evaluating its source on a
 * bare engine; the contract test [RuntimeCompatContractTest] covers the
 * production wiring (BootstrapShim.register inside PluginManager).
 *
 * Design source: plugin-engine-alignment design §8.3.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeUrlConstructorContractTest {

    private lateinit var engine: JsEngine

    @Before
    fun setUp() {
        engine = JsEngine.create()
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun nativeUrlConstructorVerdict() = runBlocking {
        // Probe: does the bare QuickJS-kt runtime expose `URL` without any
        // polyfill? Records the verdict for the plan: if `available` is true,
        // the polyfill is technically unnecessary but harmless. If false, the
        // polyfill is required (and is shipped via BootstrapShim).
        val verdict = engine.evaluate<String>(
            """
            (() => {
              try {
                if (typeof URL !== 'function') return JSON.stringify({ available: false, reason: 'typeof URL is ' + typeof URL });
                const u = new URL('https://example.com/path?q=1');
                return JSON.stringify({
                  available: true,
                  href: u.href,
                  host: u.host,
                  pathname: u.pathname,
                  search: u.search
                });
              } catch (e) {
                return JSON.stringify({ available: false, reason: String(e) });
              }
            })()
            """.trimIndent()
        )
        // The verdict is informational — we don't fail the build either way.
        // We only assert it is non-empty so the harness curator can grep the
        // logged probe result.
        val obj = JSONObject(verdict)
        // Surface the verdict in the test log so the polyfill decision is
        // traceable from CI artifacts. Both outcomes are valid for the
        // production wiring — BootstrapShim's idempotent JS check ensures
        // we don't double-install when QuickJS-kt adds URL natively.
        println("[URL_PROBE_VERDICT] $verdict")
        // Smoke check: at least one of the expected branches occurred.
        assertEquals(true, obj.has("available"))
    }

    @Test
    fun polyfillProducesCorrectUrlParts() = runBlocking {
        // Sanity-check the BootstrapShim polyfill output directly. Read the
        // asset, evaluate it on a bare engine, then probe.
        val appContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.applicationContext
        val polyfillSource = appContext.assets
            .open("jslibs/url-polyfill.js")
            .bufferedReader()
            .use { it.readText() }
        // Force re-installation of the polyfill so we deterministically test
        // OUR implementation regardless of any future built-in URL the engine
        // might gain. We delete `globalThis.URL` first, then re-evaluate.
        engine.evaluate<Any?>("delete globalThis.URL;")
        engine.evaluate<Any?>(polyfillSource)

        val raw = engine.evaluate<String>(
            """
            (() => {
              const u = new URL('https://user:pass@example.com:8080/path/seg?q=1&k=2#frag');
              return JSON.stringify({
                href: u.href,
                protocol: u.protocol,
                host: u.host,
                hostname: u.hostname,
                port: u.port,
                pathname: u.pathname,
                search: u.search,
                hash: u.hash,
                username: u.username,
                password: u.password,
                origin: u.origin
              });
            })()
            """.trimIndent()
        )
        val obj = JSONObject(raw)
        assertEquals("https:", obj.optString("protocol"))
        // href excludes userinfo per whatwg URL semantics
        assertEquals(
            "https://example.com:8080/path/seg?q=1&k=2#frag",
            obj.optString("href"),
        )
        assertEquals("example.com:8080", obj.optString("host"))
        assertEquals("example.com", obj.optString("hostname"))
        assertEquals("8080", obj.optString("port"))
        assertEquals("/path/seg", obj.optString("pathname"))
        assertEquals("?q=1&k=2", obj.optString("search"))
        assertEquals("#frag", obj.optString("hash"))
        assertEquals("user", obj.optString("username"))
        assertEquals("pass", obj.optString("password"))
        assertEquals("https://example.com:8080", obj.optString("origin"))
    }

    @Test
    fun polyfillSimpleUrlHasDefaultPathname() = runBlocking {
        val appContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.applicationContext
        val polyfillSource = appContext.assets
            .open("jslibs/url-polyfill.js")
            .bufferedReader()
            .use { it.readText() }
        engine.evaluate<Any?>("delete globalThis.URL;")
        engine.evaluate<Any?>(polyfillSource)

        val raw = engine.evaluate<String>(
            """
            (() => {
              const u = new URL('https://example.com');
              return JSON.stringify({ pathname: u.pathname, search: u.search, hash: u.hash });
            })()
            """.trimIndent()
        )
        val obj = JSONObject(raw)
        // Hierarchical URL with no explicit path → pathname defaults to "/"
        assertEquals("/", obj.optString("pathname"))
        assertEquals("", obj.optString("search"))
        assertEquals("", obj.optString("hash"))
    }
}
