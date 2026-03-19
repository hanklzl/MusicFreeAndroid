package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity

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
)
