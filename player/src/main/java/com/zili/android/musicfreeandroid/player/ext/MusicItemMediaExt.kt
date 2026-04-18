package com.zili.android.musicfreeandroid.player.ext

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zili.android.musicfreeandroid.core.model.MusicItem

fun MusicItem.toMediaItem(): MediaItem {
    val mediaUri = url
    require(!mediaUri.isNullOrBlank()) {
        "Cannot create MediaItem without URL for: $title ($platform:$id)"
    }

    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")
        .setUri(mediaUri)

    builder.setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.let { setAlbumTitle(it) }
                artwork?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
    )

    return builder.build()
}
