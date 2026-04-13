# 插件管理链路设计规格

**日期**：2026-04-14  
**范围**：插件引擎状态管理 + 插件管理 UI 对齐 RN 原版  
**前置依赖**：无  
**后续依赖**：搜索+播放链路 spec、页面动画+UI 对齐 spec

## 1. 目标

将 Android 版插件管理能力从"仅安装/卸载"提升到与 RN 原版对齐，覆盖：

- 引擎层：插件启用/禁用、排序、userVariables 存储、env 注入补全、PluginInfo 字段补全、MediaSource 完整返回
- UI 层：三个独立页面（插件列表、排序、订阅管理），FAB 安装菜单，批量操作
- 验证：单元测试 + 对齐验证 + 运行态验证

**不在本 spec 范围**：用户变量编辑 UI、替代插件选择 UI、导入歌曲/歌单 UI、缺失的 JS 模块（cheerio 等）、播放层消费 MediaSource headers。

## 2. 架构方案

采用分层重构方案：

```
PluginMetaStore (DataStore)     ← 新增，插件元数据持久化
       ↑
PluginManager (整合层)          ← 改造，集成 PluginMetaStore
       ↑
PluginListViewModel / etc.      ← 新增，UI 层 ViewModel
       ↑
PluginListScreen / etc.         ← 新增，三个独立页面
```

## 3. 数据层：PluginMetaStore

**新增文件**：`plugin/src/main/java/.../plugin/meta/PluginMetaStore.kt`

使用 DataStore Preferences 存储，单个 preferences 文件。

### 存储 Schema

| Key | 类型 | 说明 |
|-----|------|------|
| `disabled_plugins` | `Set<String>` | 被禁用的插件 platform 集合（默认全部启用） |
| `plugin_order` | `String`（JSON） | 排序列表 `["platform1","platform2",...]` |
| `user_variables_{platform}` | `String`（JSON） | 每个插件的用户变量 `{key: value}` |
| `subscriptions` | `String`（JSON） | 订阅源列表 `[{name, url}]` |

### 核心 API

```kotlin
@Singleton
class PluginMetaStore @Inject constructor(context: Context) {
    // 启用/禁用
    val disabledPlugins: Flow<Set<String>>
    suspend fun setPluginEnabled(platform: String, enabled: Boolean)
    fun isPluginEnabled(platform: String): Flow<Boolean>

    // 排序
    val pluginOrder: Flow<List<String>>
    suspend fun setPluginOrder(order: List<String>)

    // 用户变量
    fun getUserVariables(platform: String): Flow<Map<String, String>>
    suspend fun setUserVariables(platform: String, variables: Map<String, String>)

    // 订阅源
    val subscriptions: Flow<List<SubscriptionItem>>
    suspend fun addSubscription(name: String, url: String)
    suspend fun updateSubscription(index: Int, name: String, url: String)
    suspend fun removeSubscription(index: Int)
}

data class SubscriptionItem(val name: String, val url: String)
```

### 设计要点

- 用"禁用集合"而非"启用集合"，新安装的插件默认启用（与 RN 行为一致）
- pluginOrder 只存排序后的 platform 列表，未在列表中的插件排到末尾
- userVariables 按 platform 分 key 存储，避免单个 key 过大

## 4. 引擎层补全

### 4.1 PluginInfo 字段补全

当前 `PluginInfo` 新增 5 个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| appVersion | String? | 插件要求的最低 app 版本 |
| primaryKey | String? | 插件主键字段名（默认 id） |
| defaultSearchType | String? | 默认搜索类型 |
| cacheControl | String? | 缓存策略（no-cache/no-store） |
| hints | Map<String, List<String>>? | 各功能的提示文本 |

JsBridge 解析 `__plugin` 对象时提取这些新字段。

### 4.2 env 对象注入补全

当前只注入 `{os: "android"}`，补全为：

```javascript
{
    os: "android",
    appVersion: "1.0.0",       // 从 BuildConfig 读取
    lang: "zh-CN",             // 从系统 Locale 读取
    getUserVariables: () => {} // 从 PluginMetaStore 读取当前插件的 userVariables
}
```

`getUserVariables` 在 JS 引擎初始化时注入为 Kotlin 回调函数，每个插件实例的 env 绑定各自的 platform。

### 4.3 MediaSource 返回值补全

当前 JsBridge 只提取 `url`，补全为完整结构：

```kotlin
data class MediaSourceResult(
    val url: String,
    val headers: Map<String, String>?,  // 新增
    val userAgent: String?,              // 新增
    val quality: String?                 // 新增
)
```

JsBridge 解析 `getMediaSource()` 返回值时提取所有字段。本 spec 只负责提取，播放层消费在搜索+播放 spec 处理。

### 4.4 PluginManager 整合 PluginMetaStore

PluginManager 新增能力：

```kotlin
// 排序后的已启用插件列表
fun getSortedEnabledPlugins(): Flow<List<LoadedPlugin>>

// 可搜索插件（已启用 + 支持 search 方法）
fun getSearchablePlugins(): Flow<List<LoadedPlugin>>

// 启用/禁用代理
suspend fun setPluginEnabled(platform: String, enabled: Boolean)

// 排序代理
suspend fun setPluginOrder(order: List<String>)
```

内部实现：combine `loadedPlugins` + `PluginMetaStore.disabledPlugins` + `PluginMetaStore.pluginOrder`，产出排序后的已启用插件列表。

## 5. UI 层：三个独立页面

### 5.1 路由定义

AppNavHost 新增三个路由：

| 路由 | 页面 | 入口 |
|------|------|------|
| `PluginListRoute` | 插件列表 | SettingsScreen "插件管理" 入口 |
| `PluginSortRoute` | 插件排序 | PluginListScreen 菜单 > 排序 |
| `PluginSubscriptionRoute` | 订阅管理 | PluginListScreen 菜单 > 订阅设置 |

所有路由统一配置 `slide_from_right` 动画（slideIntoContainer/slideOutOfContainer，时长 100ms）。

### 5.2 PluginListScreen（插件列表）

**TopAppBar**：
- 左侧返回箭头
- 标题"插件管理"
- 右侧溢出菜单（三点图标）：订阅设置、排序、卸载全部

**插件卡片**（LazyColumn）：
- 每项展示：插件名称 + 启用/禁用 Switch + 版本/作者信息
- 操作按钮组（根据插件能力动态显示）：更新、分享（复制 srcUrl）、卸载
- 禁用状态的卡片降低透明度

**FAB**：右下角浮动按钮，点击弹出选项菜单：
- 从本地安装（调用文件选择器，支持 .js）
- 从网络安装（弹出 URL 输入 Dialog，maxLength 200）
- 更新全部插件
- 更新订阅（遍历所有订阅源批量安装）

**安装结果反馈**：
- 全部成功：Toast/Snackbar 提示
- 部分失败：Snackbar + 详情 Dialog（列出每个 URL 的成功/失败状态和原因）
- 全部失败：同上

**ViewModel**：`PluginListViewModel`
- 注入 PluginManager
- 暴露：已排序插件列表 Flow、安装状态 StateFlow
- 方法：install/update/uninstall/toggleEnabled/updateAll/updateSubscriptions

### 5.3 PluginSortScreen（插件排序）

**TopAppBar**：
- 左侧返回箭头
- 标题"插件排序"
- 右侧"完成"文本按钮

**排序列表**：
- 使用 `sh.calvin.reorderable:reorderable` 库
- 每项显示：拖拽手柄图标 + 插件名称
- 长按触发拖拽
- 点击"完成"调用 `PluginManager.setPluginOrder()` 保存

**ViewModel**：`PluginSortViewModel`
- 加载当前排序后的插件列表
- 维护本地排序状态
- 保存时写入 PluginMetaStore

### 5.4 PluginSubscriptionScreen（订阅管理）

**TopAppBar**：
- 左侧返回箭头
- 标题"订阅设置"

**订阅列表**（LazyColumn）：
- 每项显示：订阅名称 + URL（单行截断）+ 右侧复制图标
- 点击项弹出编辑 Dialog（预填 name/url + 删除按钮）
- 右侧图标点击复制 URL 到剪贴板

**FAB**：点击弹出添加 Dialog

**Dialog 结构**（添加/编辑共用）：
- 订阅名称输入框
- URL 输入框（验证：必须以 .js 或 .json 结尾）
- 编辑模式额外显示"删除"按钮
- 取消/保存按钮

**空状态**：居中提示文字

**ViewModel**：`PluginSubscriptionViewModel`
- 注入 PluginMetaStore
- 暴露：订阅列表 Flow
- 方法：add/update/remove/copyUrl

## 6. 从 SettingsScreen 迁移

现有 SettingsScreen 中的插件管理相关代码（插件列表、安装对话框等）迁移到 PluginListScreen。SettingsScreen 保留"插件管理"入口卡片，点击导航到 PluginListRoute。

移除 SettingsScreen 中的：
- 插件列表渲染逻辑
- URL 安装 / 本地安装 Dialog
- 默认订阅导入按钮（改为订阅管理页面）
- SettingsViewModel 中的插件相关方法

## 7. 验证策略

### 7.1 单元测试（自动化，CI 运行）

| 测试组 | 覆盖范围 |
|--------|---------|
| PluginMetaStore | enabled/disabled 读写、order 持久化和排序、userVariables CRUD、subscriptions CRUD |
| PluginInfo 解析 | 从 JS `__plugin` 提取全部字段（含新增 5 个），缺失字段默认值 |
| env 注入 | JS 侧能读到 os、appVersion、lang、getUserVariables()，返回值正确 |
| MediaSource 返回 | getMediaSource() 提取 url + headers + userAgent + quality |
| getSortedEnabledPlugins | 排序 + 禁用过滤组合逻辑 |

测试方式：mock JS 插件文件 + QuickJS 引擎加载 + 断言返回值。

### 7.2 对齐验证（手动，引擎改动后执行）

用同一个真实插件文件分别在 RN 和 Android 上运行，对比：
1. PluginInfo 字段完整性
2. search() 返回的 MusicItem 字段
3. getMediaSource() 返回的完整字段
4. env 对象可见性

### 7.3 运行态验证（设备上执行）

端到端场景：
- 安装插件 → 启用/禁用 → 搜索确认禁用后不出现
- 修改排序 → 确认搜索页插件 tab 顺序变化
- 添加订阅源 → 更新订阅 → 确认新插件安装成功
- FAB 菜单四种安装方式逐一验证
- 安装失败的详情展示

## 8. 不在范围内（后续 spec）

- 用户变量编辑 UI
- 替代插件选择 UI
- 导入歌曲/歌单 UI
- JS 模块补齐（cheerio、URL polyfill、webdav 等）
- 播放层消费 MediaSource 的 headers/userAgent
- 搜索二层 tab（媒体类型 + 插件源）
- 歌词 UI
- 音质降级策略
- 播放队列持久化
