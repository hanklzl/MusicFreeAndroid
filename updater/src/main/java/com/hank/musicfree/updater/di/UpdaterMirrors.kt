package com.hank.musicfree.updater.di

object UpdaterMirrors {

    const val GITHUB_OWNER = "hanklzl"
    const val GITHUB_REPO = "MusicFreeAndroid"

    val VERSION_JSON_MIRRORS: List<String> = listOf(
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/gh-pages/release/version.json",
        "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@gh-pages/release/version.json",
    )
}
