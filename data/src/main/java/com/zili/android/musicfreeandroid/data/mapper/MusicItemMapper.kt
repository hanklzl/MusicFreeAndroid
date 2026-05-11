package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicItemWithAddedAt
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity

/** Overload that accepts an explicit [Converters] instance. Prefer this when one is already injected. */
fun MusicItem.toEntity(converters: Converters): MusicItemEntity = MusicItemEntity(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualitiesJson = converters.qualitiesToJson(qualities),
    rawJson = converters.rawMapToJson(raw),
)

fun MusicItemEntity.toModel(converters: Converters, addedAt: Long = 0L): MusicItem = MusicItem(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualities = converters.jsonToQualities(qualitiesJson),
    raw = converters.jsonToRawMap(rawJson),
    addedAt = addedAt,
)

fun MusicItemWithAddedAt.toModel(converters: Converters): MusicItem =
    music.toModel(converters = converters, addedAt = addedAt)
