package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface PlaylistDefaultSortProvider {
    suspend fun defaultSortMode(): SortMode

    object Manual : PlaylistDefaultSortProvider {
        override suspend fun defaultSortMode(): SortMode = SortMode.Manual
    }
}

class AppPlaylistDefaultSortProvider @Inject constructor(
    private val appPreferences: AppPreferences,
) : PlaylistDefaultSortProvider {
    override suspend fun defaultSortMode(): SortMode =
        appPreferences.musicOrderInLocalSheet.first()
}
