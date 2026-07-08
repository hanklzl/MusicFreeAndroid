package com.hank.musicfree.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import com.hank.musicfree.data.db.entity.MusicItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Upsert
    suspend fun upsert(item: MusicItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MusicItemEntity>)

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

    @Query(
        """
    SELECT DISTINCT m.* FROM music_items m
    LEFT JOIN downloaded_tracks d
      ON d.id = m.id AND d.platform = m.platform
    WHERE m.platform = :localPlatform OR d.id IS NOT NULL
    ORDER BY m.title ASC, m.platform ASC, m.id ASC
    """
    )
    fun observeLocalLibrary(localPlatform: String): Flow<List<MusicItemEntity>>

    @Query("SELECT * FROM music_items ORDER BY title ASC")
    fun observeAll(): Flow<List<MusicItemEntity>>

    @Query("SELECT COUNT(*) FROM music_items")
    suspend fun count(): Int

    @Query("DELETE FROM music_items WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("DELETE FROM music_items WHERE platform = :platform AND id NOT IN (:ids)")
    suspend fun deleteByPlatformExceptIds(platform: String, ids: List<String>)

    @Transaction
    suspend fun replaceByPlatform(platform: String, items: List<MusicItemEntity>) {
        if (items.isEmpty()) {
            deleteByPlatform(platform)
        } else {
            upsertAll(items)
            deleteByPlatformExceptIds(platform, items.map { it.id })
        }
    }
}
