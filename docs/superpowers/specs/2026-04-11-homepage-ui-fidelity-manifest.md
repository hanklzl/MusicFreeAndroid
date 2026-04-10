# 首页 UI Fidelity 黄金数据态 Manifest

## 版本

- Manifest ID: `home-ui-fidelity-2026-04-11-v1`
- RN 参考提交：`a94d782552466b08f5e5dc78fa76eef5638782c4`
- Android 规划参考提交：`cb8eaa8d9833765566cb80af51e7c3f2a7a52d1c`
- 关联 spec：
  - [2026-03-25-home-fidelity-design.md](/Users/zili/code/android/MusicFreeAndroid/docs/superpowers/specs/2026-03-25-home-fidelity-design.md)
  - [2026-04-11-homepage-ui-fidelity-design.md](/Users/zili/code/android/MusicFreeAndroid/docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-design.md)

## 用途

本 manifest 是首页 UI fidelity 的具体 source of truth，用于锁定首页截图、录屏和 `uiautomator dump` 验收时必须恢复到的固定数据态。未恢复到本 manifest 定义的状态前，不允许关闭首页 UI 差异。

## 黄金样本继承

设备和环境继续继承旧 spec 中已经锁定的黄金样本：

- 设备：`emulator-5554`
- AVD：`Medium_Phone_API_36.0`
- 分辨率：`1080 x 2400`
- density：`420`
- font scale：`1.0`
- 语言/地区：`en-US`

## 首页全局状态

| 项目 | 值 |
|------|----|
| Drawer 初始状态 | Closed |
| 当前选中 tab | `我的歌单` |
| 迷你播放器 | Visible |
| 迷你播放器标题 | `In the End` |
| 迷你播放器歌手 | `Linkin Park` |
| 受控空态片段 | None |

## 我的歌单

| 顺序 | ID | 标题 | 副文案 | 封面来源 | 备注 |
|------|----|------|--------|----------|------|
| 1 | `playlist-nihao` | `nihao` | `0 首歌曲` | `default-note-placeholder` | 对齐当前首页截图中的默认占位歌单 |
| 2 | `playlist-night-drive` | `夜间驾驶` | `2 首歌曲` | `album-default` | 固定非空样例歌单 |

## 收藏歌单

| 顺序 | ID | 标题 | 副文案 | 右侧平台标记 | 封面来源 | 备注 |
|------|----|------|--------|--------------|----------|------|
| 1 | `starred-city-neon` | `城市霓虹` | `网易云音乐官方` | `网易云` | `plugin-sheet-city-neon` | 固定插件歌单样例 |
| 2 | `starred-weekend-clear` | `周末晴天` | `QQ音乐精选` | `QQ` | `plugin-sheet-weekend-clear` | 固定插件歌单样例 |

## 首页可见片段顺序

首页首屏和整页滚动验收默认使用以下结构顺序：

1. `HomeNavBar`
2. `HomeOperations`
3. `HomeSheetsHeader`
4. `HomeSheetsList`
5. Mini player 固定悬浮在底部

Drawer 单独在打开态采集，不与首页主内容首屏截图混采。

## 采集要求

- 首页静态截图默认采集：
  - Drawer 关闭态首页首屏
  - Drawer 打开态
- 动态录屏默认采集：
  - Drawer 打开
  - Drawer 关闭
  - `我的歌单 -> 收藏歌单`
  - 四宫格任一入口按压
- `uiautomator dump` 必须与对应截图或录屏采集处于同一状态

## 恢复契约

后续实现计划必须提供一条可重复执行的恢复流程，使 RN 和 Android 两侧都进入本 manifest 描述的状态。

最低要求：

- 首页默认进入 `我的歌单`
- `我的歌单` 恰好 2 条且顺序固定
- `收藏歌单` 恰好 2 条且顺序固定
- 迷你播放器可见，展示 `In the End / Linkin Park`
- Drawer 默认关闭

## 变更规则

- 若首页黄金数据态需要调整，必须创建新的 versioned manifest，而不是直接覆写本文件的语义
- 若 RN 参考提交或 Android 参考提交变化导致基线失效，必须更新 manifest version 并重新采集证据
