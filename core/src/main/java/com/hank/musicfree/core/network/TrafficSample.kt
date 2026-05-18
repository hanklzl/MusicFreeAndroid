package com.hank.musicfree.core.network

import java.time.LocalDate

data class TrafficSample(
    val localDate: LocalDate,
    val networkType: NetworkType,
    val bytesReceived: Long,
    val bytesSent: Long,
    val timestampMs: Long,
)
