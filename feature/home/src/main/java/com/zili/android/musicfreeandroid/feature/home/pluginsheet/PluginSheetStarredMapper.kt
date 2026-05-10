package com.zili.android.musicfreeandroid.feature.home.pluginsheet

import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

internal fun MusicSheetItemBase.toStarredSheet(): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title ?: "歌单",
    artist = artist,
    coverUri = coverImg,
    sourceUrl = raw["sourceUrl"] as? String,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = raw,
)

internal fun StarredSheet.toMusicSheetItemBase(): MusicSheetItemBase {
    val mergedRaw = raw.toMutableMap()
    sourceUrl?.let { mergedRaw.putIfAbsent("sourceUrl", it) }
    return MusicSheetItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverUri,
        artwork = artwork,
        worksNum = worksNum,
        raw = mergedRaw,
    )
}
