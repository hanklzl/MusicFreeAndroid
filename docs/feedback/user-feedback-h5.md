# 用户反馈 H5 入口

> 文档状态：当前规范
> 适用范围：侧栏“用户反馈”入口、GitHub Issue H5 表单、Logan 日志包手动上传说明。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../DOCS_STATUS.md) ｜ [AGENTS](../../AGENTS.md)
> 最后校验：2026-05-23

## 目标

侧栏提供“用户反馈”入口，点击后打开 GitHub H5 新建 Issue 页面。用户在 GitHub 页面中填写问题、上传截图或录屏，并手动上传应用生成的 Logan 日志包 zip。

该流程不在 APK 内创建 GitHub issue，也不在 APK 内上传附件，原因：

- GitHub 创建 issue REST API 需要具备 Issues write 权限的 token，不应把写权限 token 放入客户端。
- GitHub 附件上传是网页编辑器能力，不是稳定公开的 issue 创建 REST API。
- 日志包和截图由用户在 GitHub H5 页面中主动上传，能保留用户确认与隐私边界。

## 代码入口

- H5 URL 单一来源：[FeedbackIssueLinks.kt](../../core/src/main/java/com/hank/musicfree/core/feedback/FeedbackIssueLinks.kt)
- 侧栏模型：[HomeDrawerNavigation.kt](../../feature/home/src/main/java/com/hank/musicfree/feature/home/HomeDrawerNavigation.kt)
- 点击处理：[HomeScreen.kt](../../feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt)
- 浏览器打开：[AppNavHost.kt](../../app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt)
- GitHub Issue Form 模板：[user_feedback.yml](../../.github/ISSUE_TEMPLATE/user_feedback.yml)

## 行为规范

1. “用户反馈”位于首页侧栏“软件”分组中，“检查更新”之后，“关于 MusicFree”之前。
2. 点击入口必须记录 `ui_click`，`targetId` 为 `home.drawer.feedback`。
3. 打开 H5 页面必须记录 `feedback_issue_h5_open`；打开失败必须记录 `feedback_issue_h5_open_failed` 并向用户提示。
4. H5 URL 必须指向 `https://github.com/hanklzl/MusicFreeAndroid/issues/new`，并带 `template=user_feedback.yml`。
5. Issue Form 模板必须提示用户上传截图/录屏和 Logan 日志包 zip。
6. Logan 日志包生成仍复用既有设置页入口：`设置 > 基础设置 > 开发选项 > 生成日志包并分享`。

## 验收

- `:core:testDebugUnitTest --tests '*FeedbackIssueLinksTest'`：锁定 H5 URL 与模板文件名。
- `:app:testDebugUnitTest --tests '*FeedbackIssueTemplateContractTest'`：锁定 GitHub Issue Form 模板存在且包含日志包/截图上传说明。
- `:feature:home:testDebugUnitTest --tests '*HomeDrawerUiModelTest'`：锁定侧栏“软件”分组入口顺序。
- `:app:assembleDebug`：验证入口、模板、导航层 wiring 不破坏 Debug 构建。
