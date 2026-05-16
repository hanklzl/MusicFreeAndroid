package com.hank.musicfree.data.repository

import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.datastore.AppPreferences
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
