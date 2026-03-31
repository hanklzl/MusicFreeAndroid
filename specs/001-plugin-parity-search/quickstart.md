# Quickstart: 插件能力对齐验证

本指南用于快速执行“添加插件 -> 更新插件 -> 通过插件搜索歌曲”对齐验证。

## 1. Preconditions

- 当前分支：`001-plugin-parity-search`
- 可用 Android 设备或模拟器
- 本机已存在原版仓库：`/Users/zili/code/android/MusicFree`
- 网络可访问以下地址：
  - `https://13413.kstore.vip/yuanli/yuanli.json`（订阅）
  - `https://13413.kstore.vip/yuanli/wy.js`（插件示例）

## 2. Build And Launch (Compose)

```bash
# in /Users/zili/code/android/MusicFreeAndroid
./gradlew assembleDebug
./gradlew installDebug
adb shell am start -S -n com.zili.android.musicfreeandroid/.MainActivity
```

## 3. Build And Launch (Original RN)

```bash
# in /Users/zili/code/android/MusicFree
npm install          # first time only
npm start            # keep running in a terminal
npm run android
adb shell am start -S -n fun.upup.musicfree/.MainActivity
```

## 4. Run Side-by-Side Comparison

两端按同一顺序执行并记录差异：

1. 插件添加（URL / 本地 / 订阅）
2. 插件更新（单个 / 全部）
3. 插件搜索歌曲（首屏 + 分页）

可直接使用脚本拉起两端应用：

```bash
# in /Users/zili/code/android/MusicFreeAndroid
scripts/convergence/plugin-parity/run-compose-vs-rn.sh compose
scripts/convergence/plugin-parity/run-compose-vs-rn.sh rn
```

每一步都标注结论：`一致` / `偏差` / `Compose 缺失`。

推荐同时记录下列检查点到 `specs/001-plugin-parity-search/evidence-log.md`：

1. `checkpoint.add.url`
2. `checkpoint.add.subscription`
3. `checkpoint.update.single`
4. `checkpoint.update.all`
5. `checkpoint.search.query`
6. `checkpoint.search.pagination`

## 5. Validate Add Plugin

### 5.1 Add From URL

1. 在插件管理中输入插件 URL（如 `https://13413.kstore.vip/yuanli/wy.js`）
2. 执行安装
3. 预期：插件出现在已安装列表，状态可用于搜索

### 5.2 Add From Subscription

1. 使用订阅地址 `https://13413.kstore.vip/yuanli/yuanli.json`
2. 执行订阅导入
3. 预期：返回总数/成功数/失败数，并导入至少 1 个可搜索插件

### 5.3 Add From Local File

1. 选择本地插件文件进行添加
2. 执行安装
3. 预期：安装成功后插件可见且可进入搜索选择集合

## 6. Validate Update Plugin

### 6.1 Single Plugin Update

1. 选择一个已安装且具备更新来源的插件
2. 执行单插件更新
3. 预期：更新成功时版本信息刷新；失败时展示明确原因且不破坏当前可用插件

### 6.2 Batch Update

1. 执行“更新全部插件”
2. 预期：返回逐插件结果汇总（成功/失败计数）

## 7. Validate Search Song

1. 进入搜索页
2. 选择已安装且可搜索插件
3. 输入关键词（如 `in the end`）并执行搜索
4. 点击“加载更多”验证分页
5. 预期：
   - 返回结果或明确空结果
   - 分页追加正确
   - 错误时保留已有结果并允许重试

## 8. Automated Verification

```bash
# in /Users/zili/code/android/MusicFreeAndroid
./gradlew :plugin:testDebugUnitTest
./gradlew --console=plain --no-daemon :feature:settings:testDebugUnitTest
./gradlew --console=plain --no-daemon :feature:search:testDebugUnitTest
./gradlew :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest
scripts/convergence/plugin-parity/validate-matrix.sh
scripts/convergence/plugin-parity/check-release-gate.sh
```

将执行结果记录到：

- `docs/convergence/iteration-14/verification.md`
- `docs/convergence/iteration-14/plugin-parity-verification.md`

建议记录字段：

1. command
2. exit_code
3. key_output
4. related_capability_id
5. artifact_path

## 9. Release Gate

- P1 能力点自动化与人工验收必须全部通过
- 任一关键能力失败则不得标记“完全对齐”

## 10. Matrix Validation Usage

在更新 `parity-matrix.md` 后执行：

```bash
# in /Users/zili/code/android/MusicFreeAndroid
scripts/convergence/plugin-parity/validate-matrix.sh
```

可指定自定义矩阵文件：

```bash
scripts/convergence/plugin-parity/validate-matrix.sh /abs/path/to/parity-matrix.md
```
