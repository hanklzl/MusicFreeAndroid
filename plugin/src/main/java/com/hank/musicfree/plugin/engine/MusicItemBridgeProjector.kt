package com.hank.musicfree.plugin.engine

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.repository.LyricRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase F: explicit MusicItem → bridge-map boundary.
 *
 * `JsBridge.musicItemToMap` only serialises the public [MusicItem] surface
 * (id / platform / title / artist / album / duration / url / artwork). Plugins
 * may legitimately want to react to Android-side state like "is this track
 * downloaded?" or "what user-configured lyric offset applies?" Previously the
 * only way to thread that state through was to mutate the cross-process
 * [MusicItem] itself or, worse, lean on the RN-era `"$"` private marker. Phase
 * F instead projects that state at the call boundary so the persistence layer
 * stays the single source of truth.
 *
 * Consumers (e.g. [com.hank.musicfree.plugin.manager.JsLoadedPlugin])
 * MUST call [project] before invoking any JS-side method that accepts a
 * MusicItem-shaped argument (`getMediaSource`, `getLyric`, `getMusicInfo`,
 * `getMusicComments`).
 */
@Singleton
class MusicItemBridgeProjector @Inject constructor(
    private val downloadedTrackDao: DownloadedTrackDao,
    private val lyricRepository: LyricRepository,
) {
    /**
     * Returns a JSON-friendly bridge map for [item] with [DownloadedTrackEntity]
     * and [LyricCacheEntity] state layered on top of the public [MusicItem]
     * fields. The base shape comes from [JsBridge.musicItemToMap], so reserved
     * keys (`"$"`, `"internal"`) are already filtered out.
     *
     * Field semantics, kept aligned with RN MusicFree localFilePlugin shape:
     *  - `localPath`: relative path from DownloadedTrack when the track has
     *    been downloaded. Falls back to `mediaStoreUri` if the row exists but
     *    `relativePath` happens to be blank (defensive — schema currently has
     *    it non-null but it's user-visible storage). Omitted otherwise so
     *    plugins can probe with `if (musicItem.localPath)`.
     *  - `downloaded`: explicit boolean set when the DownloadedTrack row
     *    exists. Easier for JS to branch on than `localPath != null`.
     *  - `lyricOffset`: user-configured per-track offset (ms). Only emitted
     *    when non-zero so plugins can apply `offset || 0` without ambiguity.
     *
     * DAO/repository failures are swallowed; the projector must never block a
     * plugin call because of a downstream Room/IO hiccup.
     */
    suspend fun project(item: MusicItem): Map<String, Any?> {
        val base = JsBridge.musicItemToMap(item).toMutableMap()

        val downloaded = runCatching {
            downloadedTrackDao.get(item.id, item.platform)
        }.getOrNull()
        if (downloaded != null) {
            // Prefer `relativePath` (user-visible storage location, e.g.
            // `Music/<title>.mp3`) when present; fall back to the MediaStore
            // content URI so an empty `relativePath` does not silently strip
            // the local path from the bridge map.
            base["localPath"] = downloaded.relativePath.takeIf { it.isNotBlank() }
                ?: downloaded.mediaStoreUri
            base["downloaded"] = true
        } else if (!item.localPath.isNullOrBlank()) {
            // `localPath` may already live on the MusicItem (imported via
            // LocalFilePlugin.importMusicItem). Preserve it across the bridge.
            base["localPath"] = item.localPath
        }

        val lyricCache = runCatching { lyricRepository.getCache(item) }.getOrNull()
        val offsetMs = lyricCache?.userOffsetMs ?: 0L
        if (offsetMs != 0L) {
            base["lyricOffset"] = offsetMs
        }

        return base.toMap()
    }
}
