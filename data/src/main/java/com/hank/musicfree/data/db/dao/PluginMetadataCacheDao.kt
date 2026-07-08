package com.hank.musicfree.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
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
