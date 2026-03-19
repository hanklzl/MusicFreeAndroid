package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.mapper.toMusicItem
import com.zili.android.musicfreeandroid.data.mapper.toPlayQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayQueueRepository @Inject constructor(
    private val playQueueDao: PlayQueueDao,
    private val converters: Converters,
) {

    fun observeQueue(): Flow<List<MusicItem>> =
        playQueueDao.observeAll().map { entities ->
            entities.map { it.toMusicItem(converters) }
        }

    suspend fun getQueue(): List<MusicItem> =
        playQueueDao.getAll().map { it.toMusicItem(converters) }

    suspend fun saveQueue(items: List<MusicItem>) {
        val entities = items.mapIndexed { index, item ->
            item.toPlayQueueEntity(sortOrder = index, converters = converters)
        }
        playQueueDao.replaceAll(entities)
    }

    suspend fun clearQueue() = playQueueDao.clearAll()

    suspend fun count(): Int = playQueueDao.count()
}
