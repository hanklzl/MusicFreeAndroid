package com.zili.android.musicfreeandroid.player.ext

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zili.android.musicfreeandroid.core.model.MusicItem

fun MusicItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")

    url?.let { builder.setUri(it) }

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
