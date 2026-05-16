package com.hank.musicfree.plugin.local

import com.hank.musicfree.core.local.Mp3MetadataReader
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal "virtual plugin" providing PluginApi semantics for local audio files
 * (platform = "本地"). This mirrors the RN MusicFree localFilePlugin behaviour:
 * getMusicInfo / getLyric / importMusicItem / getMediaSource → file://.
 *
 * NOT a [com.hank.musicfree.plugin.manager.LoadedPlugin]; the
 * adaptation into LoadedPlugin happens in Phase B-2 via a sealed interface
 * refactor of LoadedPlugin (PluginManager will register a wrapper).
 */
@Singleton
class LocalFilePlugin @Inject constructor(
    private val reader: Mp3MetadataReader,
) {
    val platform: String = LocalFilePluginConstants.PLATFORM
    val hash: String = LocalFilePluginConstants.HASH
    val supportedMethods: Set<String> = LocalFilePluginConstants.SUPPORTED_METHODS

    /** Returns a file:// URL for standard quality when a local path is available. */
    suspend fun getMediaSource(item: MusicItem, quality: String): MediaSourceResult? {
        if (quality.lowercase() != "standard") return null
        val path = pathOf(item) ?: return null
        return MediaSourceResult(
            url = "file://$path",
            headers = null,
            userAgent = null,
            quality = PlayQuality.STANDARD,
        )
    }

    /** Enriches [item] with title/artist/album/duration read from ID3. */
    suspend fun getMusicInfo(item: MusicItem): MusicItem? {
        val path = pathOf(item) ?: return null
        val meta = reader.read(path) ?: return null
        return item.copy(
            title = meta.title ?: item.title,
            artist = meta.artist ?: item.artist,
            album = meta.album ?: item.album,
            duration = meta.durationMs ?: item.duration,
        )
    }

    /** Imports a music item from an arbitrary local file path. */
    suspend fun importMusicItem(urlLike: String): MusicItem? {
        val meta = reader.read(urlLike) ?: return null
        return MusicItem(
            id = idFor(urlLike),
            platform = platform,
            title = meta.title ?: File(urlLike).nameWithoutExtension,
            artist = meta.artist ?: "",
            album = meta.album,
            duration = meta.durationMs ?: 0L,
            url = null,
            artwork = null,
            qualities = null,
            localPath = urlLike,
        )
    }

    /**
     * Returns rawLrc from:
     *   1. adjacent .lrc with the same basename
     *   2. embedded USLT (when available; MediaMetadataRetriever currently returns null)
     */
    suspend fun getLyric(item: MusicItem): LocalLyric? {
        val path = pathOf(item) ?: return null
        val file = File(path)
        val adjacent = File(file.parentFile ?: return null, file.nameWithoutExtension + ".lrc")
        if (adjacent.exists() && adjacent.canRead()) {
            return runCatching { LocalLyric(rawLrc = adjacent.readText()) }.getOrNull()
        }
        val meta = reader.read(path) ?: return null
        return meta.embeddedLrc?.let { LocalLyric(rawLrc = it) }
    }

    private fun pathOf(item: MusicItem): String? {
        item.localPath?.let { return it }
        val url = item.url ?: return null
        if (!url.startsWith("file://")) return null
        return url.removePrefix("file://").takeIf { it.isNotBlank() }
    }

    /** Stable id derived from the file path (full path SHA1-ish via hashCode). */
    private fun idFor(path: String): String = path.hashCode().toString()
}

/** Minimal lyric value carried inside :plugin. (Phase F may unify with PluginModels.LyricResult.) */
data class LocalLyric(val rawLrc: String)
