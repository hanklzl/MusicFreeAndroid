package com.hank.musicfree.feature.home.albumdetail

import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.plugin.api.AlbumItemBase

internal fun AlbumItemBase.toStarredSheet(): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title ?: "专辑",
    artist = artist,
    coverUri = artwork,
    sourceUrl = raw["sourceUrl"] as? String,
    kind = StarredKind.ALBUM,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = raw,
)
