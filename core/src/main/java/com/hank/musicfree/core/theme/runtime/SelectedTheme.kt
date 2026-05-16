package com.hank.musicfree.core.theme.runtime

/**
 * Identity of the currently selected theme. Storage keys match the RN
 * original (`src/core/theme.ts`) so persisted preferences stay portable.
 */
enum class SelectedTheme(val storageKey: String) {
    P_LIGHT("p-light"),
    P_DARK("p-dark"),
    CUSTOM("custom");

    companion object {
        fun fromStorageKey(key: String?): SelectedTheme =
            entries.firstOrNull { it.storageKey == key } ?: P_DARK
    }
}
