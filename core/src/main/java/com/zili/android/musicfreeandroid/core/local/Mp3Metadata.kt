package com.zili.android.musicfreeandroid.core.local

/** Result of reading ID3/embedded metadata from a local audio file. */
data class Mp3Metadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val coverBytes: ByteArray?,
    val embeddedLrc: String?,
) {
    // Override equals/hashCode because ByteArray uses identity equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mp3Metadata) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            durationMs == other.durationMs &&
            coverBytes.contentEqualsOrNull(other.coverBytes) &&
            embeddedLrc == other.embeddedLrc
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (durationMs?.hashCode() ?: 0)
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + embeddedLrc.hashCode()
        return result
    }

    private fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean = when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
}
