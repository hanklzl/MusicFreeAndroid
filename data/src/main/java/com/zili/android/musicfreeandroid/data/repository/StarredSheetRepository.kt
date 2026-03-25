package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StarredSheetRepository @Inject constructor(
    private val starredSheetDao: StarredSheetDao,
) {

    fun observeAll(): Flow<List<StarredSheet>> =
        starredSheetDao.observeAll().map { entities -> entities.map { it.toModel() } }

    suspend fun upsert(sheet: StarredSheet) {
        val now = System.currentTimeMillis()
        starredSheetDao.upsert(sheet.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun deleteById(id: String) {
        starredSheetDao.deleteById(id)
    }
}
