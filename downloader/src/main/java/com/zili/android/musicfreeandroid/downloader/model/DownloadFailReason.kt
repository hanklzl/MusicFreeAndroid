package com.zili.android.musicfreeandroid.downloader.model

enum class DownloadFailReason {
    FailToFetchSource,
    NoWritePermission,
    NotAllowToDownloadInCellular,
    NetworkOffline,
    Unknown,
}
