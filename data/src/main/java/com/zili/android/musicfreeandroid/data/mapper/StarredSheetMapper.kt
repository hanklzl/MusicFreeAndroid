package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

fun StarredSheet.toEntity(createdAt: Long, updatedAt: Long): StarredSheetEntity = StarredSheetEntity(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun StarredSheetEntity.toModel(): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
)
