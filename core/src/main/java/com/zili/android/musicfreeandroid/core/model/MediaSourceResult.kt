package com.zili.android.musicfreeandroid.core.model

/**
 * Media source returned from a plugin's `getMediaSource(...)` call.
 *
 * Phase F adds [contentType] as an optional MIME hint — some plugins serve
 * media URLs that don't reveal their format via `Content-Type` (e.g.
 * authenticated redirects, signed URLs) and surfacing the plugin-declared
 * `Content-Type` lets ExoPlayer route to the right MediaSource factory without
 * relying on URL extension sniffing.
 */
data class MediaSourceResult(
    val url: String,
    val headers: Map<String, String>? = null,
    val userAgent: String? = null,
    val quality: PlayQuality? = null,
    val contentType: String? = null,
)
