package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem

object DownloadFilenames {

    private val ESCAPE_RE = Regex("""[\\/:*?"<>|@]""")
    private val SUPPORTED = setOf("mp3", "flac", "wma", "m4a", "aac", "ogg", "wav", "ape")
    private val EXT_RE = Regex("""\.([A-Za-z0-9]{2,5})(?:[?#].*)?$""")

    fun escape(s: String): String = s.replace(ESCAPE_RE, "_")

    fun displayName(item: MusicItem, ext: String): String {
        val base = "${escape(item.platform)}@${escape(item.id)}@${escape(item.title)}@${escape(item.artist)}"
            .take(200)
        return "$base.$ext"
    }

    fun extensionFromUrl(url: String): String {
        val m = EXT_RE.find(url) ?: return "mp3"
        val ext = m.groupValues[1].lowercase()
        return if (ext in SUPPORTED) ext else "mp3"
    }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "ape" -> "audio/x-ape"
        else -> "audio/mpeg"
    }
}
