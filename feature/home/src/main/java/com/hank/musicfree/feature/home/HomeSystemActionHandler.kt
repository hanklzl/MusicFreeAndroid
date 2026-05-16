package com.hank.musicfree.feature.home

interface HomeSystemActionHandler {
    fun backToDesktop()
    suspend fun exitApp()
}
