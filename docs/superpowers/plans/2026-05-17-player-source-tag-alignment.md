# 播放器插件源标签垂直对齐修复实施计划

> 文档状态：历史执行快照
> 适用范围：`docs/superpowers/specs/2026-05-17-player-source-tag-alignment-design.md`
> 日期：2026-05-17

## 目标

修复播放详情页顶部副标题行中歌手名中文底部被裁切、插件源胶囊文字垂直不居中的问题，同时保持 RN 原版 `rpx(32)` 副标题行和 tag 尺寸。

## 任务清单

1. 守门与上下文确认
   - 确认 worktree 分支为 `fix/player-source-tag`。
   - 已读取 `docs/DOCS_STATUS.md`、`docs/dev-harness/ui/rules.md`、`docs/dev-harness/player/rules.md`、`docs/dev-harness/test/rules.md`。
   - 对照 RN `../../../../../../MusicFree/src/pages/musicDetail/components/navBar.tsx` 与 `../../../../../../MusicFree/src/components/base/tag.tsx`。

2. 添加回归测试
   - 在 `PlayerNavBarTest` 中新增中文歌手 + 插件源 case。
   - 增加副标题行 test tag，断言副标题行高度为 `16.dp`（375dp 宽度下 `rpx(32)`）。
   - 断言歌手文本和插件源 tag 均显示，tag 高度为 `16.dp`。

3. 实现布局修复
   - 在 `PlayerScreen.kt` 内为播放器顶部文本增加局部 `TextStyle` helper。
   - 对标题、歌手名、插件源文本设置显式 `lineHeight = fontSize`。
   - 使用 Compose Android 文本平台设置关闭额外 font padding，对齐 RN `includeFontPadding: false`。
   - 保持 `PlayerContentLayer` 的 `WindowInsets.statusBars` 行为不变。

4. Review 与验证
   - 使用 sub-agent 做实现 / 规格 / 代码质量检查。
   - 运行：
     - `./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerNavBarTest' --no-daemon`
     - `python3 scripts/dev-harness/grep-check.py`
     - `./gradlew :app:assembleDebug --no-daemon`
   - 使用模拟器安装 / 打开 Debug 包，进入播放器页检查顶部副标题行。

5. 合并回 main
   - 在 worktree 内确认 diff。
   - 回主工作区 `main` 使用 `git merge --squash fix/player-source-tag`。
   - 提交使用 conventional commits 中文 message。
   - 合并后至少重新运行必要 Debug 构建或说明已在合并前同一提交验证。
   - 清理 worktree 和本地功能分支。
