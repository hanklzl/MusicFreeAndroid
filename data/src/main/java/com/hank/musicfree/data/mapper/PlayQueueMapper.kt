package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.PlayQueueEntity

fun MusicItem.toPlayQueueEntity(sortOrder: Int, converters: Converters): PlayQueueEntity =
    PlayQueueEntity(
        musicId = id,
        musicPlatform = platform,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        url = url,
        artwork = artwork,
        qualitiesJson = converters.qualitiesToJson(qualities),
        rawJson = converters.rawMapToJson(raw),
        localPath = localPath,
        addedAt = addedAt,
        sortOrder = sortOrder,
    )

fun PlayQueueEntity.toMusicItem(converters: Converters): MusicItem = MusicItem(
    id = musicId,
    platform = musicPlatform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualities = converters.jsonToQualities(qualitiesJson),
    raw = converters.jsonToRawMap(rawJson),
    addedAt = addedAt,
    localPath = localPath,
)
