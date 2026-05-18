package com.hank.musicfree.plugin.harness.contracts

import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Contract: [PluginManager]'s internal `httpClient` (used by `downloadOutsideLock`
 * for plugin/subscription HTTP fetches) MUST be derived from the `@BaseOkHttp`
 * client so plugin downloads flow through
 * [NetworkTrafficEventListener.Factory] and feed into `traffic_daily`.
 *
 * Failure mode this guards: someone writes a new `OkHttpClient.Builder().build()`
 * inside PluginManager — the test fails because `eventListenerFactory` will be
 * OkHttp's internal default, not our base factory.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [29])
class PluginManagerClientContractTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var pluginManager: PluginManager
    @Inject @field:BaseOkHttp lateinit var baseClient: OkHttpClient

    @Test fun plugin_manager_client_event_listener_factory_matches_base() {
        hilt.inject()
        // PluginManager.httpClient is a private `val ... by lazy { ... }`.
        // Kotlin stores the delegate under `<name>$delegate`; we read it,
        // then unwrap the Lazy<OkHttpClient> to force initialization and
        // get the actual client instance.
        val field = PluginManager::class.java.getDeclaredField("httpClient\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lazyDelegate = field.get(pluginManager) as kotlin.Lazy<OkHttpClient>
        val client = lazyDelegate.value
        assertTrue(
            "PluginManager httpClient.eventListenerFactory must be NetworkTrafficEventListener.Factory, " +
                "was ${client.eventListenerFactory::class.qualifiedName}",
            client.eventListenerFactory is NetworkTrafficEventListener.Factory,
        )
        assertSame(
            "PluginManager httpClient.eventListenerFactory must be the SAME factory instance as @BaseOkHttp",
            baseClient.eventListenerFactory,
            client.eventListenerFactory,
        )
    }
}
