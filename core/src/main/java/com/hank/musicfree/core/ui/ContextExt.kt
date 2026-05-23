package com.hank.musicfree.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the [Context] wrapper chain to find the hosting [Activity], or null if none
 * (e.g. a non-Activity context or a Compose @Preview). Used to reach the window for
 * system-bar appearance control.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
