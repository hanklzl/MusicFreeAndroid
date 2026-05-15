package com.zili.android.musicfreeandroid.data.repository.listenstats.model

enum class DetailMode {
    ALL_SONGS, ALL_ARTISTS, TOP_SONGS, TOP_ARTISTS,
    FIRST_SEEN, BY_ARTIST, BY_LANGUAGE, BY_GENRE,
}

fun parseDetailMode(raw: String): DetailMode = DetailMode.valueOf(raw)
