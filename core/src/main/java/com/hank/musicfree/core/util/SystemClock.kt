package com.hank.musicfree.core.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
