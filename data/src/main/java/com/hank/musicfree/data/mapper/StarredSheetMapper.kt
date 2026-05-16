package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.StarredSheetEntity

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
