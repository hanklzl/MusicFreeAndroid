package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.db.entity.PlaylistEntity

fun Playlist.toEntity(createdAt: Long, updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    description = description,
    sortMode = sortMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toModel(
    worksNum: Int = 0,
    legacyCoverResolver: ((String) -> String?)? = null,
): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri?.let { raw -> legacyCoverResolver?.invoke(raw) ?: raw },
    description = description,
    sortMode = parseSortMode(sortMode),
    createdAt = createdAt,
    updatedAt = updatedAt,
    worksNum = worksNum,
)

private fun parseSortMode(name: String): SortMode =
    runCatching { SortMode.valueOf(name) }.getOrDefault(SortMode.Manual)
