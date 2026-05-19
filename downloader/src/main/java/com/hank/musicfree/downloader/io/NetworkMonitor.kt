package com.hank.musicfree.downloader.io

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkState { Offline, Wifi, Cellular }

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Returns a [StateFlow] that emits `true` when the active network is Wi-Fi or Ethernet,
     * `false` otherwise. Intended for use by prefetch components that should only run on Wi-Fi.
     */
    val isWifi: StateFlow<Boolean> = _state
        .map { it == NetworkState.Wifi }
        .stateIn(monitorScope, SharingStarted.Eagerly, _state.value == NetworkState.Wifi)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { recompute() }
        override fun onLost(network: Network) { recompute() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { recompute() }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(request, callback)
        recompute()
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }

    private fun currentState(): NetworkState {
        val active = cm?.activeNetwork ?: return NetworkState.Offline
        val caps = cm.getNetworkCapabilities(active)
        return mapToNetworkState(caps)
    }

    private fun recompute() {
        _state.value = currentState()
    }

    companion object {
        fun mapToNetworkState(caps: NetworkCapabilities?): NetworkState {
            if (caps == null) return NetworkState.Offline
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkState.Offline
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.Cellular
                else -> NetworkState.Offline
            }
        }
    }
}
