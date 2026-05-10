package com.zili.android.musicfreeandroid.player.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class PlaybackNetworkState(
    val isCellular: Boolean,
)

interface PlaybackNetworkStateProvider {
    fun currentState(): PlaybackNetworkState

    object AlwaysAllowed : PlaybackNetworkStateProvider {
        override fun currentState(): PlaybackNetworkState =
            PlaybackNetworkState(isCellular = false)
    }
}

class AndroidPlaybackNetworkStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackNetworkStateProvider {
    override fun currentState(): PlaybackNetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return PlaybackNetworkState(isCellular = false)
        val network = connectivityManager.activeNetwork
            ?: return PlaybackNetworkState(isCellular = false)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return PlaybackNetworkState(isCellular = false)
        return PlaybackNetworkState(
            isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        )
    }
}
