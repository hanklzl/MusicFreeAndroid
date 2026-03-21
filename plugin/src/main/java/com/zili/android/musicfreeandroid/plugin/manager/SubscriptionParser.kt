package com.zili.android.musicfreeandroid.plugin.manager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class SubscriptionPluginEntry(
    val index: Int,
    val name: String?,
    val url: String,
    val version: String?,
)

internal data class SubscriptionParseResult(
    val totalEntries: Int,
    val installableEntries: List<SubscriptionPluginEntry>,
    val isMalformed: Boolean,
) {
    val isValid: Boolean
        get() = !isMalformed
}

internal object SubscriptionParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): SubscriptionParseResult {
        val root = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrElse {
            return SubscriptionParseResult(
                totalEntries = 0,
                installableEntries = emptyList(),
                isMalformed = true,
            )
        }

        val plugins = root["plugins"] as? JsonArray
            ?: return SubscriptionParseResult(
                totalEntries = 0,
                installableEntries = emptyList(),
                isMalformed = true,
            )

        val installableEntries = plugins.mapIndexedNotNull { index, element ->
            val plugin = element as? JsonObject ?: return@mapIndexedNotNull null
            val url = plugin.string("url")?.trim().orEmpty()
            if (url.isBlank()) {
                return@mapIndexedNotNull null
            }

            SubscriptionPluginEntry(
                index = index,
                name = plugin.string("name"),
                url = url,
                version = plugin.string("version"),
            )
        }

        return SubscriptionParseResult(
            totalEntries = plugins.size,
            installableEntries = installableEntries,
            isMalformed = false,
        )
    }

    private fun JsonObject.string(key: String): String? {
        return (get(key) as? JsonPrimitive)?.jsonPrimitive?.contentOrNull
    }
}
