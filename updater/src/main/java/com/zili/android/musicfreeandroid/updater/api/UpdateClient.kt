package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

interface UpdateClient {
    suspend fun fetchLatest(): UpdateInfo?
}
