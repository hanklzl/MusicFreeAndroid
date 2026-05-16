package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hank.musicfree.data.db.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id AND platform = :platform")
    suspend fun findByKey(id: String, platform: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun findNextPending(): DownloadTaskEntity?

    @Query("UPDATE download_tasks SET status = :status, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun updateStatus(id: String, platform: String, status: String, now: Long)

    @Query("UPDATE download_tasks SET status = :status, errorReason = :reason, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun markFailed(id: String, platform: String, status: String = "FAILED", reason: String, now: Long)

    @Query("UPDATE download_tasks SET resolvedUrl = :url, resolvedHeadersJson = :headers, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun setResolved(id: String, platform: String, url: String?, headers: String?, now: Long)

    @Query("UPDATE download_tasks SET fileSize = :fileSize, downloadedSize = :downloaded, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun updateProgress(id: String, platform: String, fileSize: Long?, downloaded: Long?, now: Long)

    @Query("UPDATE download_tasks SET status = 'PENDING', resolvedUrl = NULL, resolvedHeadersJson = NULL, errorReason = NULL WHERE status IN ('PREPARING','DOWNLOADING')")
    suspend fun resetInflightToPending()

    @Query("UPDATE download_tasks SET status = 'PENDING', errorReason = NULL WHERE status = 'FAILED'")
    suspend fun resetAllFailedToPending()

    @Query("DELETE FROM download_tasks WHERE id = :id AND platform = :platform")
    suspend fun deleteByKey(id: String, platform: String)

    @Query("DELETE FROM download_tasks WHERE status = 'FAILED'")
    suspend fun deleteAllFailed()

    @Query("DELETE FROM download_tasks WHERE status IN ('PENDING','PREPARING','DOWNLOADING')")
    suspend fun deleteAllInflight()
}
