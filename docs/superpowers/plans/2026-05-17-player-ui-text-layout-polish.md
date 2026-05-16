# 播放器文字布局细节修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复底部 mini 播放器标题过早省略，以及播放详情页插件标签圆角 / 高度不对齐的问题。

**Architecture:** 本次只调整 `:feature:player-ui` 的 Compose 展示层。mini 播放器把标题和歌手合并为一个单行富文本节点；播放详情页提取播放器专用平台标签并固定描述行高度。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Robolectric Compose UI tests, Gradle Debug unit tests.

---

> 文档状态：当前计划
> 适用范围：执行 [播放器文字布局细节修复设计](../specs/2026-05-17-player-ui-text-layout-polish-design.md)
> 直接执行：是

## 守门输入

- 已读 `docs/DOCS_STATUS.md`、`AGENTS.md`。
- 已读 `docs/dev-harness/ui/rules.md` / `docs/dev-harness/ui/incidents.md`。
- 已读 `docs/dev-harness/player/rules.md` / `docs/dev-harness/player/incidents.md`。
- 已读 `docs/dev-harness/test/rules.md` / `docs/dev-harness/test/incidents.md`。
- RN 参考：
  - `../MusicFree/src/components/musicBar/index.tsx`
  - `../MusicFree/src/components/musicBar/musicInfo.tsx`
  - `../MusicFree/src/pages/musicDetail/components/navBar.tsx`
  - `../MusicFree/src/components/base/tag.tsx`

## 并行拆分

两个 UI 问题可以并行，因为写入范围不同：

- Task A：mini 播放器文字布局。写入 `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContent.kt` 和 `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContentTest.kt`。
- Task B：播放详情页插件标签样式。写入 `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` 和对应 `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/*Test.kt`。

两个 worker 都必须注意：代码库中可能有其他并行修改，不能回滚对方文件；如必须触碰对方写入范围，应停止并汇报。

## Task A：mini 播放器文字布局

- [x] 在 `MiniPlayerContent.kt` 中把标题、分隔符、歌手合并为单个 `Text`。
- [x] 用 `buildAnnotatedString` 或等价 Compose API 保留标题与歌手不同颜色透明度和字号层级。
- [x] 单个文本容器使用 `Modifier.weight(1f)`、`maxLines = 1`、`TextOverflow.Ellipsis`，让省略按整行计算。
- [x] 不改变外层高度、封面尺寸、播放按钮、队列按钮、点击和横滑手势。
- [x] 在 `MiniPlayerContentTest` 增加长标题短歌手用例，验证长标题节点存在、mini 根节点显示、队列按钮仍存在。

## Task B：播放详情页插件标签

- [x] 在 `PlayerScreen.kt` 中提取 `PlayerPlatformTag` 或等价私有 composable。
- [x] 描述行样式对齐 RN：行高 `rpx(32)`，`Alignment.CenterVertically`。
- [x] 标签样式对齐 RN：高度 `rpx(32)`、左间距 `rpx(12)`、横向 padding `rpx(12)`、圆角 `rpx(24)`、白色文字、白色 0.2 alpha 背景。
- [x] 长歌手名时让歌手文字可收缩，标签不参与 weight，避免平台标签消失。
- [x] 保持 `PlayerNavBar` 标题、歌手、分享、返回结构不变；`PlayerContentLayer` 的 `WindowInsets.statusBars` 避让不变。
- [x] 增加测试锚点并覆盖标签文本存在与高度接近 `rpx(32)`。

## 集成与验证

- [x] 合并两个 worker 的变更后检查格式和 import。
- [x] 运行：
   - `./gradlew :feature:player-ui:testDebugUnitTest --no-daemon`
   - `python3 scripts/dev-harness/grep-check.py`
   - `./gradlew :app:assembleDebug --no-daemon`
   - `git diff --check`
- [x] 本地 review 关注：
   - mini 播放器没有让右侧按钮消失或改变点击目标。
   - 播放页插件标签不会遮挡歌手文本，长平台名可单行省略。
   - 没有改动播放业务逻辑、队列、歌词、状态栏 inset。
- [ ] squash merge 回本地 `main`，提交信息使用 `fix(player-ui): 修复播放器文字布局细节`。
