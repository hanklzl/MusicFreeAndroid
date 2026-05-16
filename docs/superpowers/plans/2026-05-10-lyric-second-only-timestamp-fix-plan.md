# 歌词纯秒小数时间戳解析修复 Plan

> 文档状态：当前规范（本次实施计划）
> 来源 spec：[2026-05-10-lyric-second-only-timestamp-fix-design.md](../specs/2026-05-10-lyric-second-only-timestamp-fix-design.md)
> 关联 incident：INC-2026-0017
> 创建日期：2026-05-10

## 工作流约束

- 在 worktree `.worktrees/fix-lyric-second-only-timestamp` 内开发，分支 `fix/lyric-second-only-timestamp`。
- 实施风格：subagent-driven-development，按顺序拆步执行；每步完成后必有可观测的验证手段。
- 所有改动只允许动到本 plan 列出的文件；任何 Spec 之外的清理留作后续。

## 步骤

### Step 1 — Parser 修复（core 层）

文件：
- `core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt`
- `core/src/main/java/com/hank/musicfree/core/model/LyricDocument.kt`
- `core/src/main/java/com/hank/musicfree/core/lyric/LyricTiming.kt`

变更：
1. `LyricParser.timestampTokenRegex` 扩展为 `\\[(?:\\d+:){0,2}\\d+(?:\\.\\d+)?]`。
2. `LyricParser.parseTimestampMs` 允许 `parts.size in 1..3`（保留 60 进位与 ms 计算逻辑）。
3. `LyricDocument.lyricTimestampRegex` 同步扩展（与 1 保持一致）。
4. `LyricTiming.currentLineIndex`：lines 全部 `timeMs == 0L` 且 size>1 → 返回 null。

验证：
- `./gradlew :core:testDebugUnitTest --tests *LyricParserTest --tests *LyricTimingTest`

### Step 2 — 单测覆盖

文件：
- `core/src/test/java/com/hank/musicfree/core/lyric/LyricParserTest.kt`
- `core/src/test/java/com/hank/musicfree/core/lyric/LyricTimingTest.kt`

新增用例：
- `parsesSecondOnlyTimestamp`、`parsesFractionalSecondOnlyTimestamp`、`stripsSecondOnlyTimestampInRenderedText`、`mixedFormatsLrcIsTimed`、`secondOnlyTranslationLineMergesWithBaseTimestamp`。
- `untimedLinesReturnNullCurrentIndex`。

验证：
- `./gradlew :core:testDebugUnitTest`

### Step 3 — Harness 入档与 contract test

文件：
- `docs/dev-harness/incidents/INC-2026-0017.md`（新增）
- `docs/dev-harness/incidents/index.md`（追加索引行）
- `docs/dev-harness/player/rules.md`（追加 `#rule-lyric-parser-supports-second-only-timestamp` 段）
- `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricTimestampFormatContractTest.kt`（新增）

contract test 内容：
- 加载一组黄金样本（含 `[265.35]`、`[m:ss.xx]`、混合），调用 `LyricParser.parse`，断言：
  - `doc.isTimed == true`
  - 每行 `text` 不含 `[`
  - `lines` 按 `timeMs` 严格升序
- 任何 regex 收窄都会破坏断言，从而强制更新 incident。

验证：
- `./gradlew :feature:player-ui:testDebugUnitTest --tests *LyricTimestampFormatContractTest`

### Step 4 — 端到端 sanity（可选 / 时间允许）

- `./gradlew :app:assembleDebug` 通过即可。
- Manual run：构建 APK 在模拟器上启动一首返回纯秒小数 LRC 的歌曲，确认：
  - 歌词每行不再有 `[xxx.xx]` 前缀；
  - 进入歌词页定位到当前行而非最后行；
  - 播放过程中自动跟随生效。

### Step 5 — 提交与归档

- 单一逻辑提交：核心修复 + 测试 + harness。
- 提交信息：`fix(lyric): support second-only LRC timestamps and guard against untimed jump`，带 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`。
- 不在本次中合并到 main；交回主工作区由用户决定 merge 流程。

## 风险与回退

- **风险 1**：扩展后的 regex 误把方括号里的非时间戳（如 `[Chorus]`）当时间戳。已经有用例 `preservesBracketedTextAfterTimestamp` 覆盖；新 regex 仅匹配纯数字（带可选 `.`），不会吞掉 `[Chorus]`。
- **风险 2**：`LyricTiming` 的 untimed 收紧让真实 timed 歌词意外退化。前置条件是"所有 line timeMs==0"，timed LRC 至少一行 >0，因此互不影响；新增 `untimedLinesReturnNullCurrentIndex` 用例 + 已有 timed 用例并存即可保证。
- **回退路径**：单一提交，必要时直接 `git revert <commit>`。
