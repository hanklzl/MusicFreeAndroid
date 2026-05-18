package com.hank.musicfree.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTypeDetector @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cm: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val cachedType = AtomicReference(NetworkType.OTHER)

    init {
        runCatching {
            cm.activeNetwork?.let { cachedType.set(classify(cm.getNetworkCapabilities(it))) }
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    cachedType.set(classify(caps))
                }
            })
        }
    }

    fun current(): NetworkType = cachedType.get()

    private fun classify(caps: NetworkCapabilities?): NetworkType = when {
        caps == null -> NetworkType.OTHER
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        else -> NetworkType.OTHER
    }
}
