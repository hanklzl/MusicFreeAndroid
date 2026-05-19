package com.hank.musicfree.plugin.media

/**
 * SAM interface that checks whether a local URI/path refers to a readable file.
 * Allows the local short-circuit logic in [PluginMediaSourceService] to be tested
 * without a real [android.content.ContentResolver].
 */
fun interface LocalFileProbe {
    fun isReadable(uri: String): Boolean
}
