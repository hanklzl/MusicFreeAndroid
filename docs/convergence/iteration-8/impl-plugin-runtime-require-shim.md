# 迭代 8 实现记录：插件运行时 require 兼容补齐

## 改动范围
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/RequireShim.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- `plugin/src/main/assets/jslibs/*`

## 关键实现
1. 新增 `RequireShim`，统一注册 CommonJS 模块缓存与 `__require`
- 在 QuickJS 中建立 `globalThis.__requireCache`。
- 从 assets 加载并注册模块：
  - `crypto-js`
  - `qs`
  - `he`
  - `dayjs`
  - `big-integer`
- 每个模块使用 CommonJS 包装执行（`module.exports`），并自动补齐 `exports.default = exports` 兼容 TS 转译调用。
- `__require` 先查缓存，找不到模块时返回空对象并打 warning（保持插件加载过程可继续）。

2. `axios` 兼容增强
- 在 `AxiosShim` 注册时增加：`axios.default = axios`。
- 兼容 `require('axios').default.get(...)` 与 `(0, axios.default)(...)` 风格调用。

3. `PluginManager` 接线替换
- 删除“仅支持 axios”的旧 `__require` 分支。
- 改为 `RequireShim.register(...)` 统一完成模块注入。

## 资产文件（新增）
- `plugin/src/main/assets/jslibs/crypto-js.js`
- `plugin/src/main/assets/jslibs/qs.js`
- `plugin/src/main/assets/jslibs/he.js`
- `plugin/src/main/assets/jslibs/dayjs.min.js`
- `plugin/src/main/assets/jslibs/BigInteger.min.js`

## 预期收益
- 修复 `CryptoJS.enc.Utf8` 未定义导致的 WY 搜索异常。
- 修复 `axios/default` 与 `he` 依赖缺失导致的 KW 搜索异常。
- 为后续真实订阅插件的搜索/播放链路提供统一运行时依赖基础。
