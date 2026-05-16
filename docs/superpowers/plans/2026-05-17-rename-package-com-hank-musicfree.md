# 应用包名重命名为 com.hank.musicfree 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `com.zili.android.musicfreeandroid` 全量替换为 `com.hank.musicfree`，覆盖 `applicationId`、13 个 module `namespace`、源码目录、活的代码/配置、历史 plans/specs/dump；保留 `tools/logan/out/` 旧抓包。一次性 big-bang，单 squash commit 合到 main。

**Architecture:** 在 `.worktrees/rename-package` worktree 分支顺序执行——先 `git mv` 物理移动源码目录，再批量字符串前缀替换，最后用现有 contract 测试 + clean debug build + dev-harness check 作为验收门。

**Tech Stack:** Bash + `git mv` + `git ls-files -z | xargs -0 perl -pi -e`（macOS/Linux 通用）+ `./gradlew` + `bash scripts/dev-harness/check.sh`。

**Spec:** `docs/superpowers/specs/2026-05-17-rename-package-com-hank-musicfree-design.md`

---

## Pre-flight 假设

- `git status` 是 clean（`gradle.properties` 的 unstaged 改动与本任务无关，**不要**把它带进重命名 commit）
- macOS 默认 Bash + perl 可用
- `.gitignore` 已经覆盖 `tools/logan/out/` 与 `**/build/`，故 `git ls-files` 自动排除它们
- `AGENTS.md` 是文件，`CLAUDE.md` 是软链 `AGENTS.md`，只需改一次 `AGENTS.md`
- `app/proguard-rules.pro` 不含 `com.zili`，无需操心 R8 keep 规则改动

---

## Task 0: 创建 worktree 分支

**Files:**
- Create worktree at: `.worktrees/rename-package` (branch `rename-package`)

- [ ] **Step 1: 确认主分支 clean（忽略 gradle.properties 这一项无关改动）**

Run:
```bash
git status -s
```
Expected: 只能看到 ` M gradle.properties`，否则停下问用户。

- [ ] **Step 2: 创建 worktree 与新分支**

Run:
```bash
git worktree add -b rename-package .worktrees/rename-package main
```
Expected: `Preparing worktree (new branch 'rename-package')` + `HEAD is now at ...`。

- [ ] **Step 3: 进入 worktree 目录**

Run:
```bash
cd .worktrees/rename-package
pwd
```
Expected: 路径以 `.worktrees/rename-package` 结尾。

- [ ] **Step 4: 记录 baseline 计数**

Run:
```bash
git grep -F 'com.zili.android.musicfreeandroid' | wc -l
```
Expected: 输出一个正整数（spec 调研时约 759）。把这个数字记下来作为"待清零"目标。

> **下面所有任务都在 `.worktrees/rename-package` 内执行。**

---

## Task 1: 物理移动 31 个源码目录

**Files:**
- Move: 13 module × {main, test, androidTest 仅 5 模块有} = 31 个目录
- 目录树：`<module>/src/<srcSet>/java/com/zili/android/musicfreeandroid` → `<module>/src/<srcSet>/java/com/hank/musicfree`

- [ ] **Step 1: 写一段循环把 31 个目录全部 `git mv` 过去**

Run（直接粘贴到 zsh/bash）：
```bash
set -euo pipefail
modules_with_androidTest=(app core data player plugin)
modules_main_test_only=(downloader updater logging feature/home feature/player-ui feature/search feature/settings feature/listen-stats)
for m in "${modules_with_androidTest[@]}"; do
  for s in main test androidTest; do
    src="$m/src/$s/java/com/zili/android/musicfreeandroid"
    dst="$m/src/$s/java/com/hank/musicfree"
    mkdir -p "$m/src/$s/java/com/hank"
    git mv "$src" "$dst"
  done
done
for m in "${modules_main_test_only[@]}"; do
  for s in main test; do
    src="$m/src/$s/java/com/zili/android/musicfreeandroid"
    dst="$m/src/$s/java/com/hank/musicfree"
    mkdir -p "$m/src/$s/java/com/hank"
    git mv "$src" "$dst"
  done
done
```
Expected: 静默成功；如果某个 `git mv` 失败立即停止。

- [ ] **Step 2: 清理空的旧目录链（`com/zili/android/musicfreeandroid` 的祖先目录）**

Run:
```bash
find . -type d -path '*/java/com/zili/android/musicfreeandroid' -empty -delete 2>/dev/null || true
find . -type d -path '*/java/com/zili/android' -empty -delete 2>/dev/null || true
find . -type d -path '*/java/com/zili' -empty -delete 2>/dev/null || true
```
Expected: 静默；没有遗留的空 `com/zili` 目录。

- [ ] **Step 3: 核校目录结构**

Run:
```bash
find . -type d -path '*/java/com/zili' 2>/dev/null | grep -v '/.worktrees/' || echo OK
find . -type d -path '*/java/com/hank/musicfree' 2>/dev/null | grep -v '/.worktrees/' | wc -l
```
Expected: 第一条输出 `OK`；第二条输出 `31`。

- [ ] **Step 4: 局部 commit（便于审查）**

Run:
```bash
git add -A
git commit -m 'refactor(rename): git mv com/zili/android/musicfreeandroid -> com/hank/musicfree'
```
Expected: 31 个 directory renames，0 文件内容改动。

---

## Task 2: 重写 13 个模块 build.gradle.kts 的 namespace

**Files:**
- Modify: `app/build.gradle.kts`、`core/build.gradle.kts`、`data/build.gradle.kts`、`player/build.gradle.kts`、`plugin/build.gradle.kts`、`downloader/build.gradle.kts`、`updater/build.gradle.kts`、`logging/build.gradle.kts`、`feature/home/build.gradle.kts`、`feature/player-ui/build.gradle.kts`、`feature/search/build.gradle.kts`、`feature/settings/build.gradle.kts`、`feature/listen-stats/build.gradle.kts`

- [ ] **Step 1: 批量替换所有 build.gradle.kts 中的 namespace 字符串**

Run:
```bash
perl -pi -e 's/\bcom\.zili\.android\.musicfreeandroid\b/com.hank.musicfree/g' \
  app/build.gradle.kts \
  core/build.gradle.kts \
  data/build.gradle.kts \
  player/build.gradle.kts \
  plugin/build.gradle.kts \
  downloader/build.gradle.kts \
  updater/build.gradle.kts \
  logging/build.gradle.kts \
  feature/home/build.gradle.kts \
  feature/player-ui/build.gradle.kts \
  feature/search/build.gradle.kts \
  feature/settings/build.gradle.kts \
  feature/listen-stats/build.gradle.kts
```
Expected: 静默成功。

- [ ] **Step 2: 验证 13 个文件都改了**

Run:
```bash
git grep -F 'com.hank.musicfree' -- '*build.gradle.kts' | wc -l
git grep -F 'com.zili.android.musicfreeandroid' -- '*build.gradle.kts' | wc -l
```
Expected: 第一条 ≥ 13；第二条 = 0。

- [ ] **Step 3: 抽查 app/build.gradle.kts**

Run:
```bash
grep -n 'namespace\|applicationId\|testInstrumentationRunner' app/build.gradle.kts
```
Expected: 看到 `namespace = "com.hank.musicfree"`、`applicationId = "com.hank.musicfree"`、`testInstrumentationRunner = "com.hank.musicfree.HiltTestRunner"` 三行（applicationId 与 testInstrumentationRunner 由本次替换顺带改了，因为前缀串完全包含它们）。

> 如果 `applicationId` 或 `testInstrumentationRunner` 没改，说明字符串前缀替换未覆盖该处——直接打开 `app/build.gradle.kts` 手工修正，然后再回到 Step 2 验证。

---

## Task 3: 批量字符串前缀替换（全量、跟踪文件）

**Files:**
- Modify: 所有 `git ls-files` 跟踪的文件（自动跳过 `tools/logan/out/`、`build/`、`.worktrees/` 等 gitignored 路径）

- [ ] **Step 1: 用 perl in-place 批量替换**

Run（在 worktree 根目录执行）：
```bash
git ls-files -z | xargs -0 perl -pi -e 's/\bcom\.zili\.android\.musicfreeandroid\b/com.hank.musicfree/g'
```
Expected: 静默成功。注意 perl `\b` 词边界保证不会过度匹配（仓库内不存在 `com.zili.android.musicfreeandroidX` 这种粘连串）。

- [ ] **Step 2: 全仓 grep 应该已 0 命中**

Run:
```bash
git grep -F 'com.zili.android.musicfreeandroid' | wc -l
```
Expected: `0`。

- [ ] **Step 3: 抽查热点文件——contract 测试**

Run:
```bash
grep -n 'com/hank/musicfree\|com.hank.musicfree' \
  app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt \
  app/src/test/java/com/hank/musicfree/navigation/NavigationMinifyContractTest.kt \
  app/src/test/java/com/hank/musicfree/navigation/PlaybackNavigationContractTest.kt \
  app/src/test/java/com/hank/musicfree/navigation/AppNavHostRouteContractTest.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/RnPluginOracleContractTest.kt
```
Expected: 每个文件都有 `com/hank/musicfree/...` 形式的相对路径字符串。如果路径还指向 `com/zili/...`，说明这些字符串是 hard-coded 拼接而非前缀串——返回 Step 1 排查原因。

- [ ] **Step 4: 抽查热点文件——maestro / scripts / .claude settings / capture-homepage**

Run:
```bash
grep -n 'appId\|APP_ID\|EXPECTED_PACKAGE' \
  maestro/flows/smoke/core/01_launch_default_plugins.yaml \
  maestro/flows/smoke/core/02_search_play.yaml \
  maestro/flows/smoke/core/03_settings_feedback_logs.yaml \
  scripts/maestro/run-smoke.sh \
  scripts/parity-audit/install-both.sh \
  tools/home-fidelity/capture-homepage-android.sh
grep -n 'com.hank.musicfree' .claude/settings.local.json
```
Expected: 全部出现 `com.hank.musicfree` / `com.hank.musicfree.debug`，没有 `com.zili`。

- [ ] **Step 5: 抽查 AGENTS.md / 当前规范**

Run:
```bash
git grep -F 'com.hank.musicfree' AGENTS.md docs/dev-harness/ui/rules.md | head
```
Expected: 看到至少 1 条命中（AGENTS.md 中可能并不直接出现，但 `docs/dev-harness/ui/rules.md` 应有 `com.hank.musicfree.core.ui.MusicFreeScreenScaffold` 这样的引用）。

- [ ] **Step 6: 局部 commit**

Run:
```bash
git add -A
git commit -m 'refactor(rename): com.zili.android.musicfreeandroid -> com.hank.musicfree across tracked files'
```
Expected: 一个大 commit，diff 集中在前缀替换。

---

## Task 4: 手工核校 + 修正残留

**Files:**
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: 各模块 `AndroidManifest.xml`
- Inspect: `.claude/settings.local.json`

- [ ] **Step 1: 全仓最终 grep 必须 0**

Run:
```bash
git grep -F 'com.zili.android.musicfreeandroid'
echo "exit=$?"
```
Expected: 无输出，`exit=1`（grep 找不到返回 1）。如果有残留：定位文件，打开手改，重 commit。

- [ ] **Step 2: 检查 AndroidManifest.xml 是否有需要硬编码的包**

Run:
```bash
find . -name AndroidManifest.xml -not -path '*/build/*' -not -path './.worktrees/*' -exec grep -lH 'com.zili\|com.hank' {} \;
```
Expected: 若有命中，逐个打开核对——绝大多数 manifest 用 `${applicationId}` 占位符不应出现硬编码包名；如果你看到硬编码的 `com.hank.musicfree.*`（不是 `${applicationId}.*`），那是被前缀替换"对了"，可以保留。

- [ ] **Step 3: 检查 `.claude/settings.local.json` 的 gradle 命令是否正确**

Run:
```bash
grep -n 'gradlew' .claude/settings.local.json | head -20
```
Expected: 任何 `:app:testDebugUnitTest --tests "com.zili..."` 都已变成 `com.hank.musicfree...`。

- [ ] **Step 4: 检查 docs/dev-harness/INDEX.md 是否清洁**

Run:
```bash
grep -rn 'com.zili' docs/dev-harness/ 2>/dev/null
```
Expected: 无输出。

- [ ] **Step 5: 如果 Step 1–4 都干净，跳过；否则手改后 commit**

Run（仅在有残留时）：
```bash
git add -A
git commit -m 'refactor(rename): clean up residual com.zili refs in <area>'
```

---

## Task 5: 验证 Debug 构建

**Files:**
- 触发：`./gradlew clean :app:assembleDebug`

- [ ] **Step 1: clean 出 build 缓存（避免 Hilt/kapt 缓存残留）**

Run:
```bash
./gradlew clean
```
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 构建 Debug APK**

Run:
```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`。如果失败：通常错误集中在某个 Kotlin 文件还有旧 import（前缀替换会覆盖 `import com.zili.android.musicfreeandroid.*`，但若有 import 引到非项目的 `com.zili.*` 第三方库，需要手工恢复）。

- [ ] **Step 3: 确认产物 applicationId**

Run:
```bash
ls app/build/outputs/apk/debug/ 2>/dev/null
aapt2 dump packagename app/build/outputs/apk/debug/*.apk 2>/dev/null || aapt dump badging app/build/outputs/apk/debug/*.apk 2>/dev/null | grep package:
```
Expected: 输出 `com.hank.musicfree.debug`。`aapt2` 没装时退到 `aapt`，仍找不到时跳过此步。

---

## Task 6: 跑单元测试（contract 测试是首要 gate）

**Files:**
- 触发：`./gradlew test`

- [ ] **Step 1: 跑所有 module 的 unit test**

Run:
```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL`。

特别关注：
- `SplashScreenResourceContractTest`、`NavigationMinifyContractTest`、`PlaybackNavigationContractTest`、`AppNavHostRouteContractTest`、`RnPluginOracleContractTest` 都按相对路径读源码——它们必须能找到新路径下的文件。
- 任何失败如果是"找不到文件 com/zili/..."，说明 Task 3 字符串替换漏了一处硬编码——回 Task 3 Step 3 修正。

---

## Task 7: 跑 dev-harness check + lint

**Files:**
- 触发：`bash scripts/dev-harness/check.sh`、`./gradlew lint`

- [ ] **Step 1: dev-harness 守门**

Run:
```bash
bash scripts/dev-harness/check.sh
```
Expected: 全绿。失败一般指向 `docs/dev-harness/ui/rules.md` 之类规则文件里旧 FQN 与代码不匹配——回 Task 4 修正。

- [ ] **Step 2: lint（提示项可接受）**

Run:
```bash
./gradlew lint
```
Expected: `BUILD SUCCESSFUL`，无 lint ERROR。WARN 可以接受。

---

## Task 8: Release 构建（R8 验证）

**Files:**
- 触发：`./gradlew :app:assembleRelease`

- [ ] **Step 1: Release 构建跑通**

Run:
```bash
./gradlew :app:assembleRelease
```
Expected: `BUILD SUCCESSFUL`。

> 如果环境缺签名变量但本地确实需要验收 R8，可以临时让 release 走 debug 签名（按 `RELEASE.md` 配置）。如果失败原因是签名缺失而不是 R8/反射问题，可以暂时跳过 Step 2 但**必须在 commit message 中注明**。

- [ ] **Step 2: 抽查 mapping.txt 不再含旧包名**

Run:
```bash
grep -F 'com.zili.android.musicfreeandroid' app/build/outputs/mapping/release/mapping.txt 2>/dev/null | head -3 || echo 'mapping clean or absent'
```
Expected: `mapping clean or absent`。若有命中，说明运行时仍有 FQN 字符串残留——回 Task 4。

---

## Task 9: 运行态验收

**Files:**
- 触发：模拟器 / 真机

- [ ] **Step 1: 安装 Debug APK 到当前模拟器**

Run:
```bash
./gradlew :app:installDebug
```
Expected: `BUILD SUCCESSFUL`，`adb shell pm list packages | grep hank` 看到 `package:com.hank.musicfree.debug`。

- [ ] **Step 2: 启动应用并冒烟**

操作：从 launcher 点开新图标（applicationId 变了，launcher 上会"多"一个图标，老应用图标仍在），完成下列动作：
1. 应用启动到首页，不闪退
2. 默认插件 bootstrap：进入"插件管理"页面，列表非空
3. 进入搜索页搜一个关键词，能出结果
4. 点结果，能播放一首歌
5. 退出再进入，状态正常

如果以上任何一步失败：用 `adb logcat | grep -i 'AndroidRuntime\|musicfree'` 排查；通常是 R8 keep 规则没覆盖某个反射点，回 Task 4 / 8 修复。

- [ ] **Step 3: 校核 Logan 日志中的 applicationId**

操作：在应用的"设置 → 反馈"导出一份 Logan 包，解压后查看 `manifest.json`：
```bash
# 假设导出到 ~/Downloads/mf-feedback-xxx.zip
unzip -p ~/Downloads/mf-feedback-*.zip manifest.json | head -5
```
Expected: `"applicationId":"com.hank.musicfree.debug"`。

- [ ] **Step 4: 卸载（清理环境）**

Run:
```bash
adb uninstall com.hank.musicfree.debug
```
Expected: `Success`。

---

## Task 10: 合并到 main

**Files:**
- main 分支收一个 squash commit

- [ ] **Step 1: 在 worktree 内确认 commit 历史**

Run（仍在 `.worktrees/rename-package/` 内）：
```bash
git log --oneline main..HEAD
```
Expected: 看到 Task 1/3/(4) 的几个中间 commit。

- [ ] **Step 2: 回到主仓库工作目录**

Run:
```bash
cd ../..
pwd
```
Expected: 路径回到主仓库根。

- [ ] **Step 3: 在主仓库 squash 合并 rename-package 分支**

Run:
```bash
git merge --squash rename-package
```
Expected: 工作区被填上重命名差异，`git status` 显示大量 `renamed` + 修改。

- [ ] **Step 4: 创建合并 commit（中文 conventional commits）**

Run:
```bash
git commit -m "$(cat <<'EOF'
refactor(app): 应用包名重命名为 com.hank.musicfree

把 applicationId 与 13 模块 namespace 从 com.zili.android.musicfreeandroid
迁移到 com.hank.musicfree；源码目录从 com/zili/android/musicfreeandroid 重排到
com/hank/musicfree；contract 测试、maestro flows、scripts、.claude allowlist、
当前规范与历史 plans/specs/uiautomator dump 一并改名。

按"全新应用"发布，老用户数据迁移由独立的备份/恢复功能承担，不在本次范围。
tools/logan/out/ 下旧抓包保留为历史事实。

设计：docs/superpowers/specs/2026-05-17-rename-package-com-hank-musicfree-design.md
计划：docs/superpowers/plans/2026-05-17-rename-package-com-hank-musicfree.md
EOF
)"
```
Expected: 单个 squash commit。

- [ ] **Step 5: 删除 worktree 与分支**

Run:
```bash
git worktree remove .worktrees/rename-package
git branch -D rename-package
```
Expected: `.worktrees/rename-package` 被清除，分支被删除。

- [ ] **Step 6: 主仓库最终 sanity check**

Run:
```bash
git grep -F 'com.zili.android.musicfreeandroid' | wc -l
./gradlew :app:assembleDebug
```
Expected: 第一条 `0`；第二条 `BUILD SUCCESSFUL`。

---

## 完成判据（与 spec §6.2 对齐）

- [x] `git grep -F 'com.zili.android.musicfreeandroid'` 0 命中（Task 4 / Task 10 Step 6）
- [x] `./gradlew clean :app:assembleDebug` 通过（Task 5）
- [x] `./gradlew test` 通过（Task 6）
- [x] `bash scripts/dev-harness/check.sh` 通过（Task 7）
- [x] `./gradlew :app:assembleRelease` 通过，mapping.txt 干净（Task 8）
- [x] 模拟器装包 + 启动 + 搜索 → 播放 OK（Task 9）
- [x] Logan manifest `applicationId=com.hank.musicfree.debug`（Task 9 Step 3）

---

## 失败回滚

- 任何 Task 1–9 失败：直接丢弃 worktree（`git worktree remove --force .worktrees/rename-package && git branch -D rename-package`），main 不受影响。
- Task 10 合并后才发现问题：`git revert <squash commit sha>`。
