package com.hank.musicfree.feature.settings.traffic

import com.hank.musicfree.core.network.NetworkType

sealed interface TrafficUiState {
    data object Loading : TrafficUiState
    data class Data(
        val scope: TrafficScope,
        val anchor: String,
        val totalBytes: Long,
        val byNetwork: Map<NetworkType, Long>,
        val bars: List<TrafficBar>,
    ) : TrafficUiState
}

data class TrafficBar(
    val label: String,
    val wifiBytes: Long,
    val cellularBytes: Long,
    val otherBytes: Long,
)
