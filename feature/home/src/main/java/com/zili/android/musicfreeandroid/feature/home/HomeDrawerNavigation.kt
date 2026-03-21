package com.zili.android.musicfreeandroid.feature.home

suspend fun runHomeDrawerNavigation(
    navigate: () -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    navigate()
    runCatching {
        closeDrawer()
    }
}
