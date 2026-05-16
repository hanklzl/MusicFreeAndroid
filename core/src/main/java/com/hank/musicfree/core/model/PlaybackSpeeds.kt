package com.hank.musicfree.core.model

object PlaybackSpeeds {
    /** Speeds aligned with RN [50, 75, 100, 125, 150, 175, 200] / 100. */
    val ALL: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    const val DEFAULT: Float = 1.0f
}
