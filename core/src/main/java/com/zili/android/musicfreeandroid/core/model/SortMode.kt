package com.zili.android.musicfreeandroid.core.model

enum class SortMode {
    Manual, Title, Artist, Album, Newest, Oldest;

    companion object { val DEFAULT: SortMode = Manual }
}
