# 发布品牌化与 CI 改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把桌面显示名落为 `MF音乐` / `MF音乐(D)`，Debug 包加红 D 角标；删除 `android-debug-apk.yml` 与 `dev-harness-gate.yml`，同步改写 5+1 处文档引用；Release workflow 增加按北京 02:00 触发的 nightly path（24h 内 main 有新提交才构建）。

**Architecture:** App 显示名用 `buildTypes.resValue` 单点注入；Debug 图标用 `app/src/debug/res/` 源集覆盖 adaptive icon（layer-list 叠加矢量徽章，零额外 webp）；Release workflow 用 step output + `if:` 守卫实现"无新提交时整 job 报 success 但跳过下游"。

**Tech Stack:** Android Gradle Plugin 9.2 / Kotlin 2.3 / adaptive icon (Android 8+) / GitHub Actions schedule cron.

**Spec 来源**：`docs/superpowers/specs/2026-05-13-release-rebrand-and-ci-design.md`（commit `af509b6`）。

---

## Task 0: Worktree 准备

**Files:** N/A（仓库层操作）

- [ ] **Step 1: 创建 worktree**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git worktree add .worktrees/release-rebrand-ci -b feat/release-rebrand-ci main
```

预期：新 worktree 路径 `.worktrees/release-rebrand-ci`，分支 `feat/release-rebrand-ci` 基于 main。

- [ ] **Step 2: 切到 worktree 并校验**

```bash
cd .worktrees/release-rebrand-ci
git status
git log -1 --oneline
```

预期：`git status` clean，`git log` 指向 main HEAD（spec commit `af509b6` 已在 main 上）。

后续所有 Task 在 `.worktrees/release-rebrand-ci` 目录内执行。

---

## Task 1: App 显示名（resValue 注入）

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/build.gradle.kts`（`buildTypes` 块）

- [ ] **Step 1: 删除 strings.xml 中的 app_name**

`app/src/main/res/values/strings.xml` 编辑后内容：

```xml
<resources>
</resources>
```

（删除原 `<string name="app_name">MusicFreeAndroid</string>` 行。文件留空 `<resources/>` 节点，避免 Android resource merge 报"找不到 strings.xml"。）

- [ ] **Step 2: 在 build.gradle.kts 注入 resValue**

编辑 `app/build.gradle.kts` 中现有的 `buildTypes` 块。当前内容：

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
    }
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        if (releaseSigningRequested) {
            buildConfigField(
                "String",
                "LOGAN_AES_KEY",
                quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_KEY")),
            )
            buildConfigField(
                "String",
                "LOGAN_AES_IV",
                quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_IV")),
            )
        }
    }
}
```

改为：

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        resValue("string", "app_name", "MF音乐(D)")
    }
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        resValue("string", "app_name", "MF音乐")
        if (releaseSigningRequested) {
            buildConfigField(
                "String",
                "LOGAN_AES_KEY",
                quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_KEY")),
            )
            buildConfigField(
                "String",
                "LOGAN_AES_IV",
                quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_IV")),
            )
        }
    }
}
```

- [ ] **Step 3: 构建 Debug APK**

```bash
./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL，无 `Duplicate resources` / `app_name` 报错。

- [ ] **Step 4: 用 aapt 校验显示名**

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
BT=$(ls -1d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BT/aapt" dump badging "$APK" | grep -E "application-label|^package:"
```

预期输出包含：

```
package: name='com.zili.android.musicfreeandroid.debug' ...
application-label:'MF音乐(D)'
```

若 `ANDROID_HOME` 未设置，用 `~/Library/Android/sdk` 替代。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/build.gradle.kts
git commit -m "feat(app): 用 resValue 注入显示名 MF音乐 / MF音乐(D)"
```

---

## Task 2: Debug 图标徽章

**Files:**
- Create: `app/src/debug/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/debug/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/debug/res/drawable/ic_launcher_foreground_debug.xml`

- [ ] **Step 1: 创建 debug 源集目录**

```bash
mkdir -p app/src/debug/res/mipmap-anydpi-v26 app/src/debug/res/drawable
```

- [ ] **Step 2: 写 ic_launcher.xml（覆盖 adaptive icon）**

`app/src/debug/res/mipmap-anydpi-v26/ic_launcher.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground_debug"/>
</adaptive-icon>
```

- [ ] **Step 3: 写 ic_launcher_round.xml（同上，shape 蒙版由系统 launcher 端施加）**

`app/src/debug/res/mipmap-anydpi-v26/ic_launcher_round.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground_debug"/>
</adaptive-icon>
```

- [ ] **Step 4: 写 layer-list foreground**

`app/src/debug/res/drawable/ic_launcher_foreground_debug.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@mipmap/ic_launcher_foreground"/>
    <item
        android:width="36dp"
        android:height="36dp"
        android:gravity="bottom|right"
        android:right="20dp"
        android:bottom="20dp">
        <vector
            android:width="36dp" android:height="36dp"
            android:viewportWidth="36" android:viewportHeight="36">
            <path
                android:fillColor="#E53935"
                android:pathData="M18,2 A16,16 0 1 1 17.99,2 Z"/>
            <path
                android:fillColor="#FFFFFF"
                android:pathData="M11,9 L19,9 A8,9 0 0 1 19,27 L11,27 Z M15,13 L15,23 L19,23 A5,5 0 0 0 19,13 Z"/>
        </vector>
    </item>
</layer-list>
```

- [ ] **Step 5: 重新构建 Debug APK**

```bash
./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL；Gradle 资源 merge log 显示 debug 源集的 `ic_launcher.xml` 覆盖了 main 源集。

- [ ] **Step 6: 用 aapt 抽包确认 mipmap 文件被替换**

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
BT=$(ls -1d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BT/aapt" dump xmltree "$APK" res/mipmap-anydpi-v26/ic_launcher.xml
```

预期：输出的 `<foreground>` 节点 `drawable` 属性值指向 `@drawable/ic_launcher_foreground_debug`（不是 main 源集里的 `@mipmap/ic_launcher_foreground`）。

- [ ] **Step 7: 运行态验收（模拟器/真机）**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.zili.android.musicfreeandroid.debug -c android.intent.category.LAUNCHER 1
```

人工目检桌面：标签为 `MF音乐(D)`，图标右下角红圆 D 徽章可见。若设备为 Pixel-style 圆形蒙版与 MIUI/EMUI 水滴蒙版各检一次。截图保留作为 PR 证据。

- [ ] **Step 8: Commit**

```bash
git add app/src/debug/
git commit -m "feat(app): Debug 包加红色 D 角标 (adaptive icon layer-list)"
```

---

## Task 3: 删除 Debug APK / Harness 门禁 workflow + 同步文档（原子提交）

> Spec §3.3.2 明确要求 workflow 删除与文档改写在**同一 commit / PR**。本 Task 全部步骤完成后再统一 commit。

**Files:**
- Delete: `.github/workflows/android-debug-apk.yml`
- Delete: `.github/workflows/dev-harness-gate.yml`
- Modify: `AGENTS.md`（第 36 行）
- Modify: `CLAUDE.md`（第 36 行）
- Modify: `docs/dev-harness/INDEX.md`（第 32 行）
- Modify: `docs/dev-harness/test/rules.md`（第 79 行）
- Modify: `docs/dev-harness/test/incidents.md`（INC-2026-0016 guard 块 + 末段备注）
- Modify: `scripts/dev-harness/check.sh`（第 52 行附近注释）

- [ ] **Step 1: 删除两个 workflow 文件**

```bash
git rm .github/workflows/android-debug-apk.yml .github/workflows/dev-harness-gate.yml
```

- [ ] **Step 2: 改写 AGENTS.md 第 36 行**

把：

```
违反 rules.md 中标记 MUST / MUST NOT 的条款将在 CI `dev-harness-gate` 作业被拦下。
```

改为：

```
违反 rules.md 中标记 MUST / MUST NOT 的条款由人工 review 拦截；本地可跑 `bash scripts/dev-harness/check.sh` 自查。
```

- [ ] **Step 3: 改写 CLAUDE.md 第 36 行**

`CLAUDE.md` 与 `AGENTS.md` 这段文字一致。同样改写为：

```
违反 rules.md 中标记 MUST / MUST NOT 的条款由人工 review 拦截；本地可跑 `bash scripts/dev-harness/check.sh` 自查。
```

（如 `CLAUDE.md` 是指向 `AGENTS.md` 的 symlink，则跳过本步——以 `ls -l CLAUDE.md` 输出为准。）

- [ ] **Step 4: 改写 docs/dev-harness/INDEX.md 第 32 行**

定位 `- 工作流：\`.github/workflows/dev-harness-gate.yml\`` 行，**整行删除**。如该行所在小节因此变空，检查并清掉孤立空标题。

- [ ] **Step 5: 改写 docs/dev-harness/test/rules.md 第 79 行**

把：

```
- `dev-harness-gate.yml` MUST 含一个 "Compile-only test sources (all modules)" 步骤跑 `:<each-module>:compileDebugUnitTestKotlin`，作为 fixture lag 的 PR 守门。新加模块时 MUST 同步加入这步。
```

改为：

```
- PR 合入前 MUST 在本地跑 `bash scripts/dev-harness/check.sh`（默认含编译全模块测试源步骤）；新加模块时 MUST 同步加入 `scripts/dev-harness/check.sh` 的 `:<each-module>:compileDebugUnitTestKotlin` 模块列表。
```

- [ ] **Step 6: 改写 docs/dev-harness/test/incidents.md 中 INC-2026-0016 的 guard 块**

定位（第 14-17 行附近）：

```yaml
- guard:
    type: ci-step
    target: .github/workflows/dev-harness-gate.yml (Compile-only test sources step)
```

改为：

```yaml
- guard:
    type: manual
    target: bash scripts/dev-harness/check.sh (Compile-only test sources step)
```

再定位同文件第 33 行附近的备注段：

```
guard 类型 `ci-step`：在 `.github/workflows/dev-harness-gate.yml` 的 "Compile-only test sources (all modules)" 步抓；本地复现见 `bash scripts/dev-harness/check.sh`（默认步骤包含编译全模块测试源）。
```

改为：

```
guard 类型 `manual`：PR 合入前 MUST 在本地跑 `bash scripts/dev-harness/check.sh`（默认步骤包含编译全模块测试源）；CI 不再自动兜底。
```

- [ ] **Step 7: 改写 scripts/dev-harness/check.sh 第 52 行附近注释**

定位：

```
# Only invoke modules that currently host harness/contracts/ tests.
# Adding tests in other modules requires extending this list AND the CI workflow.
```

改为：

```
# Only invoke modules that currently host harness/contracts/ tests.
# Adding tests in other modules requires extending this list.
```

（删掉 `AND the CI workflow` 那段，因为 CI workflow 已不存在。）

- [ ] **Step 8: 全仓再次 grep 验证无残留**

```bash
grep -rn "dev-harness-gate" --exclude-dir=.git --exclude-dir=.worktrees --exclude-dir=build
```

预期输出**只剩 spec / plan 自身的历史引用**（`docs/superpowers/specs/2026-05-13-release-rebrand-and-ci-design.md`、`docs/superpowers/plans/2026-05-13-release-rebrand-and-ci.md`）。AGENTS / CLAUDE / docs/dev-harness/ / scripts/ / .github/ 下不应再出现该字符串。

如果还能 grep 到，回看 Step 2-7，对照定位漏改。

- [ ] **Step 9: 本地 check.sh 通过**

```bash
bash scripts/dev-harness/check.sh
```

预期：symlinks 通过、grep guards 通过、全模块 `compileDebugUnitTestKotlin` 通过、contract tests 通过。脚本以 exit code 0 结束。这一步既是 §3.3.2 "降级后唯一兜底路径"的 smoke，也是确认 Step 7 注释改动没有破坏脚本。

- [ ] **Step 10: Commit（原子）**

```bash
git add .github/workflows/ AGENTS.md CLAUDE.md docs/dev-harness/ scripts/dev-harness/check.sh
git status   # 复检：只有上面 Files 列表里的文件被改动；无意外文件入场
git commit -m "ci: 删除 dev-harness-gate 与 debug APK workflow 并同步降级 harness 文档至本地 check.sh"
```

---

## Task 4: Release workflow 改造（schedule + nightly guard + APK 命名）

**Files:**
- Modify: `.github/workflows/android-release-apk.yml`

- [ ] **Step 1: 扩展 `on:` 触发器**

定位 `android-release-apk.yml` 文件开头：

```yaml
on:
  push:
    tags:
      - "v*"
  workflow_dispatch:
```

改为：

```yaml
on:
  push:
    tags:
      - "v*"
  schedule:
    - cron: "0 18 * * *"   # 18:00 UTC == 北京时间次日 02:00
  workflow_dispatch:
```

- [ ] **Step 2: Checkout step 加 fetch-depth: 0**

定位：

```yaml
      - name: Checkout
        uses: actions/checkout@v6
```

改为：

```yaml
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
```

- [ ] **Step 3: 在 Checkout 后插入 Nightly guard step**

在 `Checkout` 与 `Set up JDK 21` 之间插入：

```yaml
      - name: Check for new commits (nightly only)
        id: nightly-guard
        if: github.event_name == 'schedule'
        run: |
          new_commits=$(git log --since="24 hours ago" --oneline origin/main | wc -l)
          echo "new_commits=$new_commits" >> "$GITHUB_OUTPUT"
          if [ "$new_commits" -eq 0 ]; then
            echo "::notice::No new commits in last 24h on main; skipping nightly build."
          fi
```

- [ ] **Step 4: 给下游 5 个 step 加 if 守卫**

下面 5 个 step 都在顶层 keys 处加入 `if:`（保留原有 `name:` / `id:` / `env:` / `run:` / `uses:` / `with:` 不变）。守卫表达式：

```yaml
if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
```

需要加 `if:` 的 step（按文件出现顺序）：

1. `Set up JDK 21`
2. `Set up Gradle`
3. `Set up Android SDK`
4. `Validate release secrets`
5. `Decode release keystore`
6. `Build Release APK`
7. `Name APK`（id 为 `name-apk`）
8. `Upload Release APK artifact`

> 注：spec §3.3.3 列了"Set up JDK 起" 5 个 step，但实际从 `Set up JDK 21` 起一共 8 个 step 都属于 nightly 路径下应跳过的。守卫加全，避免 schedule 空跑时还浪费 setup 时间。

- [ ] **Step 5: 重写 Name APK 命名脚本**

定位 `Name APK` step 现有 `run` 内容：

```bash
if [ "$GITHUB_REF_TYPE" = "tag" ]; then
  apk_name="MusicFreeAndroid-${GITHUB_REF_NAME}.apk"
else
  apk_name="MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}.apk"
fi
cp app/build/outputs/apk/release/app-release.apk "$RUNNER_TEMP/$apk_name"
echo "apk_name=$apk_name" >> "$GITHUB_OUTPUT"
```

改为：

```bash
if [ "$GITHUB_REF_TYPE" = "tag" ]; then
  apk_name="MusicFreeAndroid-${GITHUB_REF_NAME}.apk"
elif [ "$GITHUB_EVENT_NAME" = "schedule" ]; then
  apk_name="MusicFreeAndroid-nightly-$(date -u +%Y%m%d)-$(git rev-parse --short HEAD).apk"
else
  apk_name="MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}.apk"
fi
cp app/build/outputs/apk/release/app-release.apk "$RUNNER_TEMP/$apk_name"
echo "apk_name=$apk_name" >> "$GITHUB_OUTPUT"
```

- [ ] **Step 6: 本地 YAML 语法 lint**

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android-release-apk.yml')); print('YAML OK')"
```

预期输出：`YAML OK`。

如果本地装有 `actionlint`，额外跑：

```bash
actionlint .github/workflows/android-release-apk.yml
```

预期：无 error 输出。

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/android-release-apk.yml
git commit -m "ci(release): 增加 02:00 北京时间 nightly 触发与 24h 新提交守卫"
```

---

## Task 5: 集成验收（push 到远端，跑实际 workflow）

> 这一步必须发生在 push 之后；不在 worktree 本地完成。

- [ ] **Step 1: 推送 worktree 分支**

```bash
git push -u origin feat/release-rebrand-ci
```

- [ ] **Step 2: 用 workflow_dispatch 手动触发 release workflow**

```bash
gh workflow run "Android Release APK" --ref feat/release-rebrand-ci
sleep 5
gh run list --workflow="Android Release APK" --limit 1
RUN_ID=$(gh run list --workflow="Android Release APK" --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID"
```

预期：run 完整跑通；artifact 命名为 `MusicFreeAndroid-manual-<run_number>.apk`；**不**触发 `publish-github-release` job（因 `event_name != 'push'`）。

- [ ] **Step 3: 下载 manual artifact 并 aapt 校验**

```bash
gh run download "$RUN_ID" -n MusicFreeAndroid-release-apk -D /tmp/release-check
ls /tmp/release-check
BT=$(ls -1d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BT/aapt" dump badging /tmp/release-check/MusicFreeAndroid-manual-*.apk | grep -E "application-label|^package:"
```

预期：

```
package: name='com.zili.android.musicfreeandroid' ...
application-label:'MF音乐'
```

- [ ] **Step 4: 模拟 schedule 路径——临时改 cron 到近期时间**

> 注意：GitHub Actions schedule 实际有 5-15 分钟延迟，需把目标 cron 时间提前安排到当前 UTC 时间 +15 分钟以内（GitHub 在 cron 时间过后才检查是否要起 run）。

获取当前 UTC 时间并算出 +10 分钟的 cron 表达式：

```bash
TARGET_UTC=$(date -u -v +10M +"%M %H")   # macOS BSD date；Linux 用：date -u -d "+10 minutes" +"%M %H"
NEW_CRON="${TARGET_UTC} * * *"
echo "Target cron: $NEW_CRON"
```

把 `.github/workflows/android-release-apk.yml` 里 `- cron: "0 18 * * *"` 临时替换为 `- cron: "$NEW_CRON"`（手动编辑文件，把双引号内的字符串替换为打印出来的 `$NEW_CRON` 实际值，例如 `"43 06 * * *"`）。

```bash
grep -n "cron:" .github/workflows/android-release-apk.yml   # 复检改对了
git commit -am "test: 临时改 cron 验证 schedule path（待回滚）"
git push
```

最长等待 20 分钟，轮询 schedule 触发：

```bash
for i in $(seq 1 40); do
  RUN_ID=$(gh run list --workflow="Android Release APK" --event schedule --branch feat/release-rebrand-ci --limit 1 --json databaseId,createdAt --jq '.[0].databaseId' 2>/dev/null)
  if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ]; then
    echo "Schedule run found: $RUN_ID"
    break
  fi
  echo "等 schedule trigger... (${i}/40)"
  sleep 30
done
gh run watch "$RUN_ID"
```

**验证逻辑**：本 Task 在 push 过 commit，分支 24h 内有新提交 → guard step 输出 `new_commits=1`、下游 8 step 全部执行、artifact 命名形如 `MusicFreeAndroid-nightly-YYYYMMDD-<sha>.apk`。

> "无新提交跳过"分支因为刚 push 过 commit 复现成本高，不在本 Task 验证范围；逻辑由 `if:` 表达式静态保证，已通过 Task 4 Step 6 的 YAML lint 检查语法。

- [ ] **Step 5: 回滚临时 cron 改动**

```bash
git reset --hard HEAD~1
git push --force-with-lease origin feat/release-rebrand-ci
```

> 这里用 `--force-with-lease` 不是 `--force`：确保推送时远端 ref 未被他人更新，避免覆盖陌生 commit。

- [ ] **Step 6: 校验 release.yml 已恢复**

```bash
grep "cron:" .github/workflows/android-release-apk.yml
```

预期：`- cron: "0 18 * * *"`，无其它 cron 行。

- [ ] **Step 7: tag path 烟雾验证（可选）**

> 仅当本地有 release 签名 env 或愿意走 GitHub Actions secrets 时执行。

```bash
git tag v0.0.0-test
git push origin v0.0.0-test
RUN_ID=$(gh run list --workflow="Android Release APK" --event push --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID"
gh release view v0.0.0-test
```

预期：build job + `publish-github-release` job 都跑通；GitHub Release `v0.0.0-test` 创建并附带 APK。

清理：

```bash
gh release delete v0.0.0-test --yes
git push origin :refs/tags/v0.0.0-test
git tag -d v0.0.0-test
```

如果跳过本 Step，请在 PR 描述里显式说明"tag path 未在本 PR 验证、回退依赖既有 workflow 历史"。

---

## Task 6: PR 合并 + main 后续动作

- [ ] **Step 1: 创建 PR**

```bash
gh pr create --base main --head feat/release-rebrand-ci --title "feat(release): MF音乐 品牌化 + CI 收敛" --body "$(cat <<'EOF'
## Summary

- 显示名落为 `MF音乐` / `MF音乐(D)`（resValue 注入，main strings.xml 不再保有 app_name）
- Debug 包 adaptive icon 右下角加红 D 徽章（layer-list + 矢量，零额外 webp）
- 删除 `.github/workflows/android-debug-apk.yml` 与 `.github/workflows/dev-harness-gate.yml`
- 同步改写 5 处文档引用（AGENTS / CLAUDE / INDEX / test rules / test incident）与 `scripts/dev-harness/check.sh` 1 处失实注释
- `.github/workflows/android-release-apk.yml`：加 02:00 北京时间 schedule、24h 新提交守卫、nightly artifact 命名；publish-github-release job 不动

Spec: `docs/superpowers/specs/2026-05-13-release-rebrand-and-ci-design.md`
Plan: `docs/superpowers/plans/2026-05-13-release-rebrand-and-ci.md`

## Test plan

- [x] `:app:assembleDebug` + aapt 校验 `application-label:'MF音乐(D)'`
- [x] adb 安装 debug 包，目检桌面圆形/水滴蒙版下徽章可见
- [x] `bash scripts/dev-harness/check.sh` 本地通过
- [x] `workflow_dispatch` 触发 release workflow，artifact 命名 `MusicFreeAndroid-manual-<run>.apk`
- [x] 临时 cron 验证 schedule path（24h 有 commit → 正常构建并 nightly 命名）
- [ ] tag path 视情况验证（已在 plan Task 5 Step 7 说明）

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: 合并（squash 单 commit，按 AGENTS.md 约定）**

`gh pr merge` 在远端做 squash，本地按 AGENTS.md 用 `git merge --squash` 等效路径：

```bash
gh pr merge --squash --subject "feat(release): MF音乐 品牌化 + CI 收敛"
```

合并后切回主仓库 worktree：

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git checkout main
git pull --ff-only
```

- [ ] **Step 3: 清理 worktree 与分支**

```bash
git worktree remove .worktrees/release-rebrand-ci
git push origin --delete feat/release-rebrand-ci
git branch -d feat/release-rebrand-ci 2>/dev/null || true
```

预期：`git worktree list` 不再含 `release-rebrand-ci`；远端分支已删。

- [ ] **Step 4: main 上 schedule 启用确认**

```bash
gh workflow list | grep "Android Release APK"
```

预期：workflow 状态 `active`。GitHub Actions schedule 在合并到默认分支后才生效，main HEAD 即为新 cron。下一次北京 02:00 触发为首个 nightly。

- [ ] **Step 5: 通知点（可选）**

如果有发布频道/群组，告知"今日起 nightly 在 24h 有 commit 时北京 02:00 自动产 artifact，名 `MusicFreeAndroid-nightly-YYYYMMDD-<sha>.apk`；正式版本仍由 `v*` tag 触发 Release"。
