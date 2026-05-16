# 歌词纯秒小数时间戳解析修复 Design

> 文档状态：当前规范（本次修复设计快照）
> 适用范围：core LyricParser、LyricDocument、LyricTiming 与配套 dev-harness
> 直接执行：是
> 当前入口：[AGENTS](../../../AGENTS.md)
> 关联 incident：INC-2026-0017
> 关联 rule：[player/rules.md#rule-lyric-parser-supports-second-only-timestamp](../../dev-harness/player/rules.md#rule-lyric-parser-supports-second-only-timestamp)
> 创建日期：2026-05-10

## 1. 背景

播放器全屏歌词出现回归（截图：每行歌词前显示 `[265.35]` 这样的原始时间戳，且点击切到歌词页时立刻跳到最后一行并高亮该行，播放过程中也不滚动）。原版 RN 通过 `src/utils/lrcParser.ts` 中 `const timeReg = /\[[\d:.]+\]/g;` 接受纯秒小数格式（例如 `[265.35]`），而当前 Android 实现 `core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt` 必须出现至少一个冒号才认作时间戳，因此漏过此格式。

本设计仅就 LRC 解析与时间索引修复，不动播放器 UI 渲染。

## 2. 根因分析

定位到三处共因：

1. `LyricParser.parseTimestampMs(tag: String)`：用 `tag.split(':')`，要求 `parts.size in 2..3`，否则返回 null。`265.35` 切出 `["265.35"]`，size=1，被拒。
2. `LyricParser.timestampTokenRegex = "\\[(?:\\d+:)?\\d+:\\d+(?:\\.\\d+)?]"`：模式中至少一个 `\d+:`，`[265.35]` 不命中，因此 `removeTimestampOnlyLines` 也不会从原始文本里 strip 掉。
3. `LyricDocument.lyricTimestampRegex`（同款）：同样错过 `[265.35]`，影响 `isTimed` 兜底推导。

副作用三连：
- 整段 LRC 在 `parseLrc` 里得不到任何 timed item → `sortedLines.ifEmpty { parsePlainText(...) }` 启用 plain-text fallback；fallback 的 strip 也用相同 regex → 文本里保留 `[265.35]`。
- `hasTimestampLine = false`，`isTimed = false` → `PlayerLyricsContent` 的自动跟随 LaunchedEffect 在 `!doc.document.isTimed` 处早退。
- plain-text fallback 把所有 line 的 `timeMs` 设为 `0L`；`LyricTiming.currentLineIndex(lines, playbackPositionMs > 0)` 走二分搜索（所有元素 timeMs=0 < 当前位置）→ 取 `previousPosition = lines.size - 1`，永远把"最后一行"标为 current → 初始化 `LaunchedEffect` 用 `initialLyricScrollIndex(currentLineIndex, lines.size)` 滚到最后一行并高亮（与截图完全吻合）。

## 3. 修复方案

### 3.1 LyricParser

- `parseTimestampMs` 接受单段秒数（含小数）：`parts.size in 1..3` 时仍按 60 进位累计，已有的 `toDoubleOrNull` 路径直接复用即可。
- `timestampTokenRegex` 扩展为 `\\[(?:\\d+:){0,2}\\d+(?:\\.\\d+)?]`：匹配 `[s]`、`[s.ff]`、`[mm:ss(.ff)?]`、`[hh:mm:ss(.ff)?]` 全部三档；同时兼容存量 RN/网络生成歌词。
- 注意：保留对 `[offset:NNN]` 的过滤——它经 `isOffsetMetaLine`/`hasOnlyTimestampOrOffsetTags` 处理，与新 regex 不冲突（新 regex 仍只匹配纯数字 token）。

### 3.2 LyricDocument

- `lyricTimestampRegex` 同步扩展（与 LyricParser 一致），保证 `isTimed` 默认推导对纯秒小数文本也判定为 true。

### 3.3 LyricTiming（防御性收紧）

- `currentLineIndex(...)` 在所有 line `timeMs == 0L` 且 `lines.size > 1` 时返回 null，杜绝纯文本歌词被错误推进到最后一行。已 timed 的 LRC 至少存在一条非 0 时间戳，不受影响。
- 这条收紧未来对所有 plain-text 歌词路径都有效，不只是这次的纯秒小数 LRC。

## 4. 测试

### 4.1 LyricParserTest（单元）

- `parsesSecondOnlyTimestamp`：`[60]` → timeMs=60_000。
- `parsesFractionalSecondOnlyTimestamp`：`[265.35]` → timeMs=265_350。
- `stripsSecondOnlyTimestampInRenderedText`：`[265.35]而我只是嘉宾` → text=`而我只是嘉宾`。
- `mixedFormatsLrcIsTimed`：混合 `[00:01.00]` 和 `[5.5]` → 两行均生成 timed line。
- `secondOnlyTranslationLineMergesWithBaseTimestamp`：翻译同步使用纯秒格式时也按时间戳对齐。

### 4.2 LyricTimingTest（单元）

- `untimedLinesReturnNullCurrentIndex`：lines 全部 `timeMs=0` 且 size>1，返回 null（避免高亮最后一行）。

### 4.3 Harness contract test

- `feature/player-ui/.../harness/contracts/LyricTimestampFormatContractTest.kt`：调用 `LyricParser.parse` 跑一组黄金样本（含 `[265.35]`、`[mm:ss.xx]`、混合），断言 `isTimed=true`、`text` 不含未 strip 的 `[`、按 timeMs 升序排列。任意修改使断言失败时，强制更新此 incident。

## 5. Dev Harness

- 新增 `INC-2026-0017`（player）：纯秒小数 LRC 时间戳未识别，导致 timed lyric 退化为 plain-text fallback、跟随失效。
- 新增 player rule `#rule-lyric-parser-supports-second-only-timestamp`，由上面 contract test guard。
- 同步修改 `docs/dev-harness/player/rules.md` 与 `docs/dev-harness/incidents/index.md`。

## 6. 范围与非目标

- **In scope**：core 层 LRC 解析正确性 + LyricTiming 防御 + 测试 + harness。
- **Out of scope**：歌词加载策略、歌词搜索、UI 渲染样式、自动跟随防抖（已由 INC-2026-0012 守护）。

## 7. 验收

- `./gradlew :core:testDebugUnitTest :feature:player-ui:testDebugUnitTest` 全绿。
- 至少一次 `:app:assembleDebug` 通过；运行 APK，对一首返回 `[s.ff]` 格式 LRC 的歌曲核对：每行不再有 `[xxx.xx]` 前缀，自动滚动跟随，点击切到歌词页时回到当前行而非跳底。
