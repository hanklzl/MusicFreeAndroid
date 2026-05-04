package com.zili.android.musicfreeandroid.data.db.converter

import androidx.room.TypeConverter
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun qualitiesToJson(qualities: Map<PlayQuality, QualityInfo>?): String? {
        if (qualities == null) return null
        val json = JSONObject()
        qualities.forEach { (quality, info) ->
            val obj = JSONObject()
            obj.put("url", info.url ?: JSONObject.NULL)
            obj.put("size", info.size ?: JSONObject.NULL)
            json.put(quality.name, obj)
        }
        return json.toString()
    }

    @TypeConverter
    fun jsonToQualities(json: String?): Map<PlayQuality, QualityInfo>? {
        if (json == null) return null
        val obj = JSONObject(json)
        val map = mutableMapOf<PlayQuality, QualityInfo>()
        obj.keys().forEach { key ->
            val quality = PlayQuality.valueOf(key)
            val info = obj.getJSONObject(key)
            map[quality] = QualityInfo(
                url = if (info.isNull("url")) null else info.getString("url"),
                size = if (info.isNull("size")) null else info.getLong("size"),
            )
        }
        return map
    }

    fun musicItemToJson(item: MusicItem?): String? {
        if (item == null) return null
        val json = JSONObject()
        json.put("id", item.id)
        json.put("platform", item.platform)
        json.put("title", item.title)
        json.put("artist", item.artist)
        json.put("album", item.album ?: JSONObject.NULL)
        json.put("duration", item.duration)
        json.put("url", item.url ?: JSONObject.NULL)
        json.put("artwork", item.artwork ?: JSONObject.NULL)
        json.put("qualities", qualitiesToJson(item.qualities) ?: JSONObject.NULL)
        return json.toString()
    }

    fun jsonToMusicItem(json: String?): MusicItem? {
        if (json.isNullOrBlank()) return null
        val obj = JSONObject(json)
        return MusicItem(
            id = obj.getString("id"),
            platform = obj.getString("platform"),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = if (obj.isNull("album")) null else obj.getString("album"),
            duration = obj.optLong("duration", 0L),
            url = if (obj.isNull("url")) null else obj.getString("url"),
            artwork = if (obj.isNull("artwork")) null else obj.getString("artwork"),
            qualities = if (obj.isNull("qualities")) null else jsonToQualities(obj.getString("qualities")),
        )
    }
}
