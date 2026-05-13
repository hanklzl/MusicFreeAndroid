package com.zili.android.musicfreeandroid.downloader.engine

import android.net.Uri
import java.io.File

class FakeMediaStoreWriter {
    @Volatile var commitCount = 0; private set
    @Volatile var lastCommittedUri: Uri? = null
    fun asWriter(): suspend (File, String, String, String, Long) -> Uri = { f, _, _, _, _ ->
        synchronized(this) {
            commitCount++
            lastCommittedUri = Uri.parse("content://media/external/audio/media/${f.nameWithoutExtension}")
        }
        lastCommittedUri!!
    }
}
