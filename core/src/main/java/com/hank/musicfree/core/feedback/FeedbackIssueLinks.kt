package com.hank.musicfree.core.feedback

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FeedbackIssueLinks {
    const val Repository = "hanklzl/MusicFreeAndroid"
    const val IssueTemplateFile = "user_feedback.yml"
    private const val DefaultTitle = "[反馈] "
    private const val DefaultLabel = "feedback"

    fun newIssueUrl(
        title: String = DefaultTitle,
        labels: String = DefaultLabel,
    ): String {
        return "https://github.com/$Repository/issues/new" +
            "?template=${encode(IssueTemplateFile)}" +
            "&labels=${encode(labels)}" +
            "&title=${encode(title)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
