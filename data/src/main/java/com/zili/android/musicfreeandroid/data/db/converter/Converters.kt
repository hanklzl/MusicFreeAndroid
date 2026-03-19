package com.zili.android.musicfreeandroid.data.db.converter

import androidx.room.TypeConverter
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
}
