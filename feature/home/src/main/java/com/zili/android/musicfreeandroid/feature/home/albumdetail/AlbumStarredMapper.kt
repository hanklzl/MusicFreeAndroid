package com.zili.android.musicfreeandroid.feature.home.albumdetail

import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase

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
