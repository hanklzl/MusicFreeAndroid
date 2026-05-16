package com.hank.musicfree.plugin.manager

import java.security.MessageDigest

internal object SubscriptionFileNames {

    fun pluginFileName(entry: SubscriptionPluginEntry): String {
        val rawName = entry.url
            .substringBefore("#")
            .substringBefore("?")
            .substringAfterLast("/")
            .ifBlank { entry.name.orEmpty() }
            .ifBlank { "plugin" }

        val normalizedBaseName = normalizeBaseName(rawName)
        val hash = sha256(entry.url).take(10)
        return "%03d-%s-%s.js".format(entry.index + 1, normalizedBaseName, hash)
    }

    fun networkPluginFileName(url: String): String {
        val rawName = url
            .substringBefore("#")
            .substringBefore("?")
            .substringAfterLast("/")
            .ifBlank { "plugin" }

        val normalizedBaseName = normalizeBaseName(rawName)
        val hash = sha256(url).take(10)
        return "$normalizedBaseName-$hash.js"
    }

    private fun normalizeBaseName(rawName: String): String =
        rawName
            .removeSuffix(".js")
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-')
            .ifBlank { "plugin" }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
