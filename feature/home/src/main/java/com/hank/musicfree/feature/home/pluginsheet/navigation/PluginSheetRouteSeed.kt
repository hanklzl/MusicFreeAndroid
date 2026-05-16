package com.hank.musicfree.feature.home.pluginsheet.navigation

import com.hank.musicfree.core.navigation.PluginSheetDetailRoute
import com.hank.musicfree.core.navigation.TopListDetailRoute
import com.hank.musicfree.plugin.api.MusicSheetItemBase

fun PluginSheetDetailRoute.fallbackSheetSeed(): MusicSheetItemBase = MusicSheetItemBase(
    id = sheetId,
    platform = pluginPlatform,
    title = title,
    artist = artist,
    description = description,
    coverImg = coverImg,
    artwork = artwork,
    worksNum = worksNum,
    raw = routeRaw(
        id = sheetId,
        platform = pluginPlatform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverImg,
        artwork = artwork,
        worksNum = worksNum,
    ),
)

fun TopListDetailRoute.fallbackTopListSeed(): MusicSheetItemBase = MusicSheetItemBase(
    id = topListId,
    platform = pluginPlatform,
    title = title,
    artist = artist,
    description = description,
    coverImg = coverImg,
    artwork = artwork,
    worksNum = worksNum,
    raw = routeRaw(
        id = topListId,
        platform = pluginPlatform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverImg,
        artwork = artwork,
        worksNum = worksNum,
    ),
)

private fun routeRaw(
    id: String,
    platform: String,
    title: String?,
    artist: String?,
    description: String?,
    coverImg: String?,
    artwork: String?,
    worksNum: Int?,
): Map<String, Any?> = buildMap {
    put("id", id)
    put("platform", platform)
    title?.let { put("title", it) }
    artist?.let { put("artist", it) }
    description?.let { put("description", it) }
    coverImg?.let { put("coverImg", it) }
    artwork?.let { put("artwork", it) }
    worksNum?.let { put("worksNum", it) }
}
