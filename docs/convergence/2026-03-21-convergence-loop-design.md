# MusicFree 收敛循环设计规范

## 概述

本文档定义了一个系统化的迭代循环流程，用于将 MusicFreeAndroid（Kotlin/Compose 原生重写）逐步逼近原版 MusicFree（React Native）。目标是在功能完整度和 UI 还原度两个维度上持续收敛。

## 核心原则

- **循环迭代**：每轮分析差异 → 选优先项 → 实现 → 验证 → 记录，不断重复
- **数据驱动**：基于代码对比 + 模拟器截图的双重验证，不靠主观判断
- **混合粒度**：每轮 3 个功能点，可混合粗/中/细粒度（新页面 + 子功能 + UI 细节）
- **文档完备**：每轮迭代产出完整的技术方案、UI 对比、验证报告

## 循环总体架构

### 编排模型

采用分层 Subagent 架构，主对话作为轻量编排器：

```
主对话（编排器，保持精简，只做决策和确认）
  │
  ├─ 阶段1: 分析 subagent
  │   → 跑两个 app，截图对比，代码分析
  │   → 输出差异报告 + 优先级排序
  │   → 写入 docs/convergence/iteration-N/analysis.md
  │
  ├─ 主对话：审阅 top 3，用户确认或调整
  │
  ├─ 阶段2: 实现 subagent（严格串行，依次执行）
  │   → 每个 subagent 实现 1 个功能点
  │   → 编写技术方案 + 代码实现 + UI 截图对比 + commit
  │   → 写入 docs/convergence/iteration-N/impl-{feature}.md
  │
  ├─ 阶段3: 验证 subagent
  │   → 重新跑 app，对变更区域做端到端验收
  │   → 写入 docs/convergence/iteration-N/verification.md
  │
  └─ 主对话：更新 STATUS.md，决定继续下一轮或停止
```

### 上下文管理

- 每个 subagent 有独立上下文，不会膨胀
- 关键产出（分析报告、截图路径、验证结果）写入文件系统，后续 subagent 可读取
- Git commit 作为状态同步点
- 主对话只做决策和编排，上下文增长极慢，支持一次对话多轮迭代

## 阶段 1：分析

### 1.1 代码层对比

分析 subagent 对比以下维度：

| 对比维度 | 原版参考 | Android 版当前状态 | 输出 |
|----------|----------|-------------------|------|
| 页面覆盖率 | 19 个页面（src/pages/） | 5 个 Screen（home, player, playlistDetail, search, settings） | 缺失页面清单 |
| PluginApi 覆盖率 | 14 个方法（见下方清单） | 2 个（search, getMediaSource） | 未实现方法清单 |
| UI 组件覆盖率 | 面板 + 弹窗（见下方计数方法） | 待盘点 | 缺失组件清单 |
| 数据模型完整度 | src/types/*.d.ts | core/model/*.kt | 缺失字段清单 |

**PluginApi 14 个方法清单**（来自 `src/types/plugin.d.ts` IPluginDefine）：
search, getMediaSource, getMusicInfo, getLyric, getAlbumInfo, getMusicSheetInfo, getArtistWorks, importMusicSheet, importMusicItem, getTopLists, getTopListDetail, getRecommendSheetTags, getRecommendSheetsByTag, getMusicComments

**UI 组件计数方法**：统计 `src/components/panels/types/` 中的面板类型（排除 index.ts）+ `src/components/dialogs/components/` 中的业务弹窗（排除基础/工具类）。每次分析时重新盘点实际数量，不预设固定分母。

每个差异项标注状态：**缺失** / **部分实现** / **已实现但有偏差**。

### 1.2 截图层对比

通过 `adb shell` 半自动化操作模拟器：

```bash
# 启动 app
adb shell am start -n <package>/<activity>
# 优先使用 deep-link intent 导航（不依赖坐标）
adb shell am start -a android.intent.action.VIEW -d "app://route"
# 坐标点击作为 fallback（注意：坐标每次迭代需重新校准）
adb shell input tap X Y
# 截图
adb shell screencap /sdcard/screen.png
adb pull /sdcard/screen.png ./docs/convergence/screenshots/
```

对比路径：两个 app 的等价页面截图并排分析。

**导航策略优先级**：`am start` intent > resource-id 定位（`uiautomator dump` + xpath）> 坐标点击。坐标点击最脆弱（布局变化、分辨率不同均会失效），仅作为最后手段，每次迭代需重新校准。

原版 RN app 如果能在模拟器运行，直接截图对比。如果跑不起来，退化为：Android 版单侧截图 + 原版 RN 代码推断预期效果。

### 1.3 优先级评分

每个差异项按三维度打分：

| 维度 | 分值范围 | 说明 |
|------|----------|------|
| 影响面 | 1-5 | 5=核心功能整页缺失, 1=细微UI偏差 |
| 技术可行性 | 1-5 | 5=无前置依赖可直接做, 1=需大量基础工作 |
| 实现成本 | 1-5 | 5=极简改动, 1=需多天工作量 |

**排序采用分层规则**（优先级从高到低）：

1. **依赖前置**：如果 A 是 B 的技术前置依赖，A 排在 B 前面，无论分数
2. **粒度层级**：同层内先按粒度分组——整页缺失 > 页面内功能缺失 > UI 细节偏差
3. **综合分**：同粒度层级内，按 `影响面 × 技术可行性 / 实现成本` 降序排列

取 top 3，混合粒度组合（如 1 粗 + 1 中 + 1 细）。

### 1.4 分析报告输出格式

文件：`docs/convergence/iteration-N/analysis.md`

```markdown
# 迭代 N 差异分析报告

## 当前状态快照
- 页面覆盖率: X/19
- PluginApi 覆盖率: X/14
- 面板/弹窗覆盖率: X/N（N 由本次盘点确定）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | ...    | 粗   | 缺失 | 5      | 4      | 3    | 6.7    | src/pages/... |

## Top 3 推荐
### 功能点 1: XXX
- 粒度: 粗/中/细
- 原版参考: <RN 文件路径列表>
- 实现范围: <概述>
- 前置依赖: <有/无，说明>

### 功能点 2: XXX
...

### 功能点 3: XXX
...

## 截图对比
| 页面 | 原版截图 | Android版截图 | 差异说明 |
|------|----------|--------------|----------|
| ...  | 路径     | 路径         | ...      |
```

## 阶段 2：实现

### 2.1 Subagent 分派策略

主对话确认 top 3 后，**始终串行执行**：按依赖顺序和优先级依次分派 subagent，一个完成后再启动下一个。

串行原因：
- 避免并行 commit 冲突
- 后续功能可利用前序功能的代码/模型变更
- 便于及时发现和修正问题

### 2.2 实现 subagent 工作流

```
1. 读取 iteration-N/analysis.md 中自己负责的功能点描述
2. 参考原版 RN 代码对应文件（路径在分析报告中给出）
3. 编写技术方案：
   - 需要新增/修改哪些文件
   - 数据模型变更
   - UI 组件设计（参考原版对应组件的 rpx 值、颜色 token、布局结构）
4. 实现代码
5. 单元验证：编译通过 + 基本功能可运行
6. UI 截图对比验证：
   a. adb 启动 Android 版，导航到对应页面，截图
   b. 与原版截图（或原版 RN 代码推断的预期效果）并排对比
   c. 记录还原度评估：布局/间距/颜色/字体/交互 逐项打分
   d. 如有明显偏差，当场修正后重新截图
7. git commit
8. 写入技术方案和 UI 对比到 iteration-N/impl-{feature}.md
```

### 2.3 UI 还原度对比记录格式

每个功能点的实现文档中包含：

```markdown
## UI 还原度对比

### 页面: XXX
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | — | — | ✅/⚠️/❌ | |
| 间距/尺寸 | — | — | ✅/⚠️/❌ | rpx值是否对齐 |
| 颜色 | — | — | ✅/⚠️/❌ | theme token对比 |
| 字体/字号 | — | — | ✅/⚠️/❌ | |
| 交互行为 | — | — | ✅/⚠️/❌ | 点击/滑动/动画 |

综合还原度: XX%
原版截图: screenshots/original/xxx.png
Android版截图: screenshots/iteration-N/android-xxx.png
```

**还原度百分比计算**：✅ = 100%, ⚠️ = 50%, ❌ = 0%，取 5 个维度的平均值。例如 3✅ + 1⚠️ + 1❌ = (300 + 50 + 0) / 5 = 70%。

### 2.4 单项完成标准

一个功能点满足以下条件才可 commit：
- 编译通过，基本功能可运行
- UI 还原度 5 个维度中**无 ❌**（所有维度至少 ⚠️）
- 如有 ⚠️ 项，记录为该功能点的遗留改进项，进入 backlog

### 2.5 Commit 规范

```
feat(convergence-N): <简述功能>

- 对应原版: <RN 文件路径>
- 差异项: <分析报告中的编号>
- 变更: <新增/修改的文件列表摘要>
```

### 2.6 实现参考规则

- **UI 还原度**：以原版 RN 代码中的 rpx 值、颜色 token、布局结构为准，必须有截图对比验证
- **插件 API**：严格对齐原版 pluginManager 中的调用方式和返回值结构
- **数据模型**：对齐 `src/types/*.d.ts` 中的字段定义
- **测试用插件**：使用 `https://13413.kstore.vip/yuanli/yuanli.json` 订阅源验证；本地缓存一份快照于 `test/fixtures/yuanli-snapshot.json` 作为 fallback

## Git 分支策略

每次迭代使用独立分支：

```
master
  └── convergence/iteration-1    # 迭代1分支
        ├── subagent-a commit
        ├── subagent-b commit
        └── subagent-c commit
        → 验证通过后 merge 回 master
  └── convergence/iteration-2    # 迭代2分支（从最新 master 创建）
        └── ...
```

- 实现 subagent 严格串行 commit 到迭代分支
- 验证通过后 merge 回 master

## 失败处理

### 单项实现失败
- 编译错误无法解决、依赖不可用等 → 标记该项为 **blocked**
- 成功项正常 commit，失败项记入 backlog 并注明阻塞原因
- 不阻塞其他功能点的实现和整轮迭代的推进

### 验证发现回归
- 已有功能被破坏 → **阻塞本轮迭代**
- 优先修复回归，修复后重新验证
- 回归修复单独 commit，message 标注 `fix(convergence-N): regression ...`

### 分析结果不准确
- 实现 subagent 发现分析报告有误（如误判缺失） → 在 impl 文档中标注修正
- 验证 subagent 交叉验证分析结论的准确性

## 阶段 3：验证

### 3.1 验证 subagent 工作流

实现阶段全部完成后，验证 subagent 做独立的端到端验收：

```
1. 拉取最新代码，编译运行 Android 版
2. 对本次迭代涉及的每个功能点：
   a. 功能验证：操作完整流程，确认功能可用
   b. UI 截图：与原版同页面截图并排对比
   c. 回归检查：已有功能是否被破坏（快速过一遍主流程）
3. 输出验证报告
```

### 3.2 验证报告格式

文件：`docs/convergence/iteration-N/verification.md`

```markdown
# 迭代 N 验证报告

## 功能验证
| 功能点 | 操作路径 | 结果 | 问题 |
|--------|----------|------|------|
| xxx    | 启动→搜索→输入→点击结果 | ✅通过/❌失败 | 描述 |

## UI 还原度验证
| 页面 | 原版截图 | Android截图 | 综合还原度 | 待改进项 |
|------|----------|-------------|-----------|----------|
| xxx  | 路径     | 路径        | 70%（3✅1⚠️1❌） | 间距偏大  |

## 回归检查
| 已有功能 | 状态 | 备注 |
|----------|------|------|
| 搜索播放 | ✅   |      |
| 本地音乐 | ✅   |      |
| 播放器   | ✅   |      |
| 设置页   | ✅   |      |

## 遗留问题（进入下轮迭代 backlog）
- ...
```

## 文档体系

```
docs/convergence/
├── 2026-03-21-convergence-loop-design.md    # 本设计文档
├── STATUS.md                                 # 总体状态跟踪（每轮更新）
├── iteration-1/
│   ├── analysis.md                           # 差异分析报告
│   ├── impl-feature-a.md                     # 功能A: 技术方案 + UI对比
│   ├── impl-feature-b.md                     # 功能B: 技术方案 + UI对比
│   ├── impl-feature-c.md                     # 功能C: 技术方案 + UI对比
│   └── verification.md                       # 验证报告
├── iteration-2/
│   └── ...
└── screenshots/
    ├── original/                              # 原版截图（可跨迭代复用）
    └── iteration-N/                           # 每轮 Android 版截图
```

### STATUS.md 格式

```markdown
# MusicFree 收敛状态

## 功能覆盖率
- 页面: X/19
- PluginApi: X/14
- 面板/弹窗: X/N（N 由最新盘点确定）

## 迭代历史
| 轮次 | 日期 | 完成项 | 页面覆盖率变化 | 备注 |
|------|------|--------|---------------|------|
| 1    | 2026-03-21 | ... | 6/19 → 8/19 | ... |

## 当前 Backlog（按综合分降序）
| # | 差异项 | 粒度 | 综合分 | 来源 |
|---|--------|------|--------|------|
| 1 | ...    | ...  | ...    | 迭代N分析/遗留 |
```

每轮迭代结束时更新覆盖率数字、迭代历史和 backlog。

### Backlog 生命周期

分析 subagent 每轮的工作流程：
1. 先读取 STATUS.md 中的现有 backlog
2. 执行全量代码 + 截图差异扫描
3. backlog 中已有项保留原分数（除非环境变化导致重新评估）
4. 新发现的差异项按标准打分
5. 合并为统一优先级列表输出

## 测试环境

- **插件订阅源**: `https://13413.kstore.vip/yuanli/yuanli.json`（本地缓存 fallback: `test/fixtures/yuanli-snapshot.json`）
- **模拟器**: Android 模拟器（API 29+），通过 adb 连接
- **原版 RN app**: `/Users/zili/code/android/MusicFree`（如能在模拟器运行则直接对比，否则基于代码推断）
- **Android 版**: `/Users/zili/code/android/MusicFreeAndroid`

## 迭代终止条件

当满足以下任一条件时，可暂停迭代循环：

1. 核心功能覆盖率达到 90%+（页面 + PluginApi）
2. 用户主动决定暂停
3. 剩余差异项综合分均低于阈值（影响面 ≤ 2 的微调项）
