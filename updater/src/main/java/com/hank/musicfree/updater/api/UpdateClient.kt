package com.hank.musicfree.updater.api

import com.hank.musicfree.updater.model.UpdateInfo

interface UpdateClient {
    suspend fun fetchLatest(): UpdateInfo?
}
