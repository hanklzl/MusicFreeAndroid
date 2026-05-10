package com.zili.android.musicfreeandroid.downloader.io

import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStateMappingTest {

    @Test fun nullCapabilitiesMeansOffline() {
        assertEquals(NetworkState.Offline, NetworkMonitor.mapToNetworkState(null))
    }

    @Test fun wifiTransportMapsToWifi() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        }
        assertEquals(NetworkState.Wifi, NetworkMonitor.mapToNetworkState(nc))
    }

    @Test fun cellularTransportMapsToCellular() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        }
        assertEquals(NetworkState.Cellular, NetworkMonitor.mapToNetworkState(nc))
    }

    @Test fun internetWithoutValidatedStillCountsButNoInternetIsOffline() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
            every { hasTransport(any()) } returns false
        }
        assertEquals(NetworkState.Offline, NetworkMonitor.mapToNetworkState(nc))
    }
}
