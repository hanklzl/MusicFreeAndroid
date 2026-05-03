---
status: 当前规范
date: 2026-05-03
topic: 全量依赖升级到 latest stable
strategy: 单 worktree + 单 PR
branch: chore/deps-bump-2026-05
worktree: .worktrees/chore/deps-bump-2026-05
---

# 全量依赖升级设计（2026-05-03）

## 1. 目标与范围

### 1.1 目标

将 `gradle/libs.versions.toml` 中的依赖一次性升级到 2026-05-03 已核实的 latest stable
版本，作为单一 worktree（`.worktrees/chore/deps-bump-2026-05`）+ 单 PR 交付。

### 1.2 范围内

- 升级 23 个依赖版本号（详见 §2）
- 调整 toolchain JDK 至 21（LTS）
- 抬升 Android `compileSdk` 至 36.1（由 core-ktx 1.18 触发）
- Gradle wrapper 从 `9.5.0-milestone-5` 回退到 stable `9.4.1`（AGP 9.2 要求）
- 修复升级过程中产生的所有编译 / 测试 / lint 错误
- 完成 Tier C 验收（编译 + 单测 + lint + connectedAndroidTest + 端到端关键链路）

### 1.3 范围外

- 不重构业务代码、不引入新功能、不调整模块边界
- 不升级到任何 alpha / beta / rc / milestone 版本（stable only）
- 不自行升级 `hilt-android`（2.59.2 已是 latest stable，2025-02-20 发布）
- 不自行升级 `quickjs-kt-android`（1.0.5 已是 latest stable）
- 不升级 `kotlinx-coroutines-core`（1.10.2 已是 latest stable，1.11 仍 RC）
- 不升级 `junit`（4.13.2 已冻结）
- 不动 RN 仓库（`../MusicFree`）

### 1.4 成功判定

- 单 PR `chore/deps-bump-2026-05` 通过 Tier C 全部关卡
- 模拟器 / 设备运行态：插件安装 → 搜索 → 播放 → 队列拖拽 → 后台播放 → 锁屏 →
  杀进程恢复 → 远程封面图加载 全部正常

## 2. 版本矩阵

### 2.1 Toolchain & 构建插件

| Key                 | 现值                  | 目标值    | 备注                                       |
| ------------------- | --------------------- | --------- | ------------------------------------------ |
| `agp`               | `9.1.0`               | `9.2.0`   | 要求 Gradle ≥ 9.4.1 + JDK 17               |
| `kotlin`            | `2.2.10`              | `2.3.21`  | Compose plugin 同步                        |
| `ksp`               | `2.2.10-2.0.2`        | `2.3.7`   | KSP ≥ 2.3.0 已与 Kotlin 解耦               |
| Gradle wrapper      | `9.5.0-milestone-5`   | `9.4.1`   | 从 milestone 回退到 stable                 |

### 2.2 AndroidX & Compose

| Key                       | 现值        | 目标值       |
| ------------------------- | ----------- | ------------ |
| `composeBom`              | `2026.02.01`| `2026.04.01` |
| `coreKtx`                 | `1.10.1`    | `1.18.0`     |
| `lifecycleRuntimeKtx`     | `2.6.1`     | `2.10.0`     |
| `lifecycleRuntimeCompose` | `2.9.1`     | `2.10.0`     |
| `activityCompose`         | `1.8.0`     | `1.13.0`     |
| `navigationCompose`       | `2.9.0`     | `2.9.8`      |
| `hiltNavigationCompose`   | `1.2.0`     | `1.3.0`      |
| `room`                    | `2.7.1`     | `2.8.4`      |
| `datastore`               | `1.1.7`     | `1.2.1`      |

### 2.3 DI / Kotlin libs / 媒体 / 图片 / 网络

| Key                   | 现值       | 目标值      |
| --------------------- | ---------- | ----------- |
| `hilt`                | `2.59.2`   | 保持不变    |
| `kotlinxSerialization`| `1.8.1`    | `1.11.0`    |
| `coroutines`          | `1.10.2`   | 保持不变    |
| `media3`              | `1.9.2`    | `1.10.0`    |
| `coil`                | `3.2.0`    | `3.4.0`     |
| `okhttp`              | `4.12.0`   | `5.3.2`     |
| `quickjsKt`           | `1.0.5`    | 保持不变    |
| `reorderable`         | `2.4.3`    | `3.1.0`     |

### 2.4 测试

| Key                 | 现值       | 目标值    |
| ------------------- | ---------- | --------- |
| `junit`             | `4.13.2`   | 保持不变  |
| `junitVersion`      | `1.1.5`    | `1.3.0`   |
| `androidxTestRunner`| `1.4.0`    | `1.7.0`   |
| `espressoCore`      | `3.5.1`    | `3.7.0`   |
| `turbine`           | `1.2.0`    | `1.2.1`   |
| `robolectric`       | `4.15.1`   | `4.16.1`  |
| `mockitoKotlin`     | `5.4.0`    | `6.3.0`   |

### 2.5 配置文件改动

- `gradle/wrapper/gradle-wrapper.properties`：
  `distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip`
- 各模块 `build.gradle.kts` 中的 `compileSdk = 36` → `36.1`、`targetSdk` 同步
- 根 `build.gradle.kts` / 各模块：Java compatibility `VERSION_11` → `VERSION_17`，
  Kotlin `jvmTarget = "17"`
- 根 `build.gradle.kts`：JVM toolchain 使用 JDK 21
  （`kotlin { jvmToolchain(21) }`）
- `gradle.properties`：开启 `org.gradle.java.installations.auto-download=true`

### 2.6 估算改动文件清单

- `gradle/libs.versions.toml`（核心）
- `gradle/wrapper/gradle-wrapper.properties`
- 根 `build.gradle.kts`
- 9 个模块的 `build.gradle.kts`：`:app`、`:core`、`:data`、`:player`、
  `:plugin`、`:feature:home`、`:feature:player-ui`、`:feature:search`、
  `:feature:settings`
- 业务源码：受 OkHttp 5 / reorderable 3 / Kotlin 2.3 / Media3 1.10 调用点影响而需要的最小修复
  （具体改动量在实施阶段才能确定）

## 3. 兼容性约束 & 内部升级顺序

### 3.1 强耦合三元组（必须同步改动）

```
[Kotlin 2.3.21] ←→ [kotlin.plugin.compose 2.3.21] ←→ [KSP 2.3.7]
```

任意一个错位都会引发 "compiler version mismatch" 编译错误。

### 3.2 工具链与 SDK 联动

| 触发                  | 必须连带改                                                                |
| --------------------- | ------------------------------------------------------------------------- |
| AGP 9.2.0             | Gradle wrapper 9.4.1（不能再用 milestone）；JDK toolchain ≥ 17（用 21）   |
| core-ktx 1.18.0       | `compileSdk` 36.1（包括 `:app` 和所有 `:feature:*`、`:core`、`:data`、`:player`、`:plugin`） |
| Java compatibility 17 | Kotlin `jvmTarget = "17"` 同步；测试任务的 `kotlinOptions.jvmTarget` 也要 17 |
| AGP 9.2.0             | `hilt-android 2.59.2` 已支持 AGP 9（Dagger 2.59 起明确支持 AGP 9 + Gradle 9.1+），不用改 |

### 3.3 Compose BOM 主导

升 BOM 至 `2026.02.01 → 2026.04.01` 后，**移除任何手工写死的 Compose 子库版本**
（`material3`、`material-icons-extended`、`ui` 系列），全部由 BOM 驱动。当前 toml
已是无 version 写法，无需改 — 仅核对一遍。

### 3.4 OkHttp 5 联调约束

OkHttp 4 → 5 是本次唯一 API 表面动得多的运行时升级。需要联调三处下游使用方：

1. `media3-datasource-okhttp 1.10.0`：内部接口若有 break，需要回看 Media3 release notes
   确认与 OkHttp 5 兼容
2. `coil-network-okhttp 3.4.0`：Coil 3.4 已声明支持 OkHttp 5
3. `:plugin` 模块自定义的 OkHttp Interceptor / Cookie Jar 等使用点：以
   `OkHttpClient`、`Interceptor`、`Authenticator`、`Dns` 为入口逐一核查

### 3.5 worktree 内 commit 顺序

为便于问题定位与必要时的局部 reset，按以下顺序推进：

1. **C1 toolchain**：Gradle wrapper 9.4.1 + AGP 9.2.0 + JDK toolchain 21 +
   Java/Kotlin target 17 + compileSdk 36.1
2. **C2 kotlin 三元组**：Kotlin 2.3.21 + Compose plugin 2.3.21 + KSP 2.3.7
3. **C3 Compose BOM**：2026.04.01
4. **C4 AndroidX 集合**：core-ktx / lifecycle-* / activity / navigation /
   hilt-nav / room / datastore
5. **C5 媒体与图片**：media3 1.10.0 + coil 3.4.0
6. **C6 OkHttp 5**（含调用点修复）
7. **C7 reorderable 3**（含 UI 调用点修复）
8. **C8 序列化**：kotlinx-serialization-json 1.11.0
9. **C9 测试矩阵**：espresso / runner / ext.junit / turbine / robolectric /
   mockito-kotlin 6
10. **C10 验收修复**：跑完 Tier C 后所有发现问题的修复（如有）

> 所有 commit 都在同一分支，最终走单 PR。这里的拆分是工程纪律，不是多 PR。

## 4. Tier C 验收清单

按 §1.2 选定的 Tier C，分三层执行，**前一层不全绿不进入下一层**。

### 4.1 Layer 1 — 静态 / 编译 / 单元测试

| 命令                       | 通过条件                                       |
| -------------------------- | ---------------------------------------------- |
| `./gradlew clean`          | 成功                                           |
| `./gradlew assembleDebug`  | 全模块编译通过，无 deprecation 致命警告        |
| `./gradlew test`           | 全模块单测全绿（9 个模块；仅含测试代码的模块需通过） |
| `./gradlew lint`           | 没有 new error 级别问题（warning 允许）        |

失败处置：在 worktree 内修复后回到第一条重跑。

### 4.2 Layer 2 — 仪器测试 + 模拟器冒烟

模拟器要求：API 36 / 36.1，arm64 镜像，至少 4 GB RAM。

| 命令                                                              | 通过条件                          |
| ----------------------------------------------------------------- | --------------------------------- |
| `./gradlew connectedAndroidTest`                                  | 全绿                              |
| 启动 → 首页 → 搜索一首歌 → 播放 30s → 暂停 → 进歌单 → 退出       | 无 ANR / crash / UI 绘制异常      |

### 4.3 Layer 3 — 端到端关键链路

| #  | 链路                                                      | 主要验证的升级项               |
| -- | --------------------------------------------------------- | ------------------------------ |
| L1 | 安装 1 个内置插件 + 1 个 URL 插件                         | QuickJS、Kotlin 2.3、AGP 9.2   |
| L2 | 使用插件搜索一首歌曲并获取 lyric / source                 | OkHttp 5、Kotlin 2.3           |
| L3 | 加入播放队列，长按拖拽重排序                              | reorderable 3                  |
| L4 | 进入全屏播放器，前后切歌、seek                            | Media3 1.10                    |
| L5 | 切到后台，锁屏，从锁屏控制暂停 / 上下首                   | Media3 1.10 + MediaSession     |
| L6 | 杀进程后重新打开，状态恢复                                | DataStore 1.2 + Room 2.8       |
| L7 | 浏览推荐歌单 / 榜单（含远程封面图）                       | Coil 3.4 + OkHttp 5 联调       |
| L8 | 在「设置 → 插件管理」执行 fileSelector 安装 / 卸载        | Activity 1.13、AndroidX 集合   |

任一条链路失败 → 在 worktree 内修复 → 回到 Layer 1 重新跑全套。

### 4.4 证据沉淀

每条链路记录：

- 命令输出片段（成功的最后几行 / 失败的 stack trace）
- 关键 UI 截图或 `uiautomator dump` 关键节点
- 全部纳入 PR 描述的「验收证据」段落

## 5. 风险登记与回滚策略

### 5.1 风险登记

| ID | 风险                                                                              | 概率 | 影响 | 缓解                                                                                       |
| -- | --------------------------------------------------------------------------------- | ---- | ---- | ------------------------------------------------------------------------------------------ |
| R1 | Kotlin 2.3 编译失败：context-receivers 已被 context-parameters 替换；kapt/K2 差异 | 中   | 高   | 跑 Layer 1，按编译器 message 改源码；KSP 错位时再次确认三元组版本                           |
| R2 | OkHttp 5 调用点 break：弃用 API 移除、Kotlin 扩展函数包路径变化                   | 中   | 高   | grep `OkHttpClient`/`Interceptor`/`Authenticator`/`Dns`/`MediaType`/`okhttp3.coroutines.*` |
| R3 | reorderable 3 API 变化：`ReorderableItem` 参数 / `rememberReorderableLazyListState` 签名 | 中 | 中 | 直接对照 v3.0 changelog；调用点应在 `:feature:player-ui` 队列 UI                            |
| R4 | Compose BOM 升级触发非预期 UI 视觉 / 重组性能问题                                 | 低   | 中   | Layer 3 L3/L4/L7 重点关注；必要时跑 Compose 自带 metric                                     |
| R5 | core-ktx 1.18 + compileSdk 36.1 触发 R8 / desugaring 失败                         | 低   | 高   | Layer 1 lint + assembleRelease 单独跑一次（不是 debug）                                     |
| R6 | mockito-kotlin 6 mocking final class 行为变化导致历史单测失败                     | 中   | 低   | Layer 1 跑 `:*:test` 全模块；失败用例评估是改 mock 还是改实现                               |
| R7 | Media3 1.10 行为差异（trackSelection / audio focus）                              | 低   | 中   | L4/L5/L6 端到端验证；`PlayerController` 的 StateFlow 输出对照旧版 log                       |
| R8 | Gradle 9.4.1 + JDK 21 toolchain 自动下载失败 / 公司网络限制                       | 低   | 高   | 升级前确认开发机已有 JDK 21；如失败回退用本地 `JAVA_HOME` 显式指向                          |
| R9 | hilt-android 2.59.2 + AGP 9.2 + KSP 2.3.7 三方组合未被官方矩阵覆盖                | 低   | 高   | Layer 1 编译时尽早暴露；如失败需要等待 hilt 新版或暂回退                                    |

### 5.2 回滚策略

worktree + 单 PR 模型本身就是最强回滚保险。三层回滚：

1. **L0 — 单 commit 级回滚**（C1～C10 任一 commit 出问题）：在 worktree 内
   `git reset --hard HEAD~N` 或 `git revert <sha>`，重新发起该 commit 的修复。
2. **L1 — 单 PR 级别整体放弃**：worktree 跑不到 Tier C 全绿 → 直接关闭 worktree，
   主仓库 main 不动，无任何代价。
3. **L2 — 已合并后回滚**：合并后才发现运行态问题 → `git revert -m 1 <merge-sha>`
   一刀切回滚到合并前；不允许 cherry-pick 部分。

### 5.3 升级中止判定

worktree 阶段下列任一条件成立时，**暂停推进**，回头与决策方对齐：

- 单一 commit（C1～C10）的修复时间 > 4 小时
- Tier C Layer 3 出现无法修复的端到端 crash
- 同时出现两个 high-impact 风险（R1+R2 / R2+R3 等）
- 发现需要修改业务架构（不属于纯依赖升级范畴）才能让升级通过

## 6. Worktree 工作流

### 6.1 创建 worktree

前置确认（一次性）：

- 主仓库 `main` 干净
- `.gitignore` 已忽略 `.worktrees/`（已确认存在）
- 本机已装 JDK 21（`/usr/libexec/java_home -V`）；如未装，开启 Gradle auto-download

操作（在主仓库根目录执行；引用使用相对路径）：

```bash
git fetch origin
git worktree add -b chore/deps-bump-2026-05 .worktrees/chore/deps-bump-2026-05 origin/main
cd .worktrees/chore/deps-bump-2026-05
```

后续工作（编辑、编译、测试、commit）只在该 worktree 内进行；主工作区保持
`main` 不变。

### 6.2 commit 纪律

按 §3.5 定义的 C1～C10 顺序，每步一个 commit：

- 每个 commit 后跑 `./gradlew :app:assembleDebug` 至少确认未破坏编译
- commit message 模板：`chore(deps): <one-line summary>`，body 写清升级幅度
  （如 `Kotlin 2.2.10 -> 2.3.21`）和触及的子模块
- 不 squash — 保留升级故事线便于 review 和 git bisect

### 6.3 验收阶段

完成 C1～C10 后，按 §4 执行 Tier C，发现的修复落到 C10「验收修复」commit
（或多个 fix commit）。

### 6.4 PR 与合并

- 在 worktree 内 `git push -u origin chore/deps-bump-2026-05`
- `gh pr create`，PR 描述包含：
  - 升级总览（链接到本设计文档）
  - C1～C10 commit 列表
  - §4.4 定义的验收证据
  - 风险登记 R1～R9 的逐项实测状态（命中 / 未命中 / 已修复）
- review 通过后再合并；合并方式 = **merge commit**（不 squash）

### 6.5 worktree 清理

PR 合并后，在主仓库根目录：

```bash
git worktree remove .worktrees/chore/deps-bump-2026-05
git branch -D chore/deps-bump-2026-05
```

### 6.6 异常处置

- worktree 推进中如需暂停（>1 天空档）：把当前进度 push 到 remote 同名分支，避免本机意外丢失
- 决定放弃这次升级（见 §5.3）：直接 `git worktree remove --force` 即可

## 7. 文档归属

- 本规范同时作为本次升级的「执行入口」，归类为 `docs/superpowers/specs/` 下「当前规范」
- 在 `docs/DOCS_STATUS.md` 中应新增一条索引（升级正式开始时再追加，避免索引过早）
- 升级合并后，本文件状态保留「当前规范」直到下一轮全量升级，再标记为历史
