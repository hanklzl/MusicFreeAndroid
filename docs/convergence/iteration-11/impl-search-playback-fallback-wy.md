# 迭代 11 实现说明（播放失败回退元力WY）

## 变更文件
- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/MusicMatch.kt`
- `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/MusicMatchTest.kt`

## 关键实现
1. 搜索页播放兜底策略
- 主路径：优先调用当前选中插件 `getMediaSource`。
- 兜底路径：当主路径失败且当前插件不是 `元力WY` 时：
  - 用 `title + artist` 在 `元力WY` 执行 `search(page=1)`；
  - 通过匹配算法挑选最佳候选；
  - 调用 `元力WY.getMediaSource()` 获取可播地址；
  - 替换队列中的点击项并开始播放。

2. 匹配算法（`MusicMatch`）
- 标题匹配：完全一致 > 包含关系。
- 歌手匹配：完全一致 > 包含关系。
- 时长匹配：3s 内额外加分，10s 内次级加分。
- 最低接受分限制，避免误命中无关歌曲。

3. 队列替换行为
- 若点击项在队列中：仅替换该位置并从该位置播放。
- 若不在队列中：将解析后的歌曲插入队首并从 0 播放。
