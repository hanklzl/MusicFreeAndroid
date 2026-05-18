package com.hank.musicfree.feature.home.pluginsheet.navigation

import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStore
import com.hank.musicfree.feature.home.runtime.RouteSeedRuntimeStoreProvider
import com.hank.musicfree.feature.home.runtime.asObjectOrNull
import com.hank.musicfree.feature.home.runtime.intOrNull
import com.hank.musicfree.feature.home.runtime.rawMap
import com.hank.musicfree.feature.home.runtime.requiredString
import com.hank.musicfree.feature.home.runtime.routeSeedPayload
import com.hank.musicfree.feature.home.runtime.stringOrNull
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import java.util.UUID

object PluginSheetSeedStore {
    fun put(item: MusicSheetItemBase): String {
        val key = RuntimeStoreKey.routeSeed(TARGET, item.platform, item.uniqueRouteSeedId()).value
        RouteSeedRuntimeStoreProvider.current.put(
            key = key,
            payloadJson = item.toPayloadJson(),
            ttlMs = RouteSeedRuntimeStore.DEFAULT_TTL_MS,
        )
        return key
    }

    fun take(token: String?): MusicSheetItemBase? {
        if (token.isNullOrBlank()) return null
        return RouteSeedRuntimeStoreProvider.current
            .resolve(token)
            ?.payload
            ?.asObjectOrNull()
            ?.toMusicSheetItemBase()
    }

    internal fun clear() {
        RouteSeedRuntimeStoreProvider.current.clear()
    }

    private fun MusicSheetItemBase.toPayloadJson(): String = routeSeedPayload(
        "id" to id,
        "platform" to platform,
        "title" to title,
        "artist" to artist,
        "description" to description,
        "coverImg" to coverImg,
        "artwork" to artwork,
        "worksNum" to worksNum,
        "raw" to raw,
    )

    private fun kotlinx.serialization.json.JsonObject.toMusicSheetItemBase(): MusicSheetItemBase =
        MusicSheetItemBase(
            id = requiredString("id"),
            platform = requiredString("platform"),
            title = stringOrNull("title"),
            artist = stringOrNull("artist"),
            description = stringOrNull("description"),
            coverImg = stringOrNull("coverImg"),
            artwork = stringOrNull("artwork"),
            worksNum = intOrNull("worksNum"),
            raw = rawMap(),
        )

    private const val TARGET = "plugin_sheet"

    private fun MusicSheetItemBase.uniqueRouteSeedId(): String =
        "$id:${UUID.randomUUID()}"
}
