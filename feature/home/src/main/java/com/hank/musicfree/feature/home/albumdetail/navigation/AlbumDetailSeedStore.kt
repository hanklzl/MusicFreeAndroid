package com.hank.musicfree.feature.home.albumdetail.navigation

import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStore
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStoreProvider
import com.hank.musicfree.feature.home.runtime.asObjectOrNull
import com.hank.musicfree.feature.home.runtime.intOrNull
import com.hank.musicfree.feature.home.runtime.rawMap
import com.hank.musicfree.feature.home.runtime.requiredString
import com.hank.musicfree.feature.home.runtime.routeSeedPayload
import com.hank.musicfree.feature.home.runtime.stringOrNull
import com.hank.musicfree.plugin.api.AlbumItemBase
import java.util.UUID

object AlbumDetailSeedStore {
    fun put(item: AlbumItemBase): String {
        val key = RuntimeStoreKey.routeSeed(TARGET, item.platform, item.uniqueRouteSeedId()).value
        RouteSeedRuntimeStoreProvider.current.put(
            key = key,
            payloadJson = item.toPayloadJson(),
            ttlMs = RouteSeedRuntimeStore.DEFAULT_TTL_MS,
        )
        return key
    }

    fun take(token: String?): AlbumItemBase? {
        if (token.isNullOrBlank()) return null
        return RouteSeedRuntimeStoreProvider.current
            .resolve(token)
            ?.payload
            ?.asObjectOrNull()
            ?.toAlbumItemBase()
    }

    internal fun clear() {
        RouteSeedRuntimeStoreProvider.current.clear()
    }

    private fun AlbumItemBase.toPayloadJson(): String = routeSeedPayload(
        "id" to id,
        "platform" to platform,
        "title" to title,
        "date" to date,
        "artist" to artist,
        "description" to description,
        "artwork" to artwork,
        "worksNum" to worksNum,
        "raw" to raw,
    )

    private fun kotlinx.serialization.json.JsonObject.toAlbumItemBase(): AlbumItemBase =
        AlbumItemBase(
            id = requiredString("id"),
            platform = requiredString("platform"),
            title = stringOrNull("title"),
            date = stringOrNull("date"),
            artist = stringOrNull("artist"),
            description = stringOrNull("description"),
            artwork = stringOrNull("artwork"),
            worksNum = intOrNull("worksNum"),
            raw = rawMap(),
        )

    private const val TARGET = "album"

    private fun AlbumItemBase.uniqueRouteSeedId(): String =
        "$id:${UUID.randomUUID()}"
}
