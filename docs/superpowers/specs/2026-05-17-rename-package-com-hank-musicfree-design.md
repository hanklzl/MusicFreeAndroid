---
title: 应用包名重命名为 com.hank.musicfree 设计
status: 当前规范（一次性执行）
scope: 全仓库
last-reviewed: 2026-05-17
---

# 应用包名重命名为 com.hank.musicfree 设计

## 1. 目标与范围

把当前 `com.zili.android.musicfreeandroid` 重命名为 `com.hank.musicfree`，按"全新应用"发布。老应用 1.0.2 用户的数据迁移**不在本次范围**，由独立的"备份/恢复"功能承担。

### 1.1 范围内（必须改）

- 13 个 module 的 `namespace`
- `:app` 的 `applicationId`（debug 后缀 `.debug` 保留）
- 所有模块 `src/{main,test,androidTest}/java/com/zili/android/musicfreeandroid/**` 源码目录
- 所有 `*.kt / *.kts / *.xml / *.pro / *.json / *.properties / *.md / *.yaml / *.sh` 文件中的 `com.zili.android.musicfreeandroid` 字符串
- contract 测试硬编码的相对路径字符串
- `.claude/settings.local.json` 中 gradle 命令 allowlist
- maestro flows + 相关 shell 脚本
- `docs/superpowers/plans/*`、`docs/superpowers/specs/*`、`docs/home-fidelity/homepage/android/*.xml`（uiautomator dump）等历史文档

### 1.2 范围外（显式保留）

- `tools/logan/out/**`：运行态 Logan 抓包，里面的 `applicationId` / sandbox 路径是历史事实，保留以便溯源
- `build/`、`.gradle/`、`.idea/`、`.worktrees/`
- 任何 user-data 迁移代码
- `versionCode / versionName`（保持 `10002 / 1.0.2`，version 跟踪代码成熟度，与"应用身份"是两件事）
- 应用图标、`app_name` 字符串、签名 keystore（与 package 改名无关）

## 2. 命名映射表

### 2.1 applicationId（仅 `:app`）

| | 现在 | 改后 |
|---|---|---|
| release | `com.zili.android.musicfreeandroid` | `com.hank.musicfree` |
| debug | `com.zili.android.musicfreeandroid.debug` | `com.hank.musicfree.debug` |

### 2.2 module namespace + 源码包

| module | 旧 namespace | 新 namespace |
|---|---|---|
| `app` | `com.zili.android.musicfreeandroid` | `com.hank.musicfree` |
| `core` | `com.zili.android.musicfreeandroid.core` | `com.hank.musicfree.core` |
| `data` | `com.zili.android.musicfreeandroid.data` | `com.hank.musicfree.data` |
| `player` | `com.zili.android.musicfreeandroid.player` | `com.hank.musicfree.player` |
| `plugin` | `com.zili.android.musicfreeandroid.plugin` | `com.hank.musicfree.plugin` |
| `downloader` | `com.zili.android.musicfreeandroid.downloader` | `com.hank.musicfree.downloader` |
| `updater` | `com.zili.android.musicfreeandroid.updater` | `com.hank.musicfree.updater` |
| `logging` | `com.zili.android.musicfreeandroid.logging` | `com.hank.musicfree.logging` |
| `feature:home` | `com.zili.android.musicfreeandroid.feature.home` | `com.hank.musicfree.feature.home` |
| `feature:player-ui` | `com.zili.android.musicfreeandroid.feature.playerui` | `com.hank.musicfree.feature.playerui` |
| `feature:search` | `com.zili.android.musicfreeandroid.feature.search` | `com.hank.musicfree.feature.search` |
| `feature:settings` | `com.zili.android.musicfreeandroid.feature.settings` | `com.hank.musicfree.feature.settings` |
| `feature:listen-stats` | `com.zili.android.musicfreeandroid.feature.listenstats` | `com.hank.musicfree.feature.listenstats` |

### 2.3 源码目录

每个模块的 `src/{main,test,androidTest}/java/com/zili/android/musicfreeandroid/...` → `src/{main,test,androidTest}/java/com/hank/musicfree/...`

### 2.4 ContentProvider authorities

`AndroidManifest.xml` 中 `${applicationId}.feedback-files` 和 `${applicationId}.updater-files` 由 build 占位符自动解析，**不需要手改**。

### 2.5 替换规则

本次仅做严格字符串前缀替换 `com.zili.android.musicfreeandroid` → `com.hank.musicfree`。不顺手做其他重命名（例如 `feature.playerui` 不改成 `feature.player_ui`）。

## 3. 文件级影响面

### 3.1 `git mv` 目录移动

13 模块 × ≤3 个 source set（main/test/androidTest）= 至多 39 次目录移动。命令模式：

```bash
git mv <module>/src/<srcSet>/java/com/zili/android/musicfreeandroid \
       <module>/src/<srcSet>/java/com/hank/musicfree
```

移动后清理空的 `com/zili/android/musicfreeandroid` 上级目录链。

### 3.2 字符串前缀替换（全量、自动）

- 搜索范围：`git ls-files`
- 显式排除：`tools/logan/out/**`、`build/**`、`.gradle/**`、`.idea/**`、`.worktrees/**`
- 替换：`com.zili.android.musicfreeandroid` → `com.hank.musicfree`
- 文件类型：`*.kt / *.kts / *.xml / *.pro / *.json / *.properties / *.md / *.yaml / *.sh / *.gradle`，以及 `AGENTS.md / CLAUDE.md / README.md / CHANGELOG.md / RELEASE.md`

### 3.3 关键非源码 hotspot（重点核对）

- `app/build.gradle.kts` —— namespace + applicationId + testInstrumentationRunner FQN
- 13 个 `build.gradle.kts` —— namespace
- contract 测试硬编码相对路径：
  - `app/src/test/java/.../SplashScreenResourceContractTest.kt`
  - `app/src/test/java/.../navigation/NavigationMinifyContractTest.kt`
  - `app/src/test/java/.../navigation/PlaybackNavigationContractTest.kt`
  - `app/src/test/java/.../navigation/AppNavHostRouteContractTest.kt`
  - `plugin/src/test/java/.../harness/contracts/RnPluginOracleContractTest.kt`
- `maestro/flows/**/*.yaml`（9 个） —— `appId`
- `scripts/maestro/run-smoke.sh`、`scripts/parity-audit/install-both.sh` —— `APP_ID` 变量
- `tools/home-fidelity/capture-homepage-android.sh` —— `EXPECTED_PACKAGE`
- `.claude/settings.local.json` —— 4 条 gradle 命令的测试 FQN

### 3.4 历史文档

- `docs/superpowers/plans/*.md`（~140 文件）
- `docs/superpowers/specs/*.md`（部分）
- `docs/dev-harness/*`（当前规范，少量 FQN）
- `docs/home-fidelity/homepage/android/*.xml`（uiautomator dump）

⚠️ **语义提示**：dump 文件里 `package="com.zili..."` 替换为 `package="com.hank..."` 仅是 grep 一致性，**并不重新跑 capture**。本次重命名提交 message 中应注明此点，避免未来 reviewer 误以为 dump 是新跑的。

## 4. 执行策略

### 4.1 分支与脚本

- 分支：`.worktrees/rename-package`（worktree，按 `AGENTS.md` 约束）
- 脚本：`scripts/rename-package.sh`（一次性执行器，合并后可删除）

### 4.2 步骤顺序

1. **预检**
   - `git status` 必须 clean
   - 记录 baseline：`git grep -F 'com.zili.android.musicfreeandroid' | wc -l`
   - 确认无未跟踪 `com.zili.*` 残留

2. **`git mv` 源码目录**：13 模块 × 3 source set，按固定顺序；不手工 `mkdir -p` 中间路径，由 `git mv` 自然带出；每个模块移完即时 grep 局部核校。

3. **`build.gradle.kts` namespace 替换**：脚本扫所有模块的 `build.gradle.kts`。

4. **`applicationId` 替换**：`app/build.gradle.kts` 一处。

5. **全量字符串替换**：`git ls-files -z | xargs -0 sed -i '' ...`（macOS 兼容写法），显式 exclude `tools/logan/out/` 和 `build/`。

6. **手工核校**
   - contract 测试相对路径
   - `.claude/settings.local.json`
   - `AndroidManifest.xml`（仅核校，`${applicationId}` 占位符无须改）
   - `tools/home-fidelity/capture-homepage-android.sh` 中 `EXPECTED_PACKAGE` 默认值

7. **构建与测试验收**（见 §6）

8. **运行态验收**（见 §6）

9. **合并**：在 worktree 内 commit；切回 main 用 `git merge --squash` 压成单 commit。Commit message 中文 + conventional commits 格式，例如：

```
refactor(app): 应用包名重命名为 com.hank.musicfree

把 applicationId 与全 13 模块 namespace 从 com.zili.android.musicfreeandroid
迁移到 com.hank.musicfree。按"全新应用"发布，老用户数据迁移由独立的
备份/恢复功能承担，不在本次范围。tools/logan/out/ 下旧抓包保留为历史事实。
```

## 5. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 隐藏的 R8 keep 规则按 FQN 写死 | 全量 grep `*.pro / consumer-rules.pro` 包含 `com.zili`，纳入字符串替换；release 构建做一次冒烟 |
| Hilt 生成代码 / kapt 缓存残留 | 改名后强制 `./gradlew clean` 再构建 |
| 文档里 `com.zili` 作为示例文本同时被替换 | 这是预期行为（"全仓 grep 0 命中"是收尾验收门） |
| sed macOS / GNU 差异 | 仅 macOS 跑，用 `sed -i ''` 显式空备份后缀 |
| 过度匹配 `com.zili.android.musicfreeandroid.<suffix>` | 安全：仓库里没有非本项目的 `com.zili.*` 引用；从最长前缀替换天然带过 `.debug` 等后缀 |
| logan 拓包出现旧 FQN | 显式保留（§1.2） |

## 6. 验收

### 6.1 失败回滚

- 验收失败：worktree 直接丢弃，main 不受影响
- 合并后才发现问题：`git revert <squash commit sha>`

### 6.2 最终验收 checklist

- [ ] `git grep -F 'com.zili.android.musicfreeandroid' -- ':!tools/logan/out/**'` 输出为空
- [ ] `./gradlew clean :app:assembleDebug` 通过
- [ ] `./gradlew test` 通过（contract 测试是首要 gate）
- [ ] `bash scripts/dev-harness/check.sh` 通过
- [ ] `./gradlew :app:assembleRelease` 通过（R8 / 反射保留验证）
- [ ] 模拟器装 debug 包，应用启动 + 搜索（需先手动安装插件）→ 播放一首歌 OK
- [ ] Logan 日志中 `applicationId=com.hank.musicfree.debug`

## 7. 决策记录

| 项 | 决定 | 理由 |
|---|---|---|
| 老用户数据 | 不迁移 | 备份/恢复功能独立开发，按全新应用 ship |
| `versionCode / versionName` | 不重置 | version 跟踪代码成熟度，独立于应用身份 |
| 历史 plans/specs/dump | 重写为 `com.hank.musicfree` | 仓库 grep 必须 0 命中 |
| Logan 拓包 | 保留 | 历史事实，溯源用 |
| 执行方式 | worktree + 脚本 big-bang | 可重放、单 squash commit、覆盖面可 grep 验证 |
| 命名结构 | 严格前缀替换 | 不顺手做其他重命名 |
