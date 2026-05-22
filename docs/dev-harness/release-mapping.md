# Release Mapping — 线上 R8 堆栈还原

> 文档状态：当前规范（跨 AI agent 共享）
> 适用范围：当用户/反馈渠道提供 Release 构建（已 R8 混淆）的堆栈，需要还原成源码类名时
> 直接执行：是
> 最后校验：2026-05-22

## 1. 触发场景

Release APK 经过 R8 minify，堆栈里类名会变成 `wh2.f`、`gw.g`、`cr.a`、`fo3.E` 这种单/双字母混淆名。任何来自线上用户、反馈渠道、Logan 导出、ANR/Tombstone 的 release 堆栈，**都必须先还原再分析**，否则只能看见 `Unknown Source:182` 这种无意义偏移。

不要凭混淆名猜测原始类（`gw` 可能是 media3 也可能是日志，肉眼看不出来）；必须用 release mapping 比对。

## 2. mapping 文件来源

每个 GitHub Release（仓库 `hanklzl/MusicFreeAndroid`）都会附带 `mapping-vX.Y.Z.zip` asset，在 `.github/workflows/android-release-apk.yml` 的发布步骤里自动上传。zip 内只有一个 `mapping.txt`（合并 arm64-v8a / x86_64 单 ABI mapping，因为 R8 输出按 variant 而非 ABI 区分）。

## 3. 获取流程

```bash
VERSION=v1.2.3   # 与崩溃版本一一对应；版本号错了所有名字都会偏

mkdir -p /tmp/mfa-mapping
gh release download "$VERSION" -p "mapping-${VERSION}.zip" \
  --clobber -D /tmp/mfa-mapping -R hanklzl/MusicFreeAndroid
unzip -o "/tmp/mfa-mapping/mapping-${VERSION}.zip" -d /tmp/mfa-mapping/extracted/
# => /tmp/mfa-mapping/extracted/mapping/mapping.txt
```

zip 解压后约 50MB+。注意确认版本号——`v1.2.3` 的 mapping 不能用来还原 `v1.2.4` 的堆栈。

## 4. 反查混淆名

### 4.1 单类反查

```bash
grep -E ' -> wh2:$' /tmp/mfa-mapping/extracted/mapping/mapping.txt
# => com.hank.musicfree.logging.LoganMfLogger -> wh2:
```

### 4.2 批量反查（推荐）

把堆栈里所有混淆类名抽出来一次性查：

```bash
SYMS=(wh2 gw qw2 cr gu2 vn5 ru2 sp2 jq2 gp2 pn3 fo3)
for sym in "${SYMS[@]}"; do
  printf '=== %s ===\n' "$sym"
  grep -E " -> ${sym}:$" /tmp/mfa-mapping/extracted/mapping/mapping.txt
done
```

### 4.3 方法/行号还原

类名查到后，方法名（`f`、`g`、`E` 等）和 `Unknown Source:N` 行号要在类块内反查。R8 输出的 mapping 行格式：

```
原始类名 -> 混淆类名:
    原始类型 原始方法名(参数...) -> 混淆方法名
    起始行:结束行:原始类型 原始方法名(...):源码起始行:源码结束行 -> 混淆方法名
```

把整段类块 dump 出来：

```bash
awk '/ -> wh2:$/{flag=1; print; next} /^[a-zA-Z]/{flag=0} flag{print}' \
  /tmp/mfa-mapping/extracted/mapping/mapping.txt
```

`gw.g(Unknown Source:182)` 里的 `182` 是 R8 输出的合成行号（PC 偏移），在类块里找形如 `0:N:... -> g` 的区段，N >= 182 的最小那段就是源码所在方法 / 行。

### 4.4 retrace 工具（可选）

整段堆栈一次性还原可以用 Android SDK 的 `retrace`：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/retrace" \
  /tmp/mfa-mapping/extracted/mapping/mapping.txt \
  < /tmp/stack.txt
```

输出仍会保留协程内部 `Unknown Source:N`，但混淆类名都会替换成源码类名。

## 5. 排查 Coroutine / Flow 堆栈

Flow / 协程的递归崩溃栈（典型：`StackOverflowError`）里会出现大量 `kotlinx.coroutines.*` 和 `androidx.media3.session.*` 框架帧。还原时重点关注业务类：

| 业务类 | 混淆映射示例（仅 v1.2.3） |
|---|---|
| `com.hank.musicfree.player.controller.PlayerController` | `fo3` |
| `PlayerController$$ExternalSyntheticLambda*` | `pn3` 等 |
| `com.hank.musicfree.logging.LoganMfLogger` | `wh2` |
| `com.hank.musicfree.logging.MfLog` | `qw2` |

`$$ExternalSyntheticLambda*` 是 D8/R8 给 Lambda 生成的合成类，对应到源码里某个 `controller.someMethod { ... }` 的闭包；类块里 `# {"id":"com.android.tools.r8.synthesized"}` 注释会标出生成它的源方法。

## 6. 工作流总结

排查 release 崩溃 / ANR 时按这个顺序走：

1. 从用户报告 / 截图 / Logan 导出里确认 **崩溃版本号**。
2. 用 §3 命令下载并解压对应版本的 `mapping.txt`。
3. 抽取堆栈里所有混淆类名，用 §4.2 批量反查。
4. 对关键帧用 §4.3 还原方法名 / 源码行。
5. 在源码里定位调用链，结合 git log（`git log VERSION...` 或 `git show <commit>`）找到 regression commit。
6. 修复后写一条 incident（`docs/dev-harness/incidents/`）记录现象、根因和守门。

## 7. 跨工具一致性

- 任何 AI agent（Claude Code / Codex / Gemini / 自动化 sub-agent）排查 release 崩溃时，都必须先按本文还原堆栈，禁止凭混淆类名/偏移直接猜测代码位置。
- mapping zip 是版本绑定的；不要复用旧版本的 mapping 去看新版本堆栈。
- 本地不保留 mapping 缓存（`/tmp/mfa-mapping/` 是一次性目录），每次按崩溃版本号重新拉取。
