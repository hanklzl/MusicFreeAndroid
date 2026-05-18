package com.hank.musicfree.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "traffic_daily",
    primaryKeys = ["local_date", "network_type"],
)
data class TrafficDailyEntity(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "network_type") val networkType: String,
    @ColumnInfo(name = "bytes_received") val bytesReceived: Long,
    @ColumnInfo(name = "bytes_sent") val bytesSent: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
