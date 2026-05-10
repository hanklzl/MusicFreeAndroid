package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

fun StarredSheet.toEntity(createdAt: Long, updatedAt: Long, converters: Converters): StarredSheetEntity = StarredSheetEntity(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
    kind = kind,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    rawJson = converters.rawMapToJson(raw),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun StarredSheetEntity.toModel(converters: Converters): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
    kind = kind,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = converters.jsonToRawMap(rawJson),
)
