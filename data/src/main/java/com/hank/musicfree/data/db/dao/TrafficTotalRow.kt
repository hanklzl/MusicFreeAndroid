package com.hank.musicfree.data.db.dao

data class TrafficTotalRow(
    val networkType: String,
    val bytesReceived: Long,
    val bytesSent: Long,
)
