package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class HomeScreenState {
    var isDrawerOpen by mutableStateOf(false)
        private set

    var isTimingCloseVisible by mutableStateOf(false)
        private set

    var isLanguageDialogVisible by mutableStateOf(false)
        private set

    var isUpdateCheckVisible by mutableStateOf(false)
        private set

    fun openDrawer() {
        isDrawerOpen = true
    }

    fun closeDrawer() {
        isDrawerOpen = false
    }

    fun showTimingCloseDialog() {
        isTimingCloseVisible = true
    }

    fun dismissTimingCloseDialog() {
        isTimingCloseVisible = false
    }

    fun showLanguageDialog() {
        isLanguageDialogVisible = true
    }

    fun dismissLanguageDialog() {
        isLanguageDialogVisible = false
    }

    fun showUpdateCheck() {
        isUpdateCheckVisible = true
    }

    fun dismissUpdateCheck() {
        isUpdateCheckVisible = false
    }

    fun onBackPressedConsumed(): Boolean = when {
        isDrawerOpen -> {
            closeDrawer()
            true
        }

        isTimingCloseVisible -> {
            dismissTimingCloseDialog()
            true
        }

        isLanguageDialogVisible -> {
            dismissLanguageDialog()
            true
        }

        isUpdateCheckVisible -> {
            dismissUpdateCheck()
            true
        }

        else -> false
    }
}
