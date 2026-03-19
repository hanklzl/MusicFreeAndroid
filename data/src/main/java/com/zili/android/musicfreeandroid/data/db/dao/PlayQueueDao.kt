package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlayQueueEntity>)

    @Query("SELECT * FROM play_queue ORDER BY sortOrder ASC")
    suspend fun getAll(): List<PlayQueueEntity>

    @Query("SELECT * FROM play_queue ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<PlayQueueEntity>>

    @Query("DELETE FROM play_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM play_queue")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(items: List<PlayQueueEntity>) {
        clearAll()
        insertAll(items)
    }
}
