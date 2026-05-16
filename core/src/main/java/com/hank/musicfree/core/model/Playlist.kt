package com.hank.musicfree.core.model

data class Playlist(
    val id: String,
    val name: String,
    val coverUri: String? = null,
    val description: String? = null,
    val sortMode: SortMode = SortMode.Manual,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val worksNum: Int = 0,
) {
    val isDefault: Boolean get() = id == DEFAULT_FAVORITE_ID

    companion object {
        const val DEFAULT_FAVORITE_ID = "favorite"
        const val DEFAULT_FAVORITE_NAME = "我喜欢"
    }
}
