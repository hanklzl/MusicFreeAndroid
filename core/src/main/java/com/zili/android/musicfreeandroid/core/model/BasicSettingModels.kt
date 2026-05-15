package com.zili.android.musicfreeandroid.core.model

enum class SearchResultClickAction {
    PlayMusic,
    PlayMusicAndReplace,
}

enum class AlbumMusicClickAction {
    PlayMusic,
    PlayAlbum,
}

enum class MusicDetailDefaultPage {
    Album,
    Lyric,
}

enum class QualityFallbackOrder {
    Asc,
    Desc,
}

enum class AudioInterruptionAction {
    Pause,
    LowerVolume,
}

enum class LyricAssociationType {
    Search,
    Input,
}

enum class DesktopLyricAlignment {
    Left,
    Center,
    Right,
}

fun PlayQuality.fallbackSequence(order: QualityFallbackOrder): List<PlayQuality> {
    val qualities = listOf(
        PlayQuality.LOW,
        PlayQuality.STANDARD,
        PlayQuality.HIGH,
        PlayQuality.SUPER,
    )
    val index = qualities.indexOf(this).coerceAtLeast(0)
    val lower = qualities.subList(0, index)
    val higher = qualities.subList(index + 1, qualities.size)
    return when (order) {
        QualityFallbackOrder.Asc -> listOf(this) + higher + lower.asReversed()
        QualityFallbackOrder.Desc -> listOf(this) + lower.asReversed() + higher
    }
}
