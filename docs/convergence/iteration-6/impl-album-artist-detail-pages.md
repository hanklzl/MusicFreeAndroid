# 功能A/B/C：新增 AlbumDetail 与 ArtistDetail 页面并接入 MusicDetail 导航

## 差异项来源
- analysis 编号: #1（`albumDetail/artistDetail` 页面缺失）
- analysis 编号: #3（`musicDetail` 导航链路待扩展）
- 对应原版:
  - `src/pages/albumDetail/index.tsx`
  - `src/pages/artistDetail/index.tsx`
  - `src/pages/musicDetail/*`

## 技术方案
### 1) 新增路由
- `AlbumDetailRoute`
- `ArtistDetailRoute`

### 2) 新增 AlbumDetail 页面链路
- `AlbumDetailViewModel`：
  - 调用 `getAlbumInfo` 拉取分页歌曲
  - 支持 `loadMore`
  - 点击歌曲解析 `getMediaSource` 后下发播放器
- `AlbumDetailScreen`：
  - 顶栏、列表、错误态、加载更多

### 3) 新增 ArtistDetail 页面链路
- `ArtistDetailViewModel`：
  - 调用 `getArtistWorks(type = "music")`
  - 支持 `loadMore`
  - 点击歌曲解析后播放
- `ArtistDetailScreen`：
  - 顶栏、列表、错误态、加载更多

### 4) 扩展 MusicDetail 导航
- `MusicDetailScreen` 增加：
  - `onOpenAlbumDetail`
  - `onOpenArtistDetail`
- `AppNavHost`：
  - `musicDetailScreen` 接线 album/artist 路由
  - 新增 `albumDetailScreen(...)`
  - 新增 `artistDetailScreen(...)`

### 5) 路由测试
- `RoutesTest` 新增：
  - `AlbumDetailRoute is serializable`
  - `ArtistDetailRoute is serializable`

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/navigation/MusicDetailNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/navigation/AlbumDetailNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/navigation/ArtistDetailNavigation.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅

## UI 还原度对比
### 页面: 专辑详情 / 歌手详情
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `src/pages/albumDetail/*`, `src/pages/artistDetail/*` | 新增 Compose 页面 | ⚠️ | 主体结构已具备，截图待补 |
| 间距/尺寸 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待稳定环境截图复核 |
| 颜色 | RN 深色风格 | 新增 Compose 页面 | ⚠️ | 当前沿用现有主题体系 |
| 字体/字号 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待截图复核 |
| 交互行为 | 分页加载 + 点击播放 | ViewModel 已接线 | ⚠️ | 真实插件数据态待复验 |

综合还原度: 50%（0✅5⚠️0❌）
原版截图: `docs/convergence/screenshots/original/rn-album-detail.png`, `docs/convergence/screenshots/original/rn-artist-detail.png`（缺失）
Android版截图: 暂缺（待补）

## 遗留项
- 在稳定插件环境补做 `musicDetail -> albumDetail/artistDetail` 端到端截图与数据态验收。
