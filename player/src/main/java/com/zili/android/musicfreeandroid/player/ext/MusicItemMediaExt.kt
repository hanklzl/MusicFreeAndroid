package com.zili.android.musicfreeandroid.player.ext

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.R as CoreR

fun MusicItem.toMediaItem(defaultArtworkUri: Uri? = null): MediaItem {
    val mediaUri = url
    require(!mediaUri.isNullOrBlank()) {
        "Cannot create MediaItem without URL for: $title ($platform:$id)"
    }

    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")
        .setUri(mediaUri)

    val artworkUri = artwork
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?: defaultArtworkUri

    builder.setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.let { setAlbumTitle(it) }
                artworkUri?.let { setArtworkUri(it) }
            }
            .build()
    )

    return builder.build()
}

fun Context.defaultAlbumArtworkUri(): Uri {
    return Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(packageName)
        .appendPath(CoreR.drawable.album_default.toString())
        .build()
}
