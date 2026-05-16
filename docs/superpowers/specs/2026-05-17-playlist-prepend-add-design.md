# 添加歌曲到歌单首部设计

- **状态**：当前规范（草案）
- **日期**：2026-05-17
- **适用范围**：`:data` 歌单写入顺序、所有复用 `PlaylistRepository.addMusicToPlaylist()` / `addMusicsToPlaylist()` 的添加歌单入口
- **关系**：是 [2026-05-04-playlist-feature-design.md](2026-05-04-playlist-feature-design.md) 与 [2026-05-16-playlist-batch-edit-design.md](2026-05-16-playlist-batch-edit-design.md) 的增量修正
- **RN 参考**：`../../../../MusicFree/src/core/musicSheet/index.ts`、`../../../../MusicFree/src/core/musicSheet/sortedMusicList.ts`

## 1. 背景

Android 当前歌单写入逻辑集中在 `PlaylistRepository`：

- 单曲入口调用 `addMusicToPlaylist(playlistId, item)`。
- 批量编辑、插件歌单导入、插件详情页批量添加等入口调用 `addMusicsToPlaylist(playlistId, items)`。
- 底层 `playlist_music.sortOrder` 由 `maxSortOrderInPlaylist() + 1` 生成，`observeMusicInPlaylist()` 在手动排序模式下按 `sortOrder ASC` 展示。

因此，当前所有添加入口的可见行为都是“追加到末尾”。

RN 原版 `MusicSheet.addMusic()` 会把新增歌曲交给 `SortedMusicList.add()`。当歌单无显式排序时，`SortedMusicList.add()` 使用 `musicItems.concat(this.array)`，也就是把新增歌曲作为一个批次放到歌单首部；批次内部保持传入顺序。Android 需要对齐这个默认行为。

## 2. 目标

1. 所有“添加到歌单”入口统一变为首部插入。
2. 批量添加时，新增批次整体插入到首部，并保持传入列表的内部顺序。
3. 重复歌曲继续跳过，不改变去重语义；未重复的新歌曲仍按批次顺序插入首部。
4. 不改 UI，不新增配置，不改变用户选择歌单的交互。
5. 不改数据库 schema，不新增 migration。

## 3. 非目标

- 不增加“添加到首部 / 末尾”的用户设置。
- 不改歌单详情页的排序菜单和排序模式枚举。
- 不重做 `MusicListEditorLite`、`AddToPlaylistBottomSheet`、插件导入面板等 UI。
- 不改变 `removeMusicFromPlaylist()`、手动排序回写、封面选择、歌单 CRUD 逻辑。

## 4. 行为规则

### 4.1 单曲添加

当歌单手动排序且已有 `[A, B]` 时，连续添加 `C` 后展示为：

```text
[C, A, B]
```

再添加 `D` 后展示为：

```text
[D, C, A, B]
```

### 4.2 批量添加

当歌单手动排序且已有 `[X, Y]` 时，批量添加 `[A, B, C]` 后展示为：

```text
[A, B, C, X, Y]
```

这与 RN 批量添加时“新批次置顶，但批次内部不反转”的行为一致。

### 4.3 重复项

当歌单已有 `[A, X, Y]`，批量添加 `[A, B, C]` 时：

```text
[B, C, A, X, Y]
```

`A` 因 `(playlistId, musicId, musicPlatform)` 已存在而跳过；`B`、`C` 作为本次新增批次插到首部，并保持相对顺序。

### 4.4 非手动排序

`SortMode.Title / Artist / Album / Newest / Oldest` 仍由 `observeMusicInPlaylist().applySort(mode)` 决定最终可见顺序。首部插入只改变底层手动顺序；当用户切回 `Manual` 时，新增歌曲应出现在手动顺序首部。

批量添加应尽量使用同一个 `addedAt` 时间戳，使 `Newest / Oldest` 在相同批次内能稳定保持传入顺序，贴近 RN `MusicSheet.addMusic()` 为批次打同一 `$timestamp` 的语义。

## 5. 数据层设计

改动集中在 `PlaylistRepository` 和 `PlaylistDao`。

### 5.1 sortOrder 分配

推荐使用“向当前最小 sortOrder 前方分配”的方式，而不是每次整体平移已有行：

1. DAO 新增 `minSortOrderInPlaylist(playlistId)`，空歌单返回 `0`。
2. 单曲添加时使用 `minSortOrder - 1`。
3. 批量添加时按请求列表下标分配连续前置区间：

```kotlin
val baseOrder = playlistDao.minSortOrderInPlaylist(playlistId) - items.size
items.forEachIndexed { index, item ->
    val sortOrder = baseOrder + index
    insertCrossRefIgnore(... sortOrder = sortOrder ...)
}
```

这样可以避免对已有歌单全量 `UPDATE sortOrder = sortOrder + n`，对大歌单和重复添加更稳。`sortOrder` 允许为负数；DAO 已按 `ASC` 排序，负数天然位于现有 `0..N` 之前。

若未来极端场景接近 `Int.MIN_VALUE`，再补一次手动顺序压缩即可；正常用户路径不会触达该边界，本次不引入额外复杂度。

### 5.2 API 边界

不新增公开 repository API。继续使用：

```kotlin
suspend fun addMusicToPlaylist(playlistId: String, item: MusicItem): Boolean
suspend fun addMusicsToPlaylist(playlistId: String, items: List<MusicItem>): Int
```

内部将 `addMusicToPlaylistNoCoverSync()` 改成可接收 `sortOrder` / `addedAt` 的私有 helper，避免单曲和批量路径重复逻辑。

### 5.3 事务与封面

`addMusicsToPlaylist()` 继续在 `db.withTransaction` 内完成批量写入，返回真实新增数量。封面同步保持当前策略：事务后按成功插入的歌曲顺序尝试 `syncPlaylistCoverIfNeeded()`，第一首能成功提供封面的歌曲即可结束。

`addMusicToPlaylist()` 保持现有日志、去重、封面同步语义，只改变新 cross-ref 的 `sortOrder`。

## 6. 影响面

因为所有入口都经由 repository，本次不需要逐个修改 ViewModel。预期自动覆盖：

- 搜索结果添加到歌单。
- 播放页更多菜单添加到歌单。
- 本地歌单详情行菜单添加到其他歌单。
- 插件歌单详情 / 榜单详情的单曲或批量添加。
- 插件歌单导入后的添加到目标歌单。
- `MusicListEditorLite` 批量选择后添加到其他歌单或新建歌单。
- 收藏到默认 `我喜欢` 歌单。

## 7. 测试策略

以 `data/src/androidTest/java/.../PlaylistRepositoryTest.kt` 为主：

1. 调整 `addAndObserveMusicInPlaylist`，验证连续单曲添加后最新歌曲位于首部。
2. 调整 `addMusicsToPlaylist_preservesImportOrderForManualSort`，验证批量添加在首部保持 `[A, B, C]` 顺序。
3. 新增重复项 case：已有 `[A, X, Y]`，添加 `[A, B, C]` 后为 `[B, C, A, X, Y]`。
4. 保留空列表返回 0、重复添加返回 0、封面自动同步、排序模式相关测试。

后续实现验证闸门：

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

如本地无可用设备 / 模拟器，至少先跑可执行的 JVM 测试与 Debug 构建，并在最终结论里明确运行态测试缺口。
