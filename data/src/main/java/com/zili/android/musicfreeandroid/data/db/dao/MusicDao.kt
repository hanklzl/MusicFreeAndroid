package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Upsert
    suspend fun upsert(item: MusicItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MusicItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MusicItemEntity>)

    @Update
    suspend fun update(item: MusicItemEntity)

    @Delete
    suspend fun delete(item: MusicItemEntity)

    @Query("SELECT * FROM music_items WHERE id = :id AND platform = :platform")
    suspend fun getById(id: String, platform: String): MusicItemEntity?

    @Query("SELECT * FROM music_items WHERE platform = :platform ORDER BY title ASC")
    fun observeByPlatform(platform: String): Flow<List<MusicItemEntity>>

    @Query("SELECT * FROM music_items ORDER BY title ASC")
    fun observeAll(): Flow<List<MusicItemEntity>>

    @Query("SELECT COUNT(*) FROM music_items")
    suspend fun count(): Int

    @Query("DELETE FROM music_items WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)
}
