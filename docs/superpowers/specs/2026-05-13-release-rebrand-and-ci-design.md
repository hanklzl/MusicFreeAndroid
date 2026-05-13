# 发布品牌化与 CI 改造设计

- **状态**：当前规范（草案）
- **日期**：2026-05-13
- **适用范围**：`:app` 模块的显示名/图标；`.github/workflows/` 下的全部 workflow

## 1. 背景与目标

当前应用桌面显示名为 `MusicFreeAndroid`（内部代号），未做发布品牌化，也无 release/debug 视觉区分。CI 上同时维护着每次 push 都跑的 Debug APK 构建和 Harness 门禁，但项目阶段进入"少而稳"的纯人盯发布节奏，门禁与每次 push 的 debug 构建已不再产生信息增益。

本次改造的目标：

1. **品牌化**：把 Release 包桌面名改为 `MF音乐`，Debug 包改为 `MF音乐(D)`；Debug 图标在原 adaptive icon 上加红色 `D` 角标，与 Release 包一眼可分辨。
2. **CI 收敛**：删除每次 push 的 Debug APK 构建工作流与 Dev Harness 门禁；同步把 Harness 文档体系内 5 处指向 `dev-harness-gate.yml` 的引用改写为"本地脚本 + 人工 review"路径，保持文档与仓库现实一致。
3. **Release 触发现代化**：Release APK 改为按北京时间每日凌晨 02:00 自动产出 nightly artifact（仅当 24h 内 main 有新提交），同时保留 tag 推送出正式 GitHub Release 与 manual dispatch 的能力。

## 2. 当前状态（事实基线）

- `app/src/main/res/values/strings.xml#app_name` = `MusicFreeAndroid`，由 `AndroidManifest.xml` 的 `application/@android:label` 与启动 activity 的 `@android:label` 引用。
- `app/build.gradle.kts` 中 `debug` 仅设了 `applicationIdSuffix = ".debug"`，未覆盖 label 或 icon。
- `app/src/main/res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml` 为 adaptive icon，foreground 引用 `@mipmap/ic_launcher_foreground`（密度限定 webp），background 引用 `@color/ic_launcher_background`。无 debug 源集覆盖。
- `player/src/main/res/values/strings.xml#playback_notification_channel_name = "播放控制"` 与显示名解耦，不在本次范围。
- 内部代号（`Theme.MusicFreeAndroid`、`MusicFreeApplication`、namespace、`applicationId = com.zili.android.musicfreeandroid`）不在本次范围，**保持不变**。
- `.github/workflows/`：
  - `android-debug-apk.yml` —— 每次 push 构建 Debug APK 并上传 artifact。
  - `dev-harness-gate.yml` —— push/PR 跑 symlinks/grep guards、`compileDebugUnitTestKotlin`、contract tests。
  - `android-release-apk.yml` —— 仅在 `v*` tag 推送或 manual dispatch 时触发；tag 路径下后续 `publish-github-release` job 创建 GitHub Release。

## 3. 设计

### 3.1 App 显示名

**改动**

1. 删除 `app/src/main/res/values/strings.xml` 里的 `<string name="app_name">…</string>`，避免与 `resValue` 冲突而触发 `Duplicate resources`。
2. `app/build.gradle.kts` 的 `buildTypes` 块：
   ```kotlin
   buildTypes {
       debug {
           applicationIdSuffix = ".debug"
           resValue("string", "app_name", "MF音乐(D)")
       }
       release {
           // …既有 signingConfig / R8 / Logan 配置不动
           resValue("string", "app_name", "MF音乐")
       }
   }
   ```
3. `AndroidManifest.xml` 保持 `@string/app_name` 不动。

**取舍**：用 `resValue` 而不是 `app/src/debug/res/values/strings.xml`，让 app_name 字符串单点存在于 build script，与 icon 的 debug 源集（§3.2）分工清晰：strings 在 build script，资源（图标 XML）在源集。

### 3.2 Debug 图标徽章

**总思路**：仅在 `app/src/debug/res/` 下新增 3 个 XML 覆盖 adaptive icon，foreground 用 `layer-list` 把原 `@mipmap/ic_launcher_foreground` 叠加一个矢量"红圆 D"。Release 素材完全不动。

**新增文件**

1. `app/src/debug/res/mipmap-anydpi-v26/ic_launcher.xml`
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@color/ic_launcher_background"/>
       <foreground android:drawable="@drawable/ic_launcher_foreground_debug"/>
   </adaptive-icon>
   ```
2. `app/src/debug/res/mipmap-anydpi-v26/ic_launcher_round.xml` —— 与上同，shape 蒙版由系统在 launcher 端施加，foreground/background 复用同一份。
3. `app/src/debug/res/drawable/ic_launcher_foreground_debug.xml`
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

**尺寸约束**
- adaptive icon foreground 画布 108×108dp，视觉安全区中心 72dp。徽章 36dp 放 `bottom|right`，内边距 20dp，徽章中心落在视觉安全区右下象限内，圆形/方形/水滴蒙版均不会被裁切。
- 徽章颜色 `#E53935`（Material Red 600），白色 D；与 release 主图标差异肉眼可一秒分辨。

**取舍**：用 `layer-list` 而不是离线烤进 webp，是为了零额外二进制资产；新增文件 3 个 XML，整体维护成本最低。如未来发现 path 拼出的"D"字形不够精致，可独立替换 vector 而不影响其他配置。

### 3.3 GitHub Actions 改造

#### 3.3.1 删除

- `.github/workflows/android-debug-apk.yml`
- `.github/workflows/dev-harness-gate.yml`

#### 3.3.2 Harness 文档同步改写（与 workflow 删除原子提交）

删除 `dev-harness-gate.yml` 让"CI 自动拦截"成为不实陈述。本次实施在**同一 commit / PR** 内一并下改以下 5 处，保证文档与仓库现实一致：

| 文件 | 当前表述 | 改写为 |
|---|---|---|
| `AGENTS.md:36` | "违反 rules.md 中标记 MUST / MUST NOT 的条款将在 CI `dev-harness-gate` 作业被拦下。" | "违反 rules.md 中标记 MUST / MUST NOT 的条款由人工 review 拦截；本地可跑 `bash scripts/dev-harness/check.sh` 自查。" |
| `CLAUDE.md:36` | 同上 | 同上（两份文件保持一致） |
| `docs/dev-harness/INDEX.md:32` | "- 工作流：`.github/workflows/dev-harness-gate.yml`" | 删除该行 |
| `docs/dev-harness/test/rules.md:79` | "`dev-harness-gate.yml` MUST 含一个 …" | "PR 合入前 MUST 在本地跑 `bash scripts/dev-harness/check.sh`（默认含编译全模块测试源步骤）；新加模块时 MUST 同步加入 `scripts/dev-harness/check.sh` 的模块列表。" |
| `docs/dev-harness/test/incidents.md:14-17, 33` 中 INC-2026-0016 的 `guard` 块 | `type: ci-step` / `target: .github/workflows/dev-harness-gate.yml (…)` 及末段"备注"里的同源描述 | `type: manual` / `target: bash scripts/dev-harness/check.sh` ；备注改为"本地复现：`bash scripts/dev-harness/check.sh`（默认步骤包含编译全模块测试源）" |

**`scripts/dev-harness/check.sh` 小修**：脚本逻辑（symlinks → grep guards → 全模块 compileDebugUnitTestKotlin → contract tests）与被删除的 `dev-harness-gate.yml` 一一对应，无需改造逻辑；但脚本内第 52 行附近注释 "Adding tests in other modules requires extending this list AND the CI workflow" 在 workflow 删除后变成陈述不实，需把 "AND the CI workflow" 删除。

> 备注：除上述注释外，`scripts/dev-harness/check.sh` 逻辑保持不变；这次只是把"CI 兜底"降级为"PR 合入前本地脚本 + 人工 review"。

#### 3.3.3 修改 `android-release-apk.yml`

**触发器扩展**

```yaml
on:
  push:
    tags:
      - "v*"
  schedule:
    - cron: "0 18 * * *"   # 18:00 UTC == 北京时间次日 02:00
  workflow_dispatch:
```

**Nightly guard**：在 `Checkout` 之后立即评估 24h 内 main 是否有新提交。schedule 触发且无新提交时，整个 job 仍报 success 但跳过后续构建步骤，不上传 artifact，不污染 workflow 历史。

1. `Checkout` step 增加 `with: { fetch-depth: 0 }`，让 `git log --since` 看到完整历史（`actions/checkout@v6` 默认浅克隆）。
2. 新增 step：
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
3. 后续 `Set up JDK` 起每个 step 加守卫条件：
   ```yaml
   if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
   ```
   含 `Validate release secrets` / `Decode release keystore` / `Build Release APK` / `Name APK` / `Upload Release APK artifact`。

**APK 命名**：扩展 `Name APK` step：
```bash
if [ "$GITHUB_REF_TYPE" = "tag" ]; then
  apk_name="MusicFreeAndroid-${GITHUB_REF_NAME}.apk"
elif [ "$GITHUB_EVENT_NAME" = "schedule" ]; then
  apk_name="MusicFreeAndroid-nightly-$(date -u +%Y%m%d)-$(git rev-parse --short HEAD).apk"
else
  apk_name="MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}.apk"
fi
```

**Publish job 不动**：`publish-github-release` job 现有 `if: github.event_name == 'push' && github.ref_type == 'tag'` 已天然把 schedule / dispatch 排除；nightly 与 manual 只走 artifact，tag 路径仍发 GitHub Release。

**Concurrency 不动**：`group: android-release-apk-${{ github.ref }}`，schedule ref = `refs/heads/main`，与 tag ref 互不冲突。

**附加备注**：GitHub Actions 对默认分支无活动的仓库会在 60 天后自动停用 schedule；本项目 main 在持续推进，没风险，仅作备忘。

## 4. 不在范围

- 内部代号（namespace、`applicationId` 基础值、`Application` 类名、theme 名）。
- player 模块通知 channel 文案。
- `scripts/dev-harness/` 脚本本体（仅文档引用同步改写，脚本不动）。
- 其他 area 的 rules.md / incidents.md（仅 `test/` 域下的 INC-2026-0016 因 guard 失效需要改写；其它 area 的 guard 不依赖被删除的 workflow）。
- Release 签名 / Logan 环境变量管理（既有机制延续）。

## 5. 验证

### 5.1 本地

- `./gradlew :app:assembleDebug` 通过；安装到设备，桌面显示 `MF音乐(D)`，图标右下角红 `D` 徽章在圆形（Pixel 风格）与水滴（小米/华为风格）蒙版下均完整可见。
- `./gradlew :app:assembleRelease`（在签名 env 齐备的环境下）通过；安装后桌面显示 `MF音乐`，图标无徽章。
- Debug 与 Release 包可并存（已有 `applicationIdSuffix = ".debug"`），并排截屏对比。

### 5.2 GitHub Actions

- 推一个临时分支用 `workflow_dispatch` 手动触发：APK artifact 命名形如 `MusicFreeAndroid-manual-<run>.apk`，无 GitHub Release 创建。
- 临时把 cron 调到最近时间或等真实凌晨 02:00 触发：
  - **24h 内有新提交**：构建并上传 `MusicFreeAndroid-nightly-YYYYMMDD-<sha>.apk` artifact。
  - **24h 内无新提交**：workflow run 显示 success，guard step 输出 `notice`，无 artifact。
- 打临时 tag `v0.0.0-test` 推送：tag 路径仍走完整 build + `publish-github-release`，验证后删除 tag 与对应 release。

## 6. 风险与回滚

- **R8 影响**：本次只新增 XML 资源与 `resValue`，不引入反射/序列化路径，对 R8 keep 规则零影响。
- **回滚成本**：所有改动均为新增/删除文件 + build script 小改，git revert 单 commit 即可回到当前状态。
- **CI 风险**：删除 `dev-harness-gate.yml` 后，依赖该闸门的本地约束（test fixture 漂移、symlink 守护）将不再在 PR 上自动拦截。Spec §3.3.2 已把约束降级为"PR 合入前本地 `scripts/dev-harness/check.sh` + 人工 review"。这是本次改造主动接受的状态——必要时未来可单独恢复 CI 路径。
- **未跑 check.sh 的合入**：降级后若 PR 作者忘记本地跑 check.sh、reviewer 也未提醒，fixture / VM ctor 漂移会回归到能合入 main 的水平。本次改造接受这个可能性；缓解是 INC-2026-0016 的 guard 仍记录在案，后续 harness-curator 巡检会盯到复发。
