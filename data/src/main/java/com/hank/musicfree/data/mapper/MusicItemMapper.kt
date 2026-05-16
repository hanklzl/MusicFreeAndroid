package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.MusicItemWithAddedAt
import com.hank.musicfree.data.db.entity.MusicItemEntity

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
    localPath = localPath,
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
    localPath = localPath,
    qualities = converters.jsonToQualities(qualitiesJson),
    raw = converters.jsonToRawMap(rawJson),
    addedAt = addedAt,
)

fun MusicItemWithAddedAt.toModel(converters: Converters): MusicItem =
    music.toModel(converters = converters, addedAt = addedAt)
