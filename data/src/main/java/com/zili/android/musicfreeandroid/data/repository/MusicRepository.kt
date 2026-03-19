package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val converters: Converters,
) {

    fun observeAll(): Flow<List<MusicItem>> =
        musicDao.observeAll().map { entities -> entities.map { it.toModel(converters) } }

    fun observeByPlatform(platform: String): Flow<List<MusicItem>> =
        musicDao.observeByPlatform(platform).map { entities -> entities.map { it.toModel(converters) } }

    suspend fun getById(id: String, platform: String): MusicItem? =
        musicDao.getById(id, platform)?.toModel(converters)

    suspend fun insert(item: MusicItem) =
        musicDao.insert(item.toEntity(converters))

    suspend fun insertAll(items: List<MusicItem>) =
        musicDao.insertAll(items.map { it.toEntity(converters) })

    suspend fun update(item: MusicItem) =
        musicDao.update(item.toEntity(converters))

    suspend fun delete(item: MusicItem) =
        musicDao.delete(item.toEntity(converters))

    suspend fun deleteByPlatform(platform: String) =
        musicDao.deleteByPlatform(platform)

    suspend fun count(): Int = musicDao.count()
}
