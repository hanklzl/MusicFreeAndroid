package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hank.musicfree.data.db.entity.PluginMetadataCacheEntity

@Dao
interface PluginMetadataCacheDao {
    @Query("SELECT * FROM plugin_metadata_cache")
    suspend fun getAll(): List<PluginMetadataCacheEntity>

    @Query("SELECT * FROM plugin_metadata_cache WHERE filePath = :filePath")
    suspend fun getByPath(filePath: String): PluginMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PluginMetadataCacheEntity)

    @Query("DELETE FROM plugin_metadata_cache WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM plugin_metadata_cache")
    suspend fun deleteAll()
}
