package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrafficDailyDao {

    @Transaction
    open suspend fun upsertAllAccumulating(rows: List<TrafficDailyEntity>) {
        rows.forEach { r ->
            val n = upsertAccumulate(r.localDate, r.networkType, r.bytesReceived, r.bytesSent, r.updatedAt)
            if (n == 0) insertIgnore(r)
        }
    }

    @Query(
        """
        UPDATE traffic_daily
        SET bytes_received = bytes_received + :rx,
            bytes_sent = bytes_sent + :tx,
            updated_at = :now
        WHERE local_date = :date AND network_type = :type
        """,
    )
    abstract suspend fun upsertAccumulate(date: String, type: String, rx: Long, tx: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(row: TrafficDailyEntity)

    @Query("SELECT * FROM traffic_daily WHERE local_date BETWEEN :startDate AND :endDate ORDER BY local_date ASC")
    abstract fun observeRange(startDate: String, endDate: String): Flow<List<TrafficDailyEntity>>

    @Query(
        """
        SELECT substr(local_date, 1, 7) AS yearMonth,
               network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        WHERE local_date >= :startInclusive AND local_date < :endExclusive
        GROUP BY yearMonth, network_type
        ORDER BY yearMonth ASC
        """,
    )
    abstract fun observeMonthlyRange(startInclusive: String, endExclusive: String): Flow<List<TrafficMonthlyRow>>

    @Query(
        """
        SELECT network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        GROUP BY network_type
        """,
    )
    abstract fun observeTotalsByNetwork(): Flow<List<TrafficTotalRow>>

    @Query("SELECT MIN(local_date) FROM traffic_daily")
    abstract fun observeFirstRecordDate(): Flow<String?>

    @Query("DELETE FROM traffic_daily")
    abstract suspend fun clearAll()
}
