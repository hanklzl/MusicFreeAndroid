package com.hank.musicfree.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackIssueLinksTest {

    @Test
    fun `new issue url opens GitHub H5 issue form template`() {
        val url = FeedbackIssueLinks.newIssueUrl()

        assertTrue(url.startsWith("https://github.com/hanklzl/MusicFreeAndroid/issues/new?"))
        assertTrue(url.contains("template=user_feedback.yml"))
        assertTrue(url.contains("labels=feedback"))
        assertTrue(url.contains("title="))
    }

    @Test
    fun `repository and issue template file are stable`() {
        assertEquals("hanklzl/MusicFreeAndroid", FeedbackIssueLinks.Repository)
        assertEquals("user_feedback.yml", FeedbackIssueLinks.IssueTemplateFile)
    }
}
