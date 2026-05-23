package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

class FeedbackIssueTemplateContractTest {

    @Test
    fun github_issue_template_exists_for_app_feedback_h5_entry() {
        val template = File(repoRoot(), ".github/ISSUE_TEMPLATE/user_feedback.yml")
        check(template.exists()) {
            "Expected app feedback to open the GitHub H5 issue form backed by .github/ISSUE_TEMPLATE/user_feedback.yml"
        }
        val text = template.readText()

        check(text.contains("name: 用户反馈")) {
            "Issue template should be clearly named for app feedback."
        }
        check(text.contains("label: 问题描述")) {
            "Issue template should ask for the issue description in GitHub H5."
        }
        check(text.contains("label: 日志包与截图")) {
            "Issue template should tell users to upload Logan zip and screenshots themselves."
        }
        check(text.contains("生成日志包")) {
            "Issue template should mention the in-app Logan package generation path."
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
