# 首页 UI 对齐设计 — Mock 数据驱动

**日期**: 2026-04-14
**状态**: 当前规范
**范围**: 首页主体 + MiniPlayer，竖屏 only，mock 数据驱动

## 背景

当前 Android 首页已有基本结构（NavBar、操作卡片、歌单 Tab、侧边栏），但与 RN 原版存在若干结构和交互差异。本轮工作目标是在现有实现基础上增量调整，使首页 UI 结构和交互对齐 RN 原版。

## 方案

增量调整（方案 A）：在现有代码上逐项补齐缺失结构和交互，不重写已对齐的部分。

## 排除项

- 侧边栏（已做过一轮 UI 还原，本轮不动）
- 横屏布局（后续处理）
- 封面图片（歌单列表和 MiniPlayer 的封面暂不处理）
- 歌单交互（长按、新建、导入面板均不做，只保留列表展示 + Tab 切换）

## 差异清单

| # | 区域 | 问题 | 动作 |
|---|------|------|------|
| 1 | 搜索栏宽度 | 没有占满 NavBar 剩余宽度 | 修复 weight 填充 |
| 2 | NavBar 与操作卡片间距 | 间距过大 | 缩小 margin 对齐 RN rpx(32) |
| 3 | 歌单 Tab header 对齐 | 文字与右侧图标没有垂直居中 | 修复 verticalAlignment |
| 4 | 歌单右侧删除图标 | 缺失 | 补上（不响应点击） |
| 5 | "我喜欢" 心形遮罩 | 缺失 | 封面占位图上叠加心形 overlay |
| 6 | 歌单副标题格式 | "18 首歌曲" → 应为 "18首" | 对齐 RN 格式 |
| 7 | MiniPlayer 播放按钮 | 缺少圆形进度环 | 新增 CircularProgressRing |
| 8 | MiniPlayer 歌曲信息 | 双行 → 应为单行 + 左右滑动切歌 | 重构布局 + 手势 |
| 9 | MiniPlayer 整体尺寸 | 偏矮 | 对齐 rpx(132) |

## 首页主体修复设计

### #1 搜索栏宽度

`HomeNavBar` 中搜索栏 Composable 添加 `Modifier.weight(1f)`，使其填满 menu 按钮右侧的剩余空间。

### #2 NavBar 与操作卡片间距

检查 `HomeNavBar` 底部 padding 和 `HomeOperations` 顶部 margin，对齐 RN 的 `rpx(32)` 上下间距。

### #3 歌单 Tab header 垂直对齐

`HomeSheetsHeader` 的 Row 设置 `verticalAlignment = Alignment.CenterVertically`，确保 tab 文字和右侧 +/导入 图标居中。

### #4 歌单右侧删除图标

在 `HomeSheetRow` 末尾追加 trash-outline 图标（不响应点击）。第一项"我喜欢"不显示删除图标（与 RN 一致）。

### #5 "我喜欢" 心形遮罩

在默认歌单的封面占位图上叠加一个心形图标 overlay。优先从 RN 资源目录查找是否有现成的 heart icon 可复用。

### #6 歌单副标题格式

将 `"{count} 首歌曲"` 改为 `"{count}首"`。

## MiniPlayer 详细设计

### 布局结构

```
MiniPlayerContent (Row, height = rpx(132), bg = musicBar)
├── MusicInfo (Modifier.weight(1f), 可滑动区域)
│   └── 水平滑动 Carousel (3 帧: prev / current / next)
│       └── BarMusicItem (Row)
│           ├── 圆形封面占位 (rpx(96))
│           └── Text: "歌名 - 艺术家" (单行, ellipsis)
│
├── CircularPlayBtn (rpx(72) 直径)
│   ├── 外圈: 进度环 (active stroke rpx(4), inactive stroke rpx(2))
│   └── 中心: play/pause 图标
│
└── QueueButton (rpx(56))
    └── playlist 图标
```

### 交互行为

| 手势 | 行为 |
|------|------|
| 点击 MusicInfo 区域 | 打开全屏播放器 |
| 左滑 MusicInfo | 切下一首（带动画过渡） |
| 右滑 MusicInfo | 切上一首（带动画过渡） |
| 点击播放按钮 | 播放/暂停切换 |
| 点击队列按钮 | 打开播放队列 |

滑动判定（对齐 RN）：水平位移 > 30% 宽度或速度 > 1500px/s 时触发切歌。

### 数据模型

```kotlin
data class MiniPlayerUiModel(
    val coverUri: String?,
    val title: String,        // "歌名 - 艺术家" 单行格式
    val isPlaying: Boolean,
    val progress: Float,      // 0f..1f 播放进度
    val hasPrev: Boolean,     // 是否有上一首
    val hasNext: Boolean,     // 是否有下一首
    val prevTitle: String?,   // 上一首标题
    val nextTitle: String?,   // 下一首标题
)
```

Mock 场景：固定 3 首歌循环，progress 固定 0.35f。

### 圆形进度环

用 Compose `Canvas` + `drawArc` 实现：
- 底圈：`musicBarText` 色，20% opacity，stroke rpx(2)
- 进度弧：`musicBarText` 色，100% opacity，stroke rpx(4)
- 圆心：play/pause 图标

## 涉及文件

### 首页主体
- `feature/home/.../component/HomeNavBar.kt` — #1 搜索栏宽度, #2 间距
- `feature/home/.../component/HomeOperations.kt` — #2 间距
- `feature/home/.../sheets/HomeSheetsHeader.kt` — #3 垂直对齐
- `feature/home/.../sheets/HomeSheetsList.kt` — #4 删除图标, #5 心形遮罩, #6 副标题格式
- `feature/home/.../HomeMockVisualFactory.kt` — mock 数据调整

### MiniPlayer
- `feature/player-ui/.../component/MiniPlayerContent.kt` — 布局重构
- `feature/player-ui/.../component/MiniPlayerUiModel.kt` — 模型扩展
- `app/.../MainActivity.kt` — mock 数据更新

## RN 参考文件

- `../MusicFree/src/pages/home/components/navBar.tsx`
- `../MusicFree/src/pages/home/components/homeBody/operations.tsx`
- `../MusicFree/src/pages/home/components/homeBody/sheets.tsx`
- `../MusicFree/src/components/musicBar/index.tsx`
- `../MusicFree/src/components/musicBar/musicInfo.tsx`
