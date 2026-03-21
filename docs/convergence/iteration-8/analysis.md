# 迭代 8 差异分析报告

## 本轮触发问题（来自真实端上 logcat）
- 默认订阅导入后，插件可见但搜索返回“暂无搜索结果”。
- 关键异常：
  - `元力WY`: `JS async error: cannot read property 'Utf8' of undefined`
  - `元力KW`: `JS async error: not a function`

## 根因定位
- Android QuickJS 运行时当前 `__require` 仅支持 `axios`，其它模块返回空对象。
- 真实订阅插件（`wy.js/kw.js/xiaomi.js`）依赖 CommonJS 模块：`crypto-js`、`qs`、`he`、`big-integer`、`dayjs`。
- `axios` 仅挂在 `globalThis.axios`，但很多插件按 TS CommonJS 产物调用 `require('axios').default(...)`，导致 `default` 缺失时报 `not a function`。

## 本轮目标
- 补齐 Android 侧 `require()` 兼容层，优先覆盖真实订阅搜索链路依赖。
- 保持插件安装/加载机制不变，只修复运行时模块注入能力。
