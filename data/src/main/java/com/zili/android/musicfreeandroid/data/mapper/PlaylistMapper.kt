package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity

fun Playlist.toEntity(createdAt: Long, updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toModel(): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri,
)
