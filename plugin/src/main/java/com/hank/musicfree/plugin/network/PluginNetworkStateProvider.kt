package com.hank.musicfree.plugin.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PluginNetworkStateProvider {
    fun isOffline(): Boolean

    object AlwaysOnline : PluginNetworkStateProvider {
        override fun isOffline(): Boolean = false
    }
}

class AndroidPluginNetworkStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PluginNetworkStateProvider {
    override fun isOffline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
