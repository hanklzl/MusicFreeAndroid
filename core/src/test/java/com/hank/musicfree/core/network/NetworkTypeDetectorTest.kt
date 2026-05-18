package com.hank.musicfree.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯 JVM 测试，使用 mockk 对 ConnectivityManager 打桩；不引入 Robolectric。
 * NetworkRequest.Builder 在 JVM 单测下会抛 `RuntimeException("Stub!")`，因此 init 块里的
 * registerNetworkCallback 调用会被 runCatching 静默吞掉，但首次 activeNetwork 分类逻辑仍然生效，
 * 足以覆盖纯函数语义；callback 路径留给 instrumentation / 集成测试。
 */
class NetworkTypeDetectorTest {

    private fun newContextWith(cm: ConnectivityManager): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(ConnectivityManager::class.java) } returns cm
        return ctx
    }

    @Test
    fun defaults_to_OTHER_when_no_active_network() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        every { cm.activeNetwork } returns null
        val detector = NetworkTypeDetector(newContextWith(cm))
        assertEquals(NetworkType.OTHER, detector.current())
    }

    @Test
    fun reports_WIFI_when_active_network_has_wifi_transport() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        val detector = NetworkTypeDetector(newContextWith(cm))
        assertEquals(NetworkType.WIFI, detector.current())
    }

    @Test
    fun reports_CELLULAR_when_active_network_has_cellular_transport() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true

        val detector = NetworkTypeDetector(newContextWith(cm))
        assertEquals(NetworkType.CELLULAR, detector.current())
    }

    @Test
    fun reports_OTHER_when_no_known_transport() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        val detector = NetworkTypeDetector(newContextWith(cm))
        assertEquals(NetworkType.OTHER, detector.current())
    }
}
