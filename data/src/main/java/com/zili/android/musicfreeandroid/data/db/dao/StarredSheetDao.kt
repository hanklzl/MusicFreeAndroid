package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StarredSheetDao {

    @Query("SELECT * FROM starred_sheets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<StarredSheetEntity>>

    @Upsert
    suspend fun upsert(entity: StarredSheetEntity)

    @Query("SELECT * FROM starred_sheets WHERE id = :id AND platform = :platform LIMIT 1")
    suspend fun getByIdAndPlatform(id: String, platform: String): StarredSheetEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM starred_sheets WHERE id = :id AND platform = :platform)")
    fun observeExists(id: String, platform: String): Flow<Boolean>

    @Query("DELETE FROM starred_sheets WHERE id = :id AND platform = :platform")
    suspend fun deleteByIdAndPlatform(id: String, platform: String)
}
