package com.hank.musicfree.core.local

/**
 * Reads ID3/embedded metadata from a local audio file. Implementations should be
 * tolerant of missing/malformed tags and return null instead of throwing on
 * unreadable files.
 */
interface Mp3MetadataReader {
    suspend fun read(path: String): Mp3Metadata?
}
