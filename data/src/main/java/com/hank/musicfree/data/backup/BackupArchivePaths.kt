package com.hank.musicfree.data.backup

import java.io.InputStream
import java.security.MessageDigest

object BackupArchivePaths {
    const val MANIFEST = "manifest.json"
    const val DB = "db/musicfree.db"
    const val DB_WAL = "db/musicfree.db-wal"
    const val DB_SHM = "db/musicfree.db-shm"
    const val DATASTORE = "datastore/app_preferences.preferences_pb"
    const val PLUGINS_PREFIX = "files/plugins/"
    const val PLAYLIST_COVERS_PREFIX = "files/playlist_covers/"
    const val THEME_BACKGROUND_PREFIX = "files/theme_background."

    val requiredEntries: Set<String> = setOf(MANIFEST, DB)

    fun isAllowedEntry(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains("\\")) return false
        val parts = path.split('/')
        if (parts.any { it == ".." || it == "." || it.isBlank() }) return false

        return path == MANIFEST ||
            path == DB ||
            path == DB_WAL ||
            path == DB_SHM ||
            path == DATASTORE ||
            path.isDirectChildOf(PLUGINS_PREFIX, suffix = ".js") ||
            path.startsWith(PLAYLIST_COVERS_PREFIX) ||
            path.isDirectChildOf(THEME_BACKGROUND_PREFIX)
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var consecutiveZeroReads = 0
        val maxConsecutiveZeroReads = 16

        while (true) {
            val read = input.read(buffer)
            when {
                read > 0 -> {
                    consecutiveZeroReads = 0
                    digest.update(buffer, 0, read)
                }

                read == -1 -> break
                else -> {
                    consecutiveZeroReads++
                    if (consecutiveZeroReads >= maxConsecutiveZeroReads) {
                        throw IllegalStateException(
                            "InputStream returned 0 for $consecutiveZeroReads consecutive reads",
                        )
                    }
                    Thread.yield()
                }
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun String.isDirectChildOf(prefix: String, suffix: String): Boolean {
        if (!startsWith(prefix) || !endsWith(suffix)) return false
        val child = removePrefix(prefix)
        return child.length > suffix.length && '/' !in child
    }

    private fun String.isDirectChildOf(prefix: String): Boolean {
        if (!startsWith(prefix)) return false
        val child = removePrefix(prefix)
        return child.isNotBlank() && '/' !in child
    }
}
