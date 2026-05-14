package com.zili.android.musicfreeandroid.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HorizontalTabSwipeTest {
    @Test
    fun `left swipe past threshold moves to next tab`() {
        assertEquals(
            2,
            resolveHorizontalSwipeTarget(
                selectedIndex = 1,
                pageCount = 4,
                dragDistancePx = -80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `right swipe past threshold moves to previous tab`() {
        assertEquals(
            0,
            resolveHorizontalSwipeTarget(
                selectedIndex = 1,
                pageCount = 4,
                dragDistancePx = 80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `drag below threshold keeps current tab`() {
        assertEquals(
            1,
            resolveHorizontalSwipeTarget(
                selectedIndex = 1,
                pageCount = 4,
                dragDistancePx = -32f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `drag equal to threshold keeps current tab`() {
        assertEquals(
            1,
            resolveHorizontalSwipeTarget(
                selectedIndex = 1,
                pageCount = 4,
                dragDistancePx = -48f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `first tab right swipe stays on first tab`() {
        assertEquals(
            0,
            resolveHorizontalSwipeTarget(
                selectedIndex = 0,
                pageCount = 4,
                dragDistancePx = 80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `last tab left swipe stays on last tab`() {
        assertEquals(
            3,
            resolveHorizontalSwipeTarget(
                selectedIndex = 3,
                pageCount = 4,
                dragDistancePx = -80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `single page keeps current index`() {
        assertEquals(
            0,
            resolveHorizontalSwipeTarget(
                selectedIndex = 0,
                pageCount = 1,
                dragDistancePx = -80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `empty page count keeps current index`() {
        assertEquals(
            0,
            resolveHorizontalSwipeTarget(
                selectedIndex = 0,
                pageCount = 0,
                dragDistancePx = -80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `negative selected index stays unchanged`() {
        assertEquals(
            -1,
            resolveHorizontalSwipeTarget(
                selectedIndex = -1,
                pageCount = 4,
                dragDistancePx = -80f,
                thresholdPx = 48f,
            ),
        )
    }

    @Test
    fun `selected index beyond page count stays unchanged`() {
        assertEquals(
            4,
            resolveHorizontalSwipeTarget(
                selectedIndex = 4,
                pageCount = 4,
                dragDistancePx = 80f,
                thresholdPx = 48f,
            ),
        )
    }
}
