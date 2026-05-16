package com.hank.musicfree.downloader.model

enum class DownloadFailReason {
    FailToFetchSource,
    NoWritePermission,
    NotAllowToDownloadInCellular,
    NetworkOffline,
    Unknown,
}
