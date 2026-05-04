package com.zili.android.musicfreeandroid.core.model

import org.junit.Assert.assertFalse
import org.junit.Test

class LyricDocumentTest {

    @Test
    fun constructorKeepsTaskOnePositionalShape() {
        val doc = LyricDocument(
            "m1",
            "demo",
            emptyList(),
            0L,
            LyricSourceInfo.Plugin("demo"),
            null,
            null,
            null,
        )

        assertFalse(doc.isTimed)
    }
}
