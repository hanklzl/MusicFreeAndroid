package com.zili.android.musicfreeandroid.feature.playerui.component

object MiniPlayerMockFactory {
    private val mockSongs = listOf(
        Triple("In the End", "Linkin Park", null as String?),
        Triple("半兽人", "周杰伦", null),
        Triple("Bohemian Rhapsody", "Queen", null),
    )

    fun buildMockUiModel(
        currentIndex: Int = 0,
        isPlaying: Boolean = true,
    ): MiniPlayerUiModel {
        val idx = ((currentIndex % mockSongs.size) + mockSongs.size) % mockSongs.size
        val current = mockSongs[idx]
        val prev = mockSongs[(idx - 1 + mockSongs.size) % mockSongs.size]
        val next = mockSongs[(idx + 1) % mockSongs.size]
        return MiniPlayerUiModel(
            coverUri = current.third,
            title = current.first,
            artist = current.second,
            isPlaying = isPlaying,
            progress = 0.35f,
            hasPrev = true,
            hasNext = true,
            prevTitle = "${prev.first} - ${prev.second}",
            nextTitle = "${next.first} - ${next.second}",
        )
    }
}
