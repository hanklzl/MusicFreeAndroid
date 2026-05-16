package com.hank.musicfree.downloader.engine

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.downloader.model.MediaKey

class FakeQualityResolver {
    private val table = mutableMapOf<MediaKey, MediaSourceResult?>()
    private val qualityTable = mutableMapOf<Pair<MediaKey, String>, MediaSourceResult?>()
    val callOrder = mutableListOf<String>()

    fun bind(key: MediaKey, result: MediaSourceResult?) { table[key] = result }
    fun bind(key: MediaKey, qualityWire: String, result: MediaSourceResult?) {
        qualityTable[key to qualityWire] = result
    }

    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? =
        MediaKey.of(item).let { key ->
            callOrder += qualityWire
            if (qualityTable.containsKey(key to qualityWire)) {
                qualityTable[key to qualityWire]
            } else {
                table[key]
            }
        }
}
