package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity

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
)

fun MusicItemEntity.toModel(converters: Converters): MusicItem = MusicItem(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualities = converters.jsonToQualities(qualitiesJson),
)
