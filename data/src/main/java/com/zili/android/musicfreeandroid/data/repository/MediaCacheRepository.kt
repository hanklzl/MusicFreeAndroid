package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class CachedSource(
    val url: String,
    val headers: Map<String, String>?,
    val userAgent: String?,
)

@Singleton
class MediaCacheRepository @Inject constructor(
    private val dao: MediaCacheDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun get(item: MusicItem, quality: PlayQuality): CachedSource? {
        val row = dao.get(item.platform, item.id) ?: return null
        return runCatching {
            val obj = JSONObject(row.sourcesJson)
            if (!obj.has(quality.name)) return null
            val q = obj.getJSONObject(quality.name)
            CachedSource(
                url = q.optString("url").takeIf { it.isNotEmpty() } ?: return null,
                headers = q.optJSONObject("headers")?.toStringMap(),
                userAgent = q.optString("userAgent").takeIf { it.isNotEmpty() },
            )
        }.getOrNull()
    }

    suspend fun put(item: MusicItem, quality: PlayQuality, source: MediaSourceResult) {
        val existing = dao.get(item.platform, item.id)
        val json = if (existing != null) JSONObject(existing.sourcesJson) else JSONObject()
        val q = JSONObject().apply {
            put("url", source.url)
            source.headers?.let { put("headers", JSONObject(it as Map<*, *>)) }
            source.userAgent?.let { put("userAgent", it) }
        }
        json.put(quality.name, q)
        dao.upsert(MediaCacheEntity(item.platform, item.id, json.toString(), now()))
        if (dao.count() >= LIMIT) dao.deleteOldest(LIMIT / 2)
    }

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { getString(it) }

    companion object {
        const val LIMIT = 800
    }
}
