package com.hank.musicfree.feature.home.artistdetail.navigation

import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStore
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStoreProvider
import com.hank.musicfree.feature.home.runtime.asObjectOrNull
import com.hank.musicfree.feature.home.runtime.intOrNull
import com.hank.musicfree.feature.home.runtime.rawMap
import com.hank.musicfree.feature.home.runtime.requiredString
import com.hank.musicfree.feature.home.runtime.routeSeedPayload
import com.hank.musicfree.feature.home.runtime.stringOrNull
import com.hank.musicfree.plugin.api.ArtistItemBase
import java.util.UUID

object ArtistDetailSeedStore {
    fun put(item: ArtistItemBase): String {
        val key = RuntimeStoreKey.routeSeed(TARGET, item.platform, item.uniqueRouteSeedId()).value
        RouteSeedRuntimeStoreProvider.current.put(
            key = key,
            payloadJson = item.toPayloadJson(),
            ttlMs = RouteSeedRuntimeStore.DEFAULT_TTL_MS,
        )
        return key
    }

    fun take(token: String?): ArtistItemBase? {
        if (token.isNullOrBlank()) return null
        return RouteSeedRuntimeStoreProvider.current
            .resolve(token)
            ?.payload
            ?.asObjectOrNull()
            ?.toArtistItemBase()
    }

    internal fun clear() {
        RouteSeedRuntimeStoreProvider.current.clear()
    }

    private fun ArtistItemBase.toPayloadJson(): String = routeSeedPayload(
        "id" to id,
        "platform" to platform,
        "name" to name,
        "avatar" to avatar,
        "fans" to fans,
        "description" to description,
        "worksNum" to worksNum,
        "raw" to raw,
    )

    private fun kotlinx.serialization.json.JsonObject.toArtistItemBase(): ArtistItemBase =
        ArtistItemBase(
            id = requiredString("id"),
            platform = requiredString("platform"),
            name = stringOrNull("name"),
            avatar = stringOrNull("avatar"),
            fans = intOrNull("fans"),
            description = stringOrNull("description"),
            worksNum = intOrNull("worksNum"),
            raw = rawMap(),
        )

    private const val TARGET = "artist"

    private fun ArtistItemBase.uniqueRouteSeedId(): String =
        "$id:${UUID.randomUUID()}"
}
