package com.zili.android.musicfreeandroid.feature.home.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionCapabilitiesTest {

    @Test
    fun `playlist supports search membership editing and persistence`() {
        val capabilities = CollectionSource.Playlist(playlistId = "playlist-1").capabilities()

        assertEquals(
            CollectionCapabilities(
                supportsSearch = true,
                supportsMembershipEditing = true,
                isPersistent = true,
            ),
            capabilities,
        )
    }

    @Test
    fun `history supports search without membership editing and is persistent`() {
        val capabilities = CollectionSource.History.capabilities()

        assertEquals(
            CollectionCapabilities(
                supportsSearch = true,
                supportsMembershipEditing = false,
                isPersistent = true,
            ),
            capabilities,
        )
    }

    @Test
    fun `local library supports search without membership editing and is persistent`() {
        val capabilities = CollectionSource.LocalLibrary.capabilities()

        assertEquals(
            CollectionCapabilities(
                supportsSearch = true,
                supportsMembershipEditing = false,
                isPersistent = true,
            ),
            capabilities,
        )
    }

    @Test
    fun `transient supports search without membership editing and is not persistent`() {
        val capabilities = CollectionSource.Transient(sourceId = "session-42").capabilities()

        assertEquals(
            CollectionCapabilities(
                supportsSearch = true,
                supportsMembershipEditing = false,
                isPersistent = false,
            ),
            capabilities,
        )
    }
}
