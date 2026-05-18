package com.hank.musicfree.plugin.harness.contracts

import com.hank.musicfree.plugin.engine.AxiosShim
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Contract: [AxiosShim.setBaseClient] must propagate the caller's
 * [okhttp3.EventListener.Factory] onto the shim's internal `baseClient`.
 *
 * In production, `MusicFreeApplication.onCreate` hands in `@BaseOkHttp` (which
 * has [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory]).
 * If a future refactor accidentally drops the factory while reseating the
 * client (e.g. by calling `OkHttpClient.Builder()` from scratch), this test
 * fails because the sentinel factory we set up no longer matches the one
 * stored in `baseClient`.
 *
 * Plain JUnit (no Hilt graph required) — [AxiosShim] is a Kotlin `object`,
 * the test surface is the static setter.
 */
class AxiosShimClientContractTest {

    @Test
    fun setBaseClient_propagates_event_listener_factory() {
        val sentinel = object : EventListener.Factory {
            override fun create(call: okhttp3.Call) = EventListener.NONE
        }
        val client = OkHttpClient.Builder().eventListenerFactory(sentinel).build()
        AxiosShim.setBaseClient(client)

        // Reflection: AxiosShim.baseClient is private @Volatile var.
        val field = AxiosShim::class.java.getDeclaredField("baseClient")
        field.isAccessible = true
        val stored = field.get(AxiosShim) as OkHttpClient
        assertSame(
            "AxiosShim.baseClient must inherit the caller's EventListener.Factory",
            sentinel,
            stored.eventListenerFactory,
        )
    }
}
