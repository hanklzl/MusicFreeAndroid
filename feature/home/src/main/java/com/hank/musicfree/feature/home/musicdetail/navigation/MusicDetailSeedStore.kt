package com.hank.musicfree.feature.home.musicdetail.navigation

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStore
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStoreProvider
import com.hank.musicfree.feature.home.runtime.asObjectOrNull
import com.hank.musicfree.feature.home.runtime.longOrNull
import com.hank.musicfree.feature.home.runtime.qualitiesMapOrNull
import com.hank.musicfree.feature.home.runtime.qualitiesToJson
import com.hank.musicfree.feature.home.runtime.rawMap
import com.hank.musicfree.feature.home.runtime.requiredString
import com.hank.musicfree.feature.home.runtime.routeSeedPayload
import com.hank.musicfree.feature.home.runtime.stringOrNull
import java.util.UUID

object MusicDetailSeedStore {
    fun put(item: MusicItem): String {
        val key = RuntimeStoreKey.routeSeed(TARGET, item.platform, item.uniqueRouteSeedId()).value
        RouteSeedRuntimeStoreProvider.current.put(
            key = key,
            payloadJson = item.toPayloadJson(),
            ttlMs = RouteSeedRuntimeStore.DEFAULT_TTL_MS,
        )
        return key
    }

    fun take(token: String?): MusicItem? {
        if (token.isNullOrBlank()) return null
        return RouteSeedRuntimeStoreProvider.current
            .resolve(token)
            ?.payload
            ?.asObjectOrNull()
            ?.toMusicItem()
    }

    internal fun clear() {
        RouteSeedRuntimeStoreProvider.current.clear()
    }

    private fun MusicItem.toPayloadJson(): String = routeSeedPayload(
        "id" to id,
        "platform" to platform,
        "title" to title,
        "artist" to artist,
        "album" to album,
        "duration" to duration,
        "url" to url,
        "artwork" to artwork,
        "qualities" to qualitiesToJson(qualities),
        "raw" to raw,
        "addedAt" to addedAt,
        "localPath" to localPath,
    )

    private fun kotlinx.serialization.json.JsonObject.toMusicItem(): MusicItem =
        MusicItem(
            id = requiredString("id"),
            platform = requiredString("platform"),
            title = requiredString("title"),
            artist = requiredString("artist"),
            album = stringOrNull("album"),
            duration = longOrNull("duration") ?: 0L,
            url = stringOrNull("url"),
            artwork = stringOrNull("artwork"),
            qualities = qualitiesMapOrNull(),
            raw = rawMap(),
            addedAt = longOrNull("addedAt") ?: 0L,
            localPath = stringOrNull("localPath"),
        )

    private const val TARGET = "music"

    private fun MusicItem.uniqueRouteSeedId(): String =
        "$id:${UUID.randomUUID()}"
}
