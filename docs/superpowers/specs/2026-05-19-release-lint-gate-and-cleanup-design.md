# Release Lint Gate 与当前 Lint 清理设计

> 文档状态：当前规范
> 适用范围：当前 `./gradlew lint --continue --no-daemon` 报告清理、发布前 lint gate 接入
> 直接执行：是（作为实现计划输入）
> 创建日期：2026-05-19

## 背景

本次在仓库根目录执行 `./gradlew lint --continue --no-daemon`，完整报告生成在各模块 `build/reports/lint-results-debug.txt`。当前报告合计为 **2 errors + 92 warnings**，其中唯一 error 类别是 `:player` 的 `MissingPermission`；warning 分为代码 / manifest / Compose / 资源 / 版本 freshness 等多组。

用户确认采用源头修复方案，并额外要求：**不通过 lint 配置排除、baseline、ignore 或 suppress 来隐藏问题**。因此本轮不新增 `lint.xml` 排除项，不新增 lint baseline，不把 issue 改成低优先级或关闭；能修的从代码、manifest、资源、版本目录和 release 脚本入口修。

## 目标

1. 清理当前 lint 报告中的全部 error 与 warning，使当前代码库在不新增 lint 配置排除的前提下通过 `./gradlew lint --continue --no-daemon`。
2. 将 lint 纳入发布性检查：本地 `scripts/release/preflight.sh` 必跑 lint；GitHub release workflow 仅在推送 `v*` tag 时跑 lint。
3. 保持日常开发检查轻量：不把 lint 加入 `scripts/dev-harness/check.sh`，不让 nightly / manual workflow 默认承担 lint 成本。
4. 所有修复保持窄范围；版本 freshness 类 warning 通过显式版本升级解决，而不是隐藏。

## 非目标

- 不引入 lint baseline。
- 不新增 `lint.xml`、Gradle lint disable / ignore / warning 降噪配置。
- 不把 release 构建验证扩展到普通日常收尾；普通功能仍默认 Debug 构建验证。
- 不借 lint 清理做无关 UI 重构或功能改造。

## 当前问题分组

### 必修错误

- `:player` `MissingPermission`：`PlaybackNetworkStateProvider` 使用 `ConnectivityManager.activeNetwork` 与 `getNetworkCapabilities`，缺少 `android.permission.ACCESS_NETWORK_STATE`。

### 代码与 manifest warning

- `ExportedService`：`player` 的 exported service 未声明绑定权限，需要按 MediaSessionService 公开语义复核后从 manifest 层修复。
- `QueryPermissionsNeeded`：播放器页与设置页打开系统设置 / intent 查询时需要 package visibility 处理。
- `DefaultLocale`：下载中页面字符串格式化应使用显式 `Locale`。
- `ObsoleteSdkInt`：`minSdk=29` 后多余 SDK 判断应移除或改写。
- `UseKtx`：已有 KTX extension 可替换 `Uri.parse`、`Color.parseColor` 等调用。
- `InlinedApi`：Android 13 权限常量应通过 API guard 或等价安全常量表达。

### Compose warning

- `ModifierParameter`：Composable 的 `modifier` 参数顺序按 Compose lint 要求调整，并同步调用点。
- `ConfigurationScreenWidthHeight`：`rpx` 计算从 `LocalConfiguration.screenWidthDp/screenHeightDp` 迁移到 `LocalWindowInfo.current.containerSize` 口径，保持 RN `minWindowEdge` 语义。
- `FrequentlyChangingValue`：歌词内容读取布局信息时避免在 composition 阶段直接读取频繁变化值，改到 layout / derived state / side effect 中处理，避免歌词页频繁重组。

### 资源 warning

- `IconLocation`：bitmap 资源从 densityless `drawable` 移到合适的 `drawable-nodpi` 或 density 目录；必须保持资源名稳定，避免调用点变化。
- `UnusedResources`：删除确认为模板遗留且没有 runtime 反射 / manifest / theme 引用的颜色和 splash 资源。
- `MonochromeLauncherIcon`：为主包与 debug 包 adaptive icon 补 monochrome layer，资源复用现有图形资产，避免新增品牌样式变更。
- `UnusedAttribute` / `RedundantLabel`：manifest 里移除冗余或低版本无效配置；若配置承担行为约束，改到版本限定资源或代码路径。

### 版本 freshness warning

- `compileSdk` / `targetSdk` / AGP / AndroidX / Compose BOM / Media3 / coroutines / mockk / semver / org.json 等版本提示不通过 lint 配置隐藏。
- 实现阶段按 lint 当前建议做显式升级，并同步版本目录与构建基线文档。
- 若升级出现 API 破坏、插件不兼容、构建工具缺失或 release 风险，需要停在具体失败点，给出失败证据和下一步选择，而不是引入 suppress。

## 发布检查设计

### 本地 preflight

在 `scripts/release/preflight.sh` 的版本一致性检查之后、Release APK 构建之前加入：

```bash
echo "[dry] Run release lint"
./gradlew lint --continue --no-daemon
```

这样本地推 tag 前的硬性 preflight 会先拦住 lint error。warning 若未来因上游新版本提示再次出现，默认 Android lint 不会让 preflight 失败；本轮仅要求清理当前报告，不把 warning 设为 fatal。

### GitHub release workflow

在 `.github/workflows/android-release-apk.yml` 的 `build-release-apk` job 中增加 tag-only step：

```yaml
- name: Run release lint
  if: github.event_name == 'push' && github.ref_type == 'tag'
  run: ./gradlew lint --continue --no-daemon
```

该 step 放在 JDK / Gradle / Android SDK setup 之后，Release secrets 校验与 `:app:assembleRelease` 之前。`schedule` nightly 和 `workflow_dispatch` 不默认执行 lint，避免日常构建成本上升。

## 验证计划

1. `./gradlew lint --continue --no-daemon`
2. `./gradlew :app:assembleDebug --no-daemon`
3. `bash scripts/release/preflight.sh v$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')`
4. 若版本升级触发测试或编译风险，补跑受影响模块测试，例如 `:feature:player-ui:testDebugUnitTest`、`:core:testDebugUnitTest` 或 `:app:testDebugUnitTest`。

## 验收标准

- 当前 lint 报告不再包含本次列出的 error / warning。
- 没有新增 lint baseline、lint issue disable、lint suppress 或 lint 配置排除。
- release preflight 本地路径包含 lint；release workflow tag 路径包含 lint；日常 dev harness 不包含 lint。
- Debug 构建通过；若 release signing 环境不可用，不因普通本地环境缺签名而阻塞。
