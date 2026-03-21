package com.zili.android.musicfreeandroid.feature.home.collection

import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionSourceResolutionTest {

    @Test
    fun `local-library route resolves to local library collection source`() {
        val route = SearchMusicListRoute.localLibrary()

        val source = route.toCollectionSource()

        assertEquals(CollectionSource.LocalLibrary, source)
    }

    @Test
    fun `transient route resolves to transient collection source`() {
        val route = SearchMusicListRoute.transient(sourceId = "session-42")

        val source = route.toCollectionSource()

        assertEquals(CollectionSource.Transient(sourceId = "session-42"), source)
    }
}
