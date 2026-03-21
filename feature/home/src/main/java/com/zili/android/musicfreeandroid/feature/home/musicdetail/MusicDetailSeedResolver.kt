package com.zili.android.musicfreeandroid.feature.home.musicdetail

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.MusicDetailSeedStore
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase

internal object MusicDetailSeedResolver {
    fun baseMusicItem(route: MusicDetailRoute): MusicItem {
        return MusicDetailSeedStore.take(route.seedToken) ?: MusicItem(
            id = route.musicId,
            platform = route.pluginPlatform,
            title = route.title,
            artist = route.artist,
            album = route.album,
            duration = route.durationMs,
            url = null,
            artwork = route.artwork,
            qualities = null,
        )
    }

    fun albumPreviewSeed(musicItem: MusicItem): AlbumItemBase? {
        val albumTitle = musicItem.album?.takeIf { it.isNotBlank() } ?: return null
        val raw = nestedRawMap(musicItem.raw, "albumItem") ?: musicItem.raw.toMutableMap()
        val albumId = previewId(raw, "id", "albumId", "albumID", "albumMid", "albumMID") ?: albumTitle
        raw["id"] = albumId
        raw["platform"] = musicItem.platform
        raw["title"] = raw["title"]?.toString()?.takeIf { it.isNotBlank() } ?: albumTitle
        raw["artist"] = raw["artist"]?.toString()?.takeIf { it.isNotBlank() } ?: musicItem.artist
        raw["artwork"] = raw["artwork"]?.toString() ?: musicItem.artwork

        return AlbumItemBase(
            id = albumId,
            platform = musicItem.platform,
            title = raw["title"]?.toString(),
            date = raw["date"]?.toString(),
            artist = raw["artist"]?.toString(),
            description = raw["description"]?.toString(),
            artwork = raw["artwork"]?.toString(),
            worksNum = (raw["worksNum"] as? Number)?.toInt(),
            raw = raw,
        )
    }

    fun artistPreviewSeed(musicItem: MusicItem): ArtistItemBase? {
        val artistName = musicItem.artist.takeIf { it.isNotBlank() } ?: return null
        val raw = nestedRawMap(musicItem.raw, "artistItem") ?: musicItem.raw.toMutableMap()
        val artistId = previewId(raw, "id", "artistId", "artistID", "artistMid", "artistMID") ?: artistName
        raw["id"] = artistId
        raw["platform"] = musicItem.platform
        raw["name"] = raw["name"]?.toString()?.takeIf { it.isNotBlank() } ?: artistName
        raw["avatar"] = raw["avatar"]?.toString() ?: musicItem.artwork

        return ArtistItemBase(
            id = artistId,
            platform = musicItem.platform,
            name = raw["name"]?.toString(),
            avatar = raw["avatar"]?.toString(),
            fans = (raw["fans"] as? Number)?.toInt(),
            description = raw["description"]?.toString(),
            worksNum = (raw["worksNum"] as? Number)?.toInt(),
            raw = raw,
        )
    }

    private fun nestedRawMap(
        raw: Map<String, Any?>,
        key: String,
    ): MutableMap<String, Any?>? {
        val nested = raw[key] as? Map<*, *> ?: return null
        return nested.entries.associate { (nestedKey, nestedValue) ->
            nestedKey.toString() to nestedValue
        }.toMutableMap()
    }

    private fun previewId(
        raw: Map<String, Any?>,
        vararg keys: String,
    ): String? {
        return keys.firstNotNullOfOrNull { key ->
            raw[key]?.toString()?.takeIf { it.isNotBlank() }
        }
    }
}
