package com.hank.musicfree.feature.home.collection

data class CollectionCapabilities(
    val supportsSearch: Boolean = true,
    val supportsMembershipEditing: Boolean = false,
    val isPersistent: Boolean = false,
)

fun CollectionSource.capabilities(): CollectionCapabilities =
    when (this) {
        is CollectionSource.Playlist -> CollectionCapabilities(
            supportsSearch = true,
            supportsMembershipEditing = true,
            isPersistent = true,
        )
        CollectionSource.History -> CollectionCapabilities(
            supportsSearch = true,
            supportsMembershipEditing = false,
            isPersistent = true,
        )
        CollectionSource.LocalLibrary -> CollectionCapabilities(
            supportsSearch = true,
            supportsMembershipEditing = false,
            isPersistent = true,
        )
        is CollectionSource.Transient -> CollectionCapabilities(
            supportsSearch = true,
            supportsMembershipEditing = false,
            isPersistent = false,
        )
    }
