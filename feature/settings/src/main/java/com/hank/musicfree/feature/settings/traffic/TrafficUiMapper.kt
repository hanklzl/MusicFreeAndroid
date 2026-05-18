package com.hank.musicfree.feature.settings.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.traffic.TrafficRangeSummary

fun TrafficRangeSummary.toUi(scope: TrafficScope, anchorLabel: String): TrafficUiState.Data =
    TrafficUiState.Data(
        scope = scope,
        anchor = anchorLabel,
        totalBytes = totalBytes,
        byNetwork = byNetwork,
        bars = buckets.map { b ->
            TrafficBar(
                label = b.label,
                wifiBytes = b.byNetwork[NetworkType.WIFI] ?: 0L,
                cellularBytes = b.byNetwork[NetworkType.CELLULAR] ?: 0L,
                otherBytes = b.byNetwork[NetworkType.OTHER] ?: 0L,
            )
        },
    )
