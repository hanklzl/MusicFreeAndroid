# Logan Log Viewer (H5) Design

> 文档状态：当前规范
> 适用范围：开发者本地排查用 Logan 日志可视化 H5 工具（`tools/logan-viewer/`）。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：[Logan Logging System Design](./2026-05-05-logging-system-design.md)、[LoganLocalDecoder.java](../../../tools/logan/LoganLocalDecoder.java)、[decode-logan.sh](../../../tools/logan/decode-logan.sh)、[FeedbackLogExporter.kt](../../../logging/src/main/java/com/hank/musicfree/logging/FeedbackLogExporter.kt)、[LogEventFormatter.kt](../../../logging/src/main/java/com/hank/musicfree/logging/LogEventFormatter.kt)
> 最后校验：2026-05-23

## 背景

MusicFreeAndroid 已正式发布，反馈日志通过 `FeedbackLogExporter.kt` 打包成
`musicfree-feedback-*.zip`（`manifest.json` + Logan AES-128-CBC 加密文件）。当前
排查依赖命令行 `tools/logan/decode-logan.sh` 解密成纯文本 JSON，再人工 `jq`
查询。

70+ 业务事件 + `sessionId` / `traceId` 结构化字段已经齐备，但缺一个图形化工具
把「一次用户操作的所有事件」按时间线折叠展示——尤其在 trace 维度上，"一次操作
的所有事件"折成一行后操作链路一眼可见，比逐行 `jq` 更适合复现用户报障。

## 目标

1. 在 `tools/logan-viewer/` 提供一个独立的纯静态 H5 工具，不与 Android Gradle
   工程耦合。
2. 浏览器内一站式：拖入原始反馈 zip → 解压 → 解密 → 帧切分 → 行 JSON 解析 →
   时间线渲染，无需先跑命令行。
3. 时间线主序：按 `sessionId` 分会话，会话内按 `traceId` 自动折叠成「操作组」。
4. 错误突出：含 `error` 的 trace 组默认展开 + 红条；左栏 session 卡片显示错误数。
5. 可发布到 GitHub Pages，团队与外部协作者一个 URL 即可使用。
6. 关键路径（解密 / 解析 / 索引 / 分组）有 vitest 覆盖，并提供能跟
   `decode-logan.sh` diff 的 Node CLI 入口。

## 非目标

1. 不做多日志包对比 / 跨版本 diff。
2. 不做泳道图（swimlane）/ Gantt 视图。
3. 不做半自动诊断（缓存命中率、耗时直方图、调用图等）。
4. 不做 URL 深链 / 共享某条事件链接。
5. 不做 e2e 测试（单测 + 手测覆盖即可）。
6. 不做应用内浏览（应用内日志查看由其它入口承担，本工具只面向反馈包）。

## 关键产品决策

| # | 决策 | 选择 |
|---|------|------|
| 1 | 目标用户 | 仅开发者本地排查 |
| 2 | 核心目标 | 重现用户操作链路（时间线） |
| 3 | 部署 | 纯静态 SPA → GitHub Pages |
| 4 | 输入 | 直接接原始 `.zip`，浏览器内解密 |
| 5 | 时间线主序 | 按 `sessionId` 分会话；会话内按 `traceId` 折叠 |
| 6 | AES key | debug + release 两个 key 都内置（见下） |
| 7 | 前端栈 | Vite + React + TypeScript |
| 8 | trace 折叠默认 | 默认折叠；含 `error` 的组自动展开 |
| 9 | 多包对比 | 初版不做 |

### Release key 内置的 trade-off

release key 内置 → Pages 上任何人都能 fetch key → 能解密任何拿到的反馈包。
缓解：

1. key 只能解密，不能签发 / 伪造日志。
2. 反馈包是用户主动发给开发者的，trust 边界 ≈ "私聊给开发者一份日志"。
3. 日志规范禁止写入 token / 用户原文输入 / 含用户名的绝对路径，由调用方在
   写日志时就约束（见 [Logan Logging System Design](./2026-05-05-logging-system-design.md)）。
4. `src/keys/builtin.ts` 是唯一入口，未来如需收口可改为表单输入或迁移到私有
   部署，切换成本低。

## 设计概览

### 数据流

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       Browser (single static page)                       │
│                                                                          │
│   ┌─────────────┐     ┌────────────────────────┐     ┌──────────────┐    │
│   │ Drop / pick │ ──► │     Web Worker         │ ──► │  Main Thread │    │
│   │ feedback.zip│     │                        │     │   (React)    │    │
│   └─────────────┘     │  JSZip unpack          │     │              │    │
│                       │    │                   │     │  Indices     │    │
│                       │    ▼                   │     │  ┌─────────┐ │    │
│                       │  manifest.json + logan/*│    │  │bySession│ │    │
│                       │    │                   │     │  └─────────┘ │    │
│                       │    ▼                   │     │  ┌─────────┐ │    │
│                       │  WebCrypto AES-128-CBC │     │  │ byTrace │ │    │
│                       │  (两 key 都试，先 debug)│    │  └─────────┘ │    │
│                       │    │                   │     │  ┌─────────┐ │    │
│                       │    ▼                   │     │  │byCategory│ │   │
│                       │  Split Logan frames    │     │  └─────────┘ │    │
│                       │    │                   │     │      │       │    │
│                       │    ▼                   │     │      ▼       │    │
│                       │  JSON.parse per line   │     │  Virtualized │    │
│                       │    │                   │     │   Timeline   │    │
│                       │    ▼                   │     │  + Filters   │    │
│                       │  postMessage progress  │     │  + Detail    │    │
│                       │  + ParsedEvent[]       │     │              │    │
│                       └────────────────────────┘     └──────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

### 主页面 UI 线框

```
┌───────────────────────────────────────────────────────────────────────────────────────┐
│  MusicFree Log Viewer                                              [+ Load .zip]      │
├───────────────────────────────────────────────────────────────────────────────────────┤
│ v1.2.5  release  Pixel 7  Android 14   ·   4 sessions   ·   12,431 events  ·  86 err │
├──────────────┬─────────────────────────────────────────────────────┬──────────────────┤
│  SESSIONS    │ [APP][PLUGIN][PLAYER][SEARCH][DATA]…  Level:[error]│   DETAIL          │
│              │ Search: [_____________________________]   ☐ only err│                  │
│  ▶ #1  ✓     │ ────────────────────────────────────────────────── │  event:           │
│   14:23-     │ 14:23:01.123 [APP]    app_start                     │  plugin_install_  │
│   14:35      │ 14:23:01.234 [APP]    main_activity_create_start    │  failed           │
│   2,431 ev   │ ▶ 14:23:02.456 [PLUGIN] plugin_install  trace=abc   │                  │
│   12  err    │      └─ 3 events · +1.2s · ✓ success                │  level:  error    │
│              │ 14:23:04.789 [PLAYER] resolve_plugin_call_start     │  time:   14:25:08 │
│    #2  ✓     │ 14:23:04.999 [PLAYER] cache_hit  source=local       │  trace:  ghi      │
│   14:36-     │ ▶ 14:23:05.123 [PLAYER] setMediaSource trace=def    │  session: #1      │
│   …          │      └─ 5 events · +234ms · ✓ success               │                  │
│              │ ⚠ 14:25:08.456 [PLUGIN] plugin_install_failed       │  fields:          │
│    #3  ⚠     │     reason="http 404"  durationMs=8123              │  { pluginId, url, │
│              │ 14:25:08.999 [APP]    user_toast_shown              │    http, reason } │
│    #4  ⚠     │ …                                                   │  stack ▼          │
└──────────────┴─────────────────────────────────────────────────────┴──────────────────┘
```

### trace 折叠 / 展开示意

```
COLLAPSED  ── 默认这样，"一次操作"只占一行
─────────────────────────────────────────────────────────────────────
▶ 14:23:02.456  [PLUGIN]  plugin_install   trace=abc   +1.2s   ✓ success
                          └─ 3 events

EXPANDED  ── 点 ▶ 展开
─────────────────────────────────────────────────────────────────────
▼ 14:23:02.456  [PLUGIN]  plugin_install   trace=abc   +1.2s   ✓ success
  │  14:23:02.456  [PLUGIN]  plugin_install_start
  │  14:23:03.100  [PLUGIN]  plugin_download_success  size=42KB
  │  14:23:03.612  [PLUGIN]  plugin_install_success   version=1.0.3

ERROR group ── 含 error 默认展开 + 红条
─────────────────────────────────────────────────────────────────────
▼ 14:25:00.000  [PLAYER]  setMediaSource  trace=def  +8.1s  ⚠ failed
  │  14:25:00.000  [PLAYER]   resolve_plugin_call_start
  │  14:25:00.034  [PLAYER]   cache_miss
  │  14:25:08.123  [PLUGIN]   axios_request   url="https://…"
  │  ⚠ 14:25:08.456 [PLUGIN]  plugin_install_failed  reason="http 404"
```

## 目录结构

```
tools/logan-viewer/
├── README.md                  # 使用说明 + AES key 风险声明
├── package.json
├── tsconfig.json
├── vite.config.ts             # base="/MusicFreeAndroid/logan-viewer/", worker.format='es'
├── vitest.config.ts
├── index.html
├── public/
├── src/
│   ├── main.tsx
│   ├── format/                # 纯函数解析层，无 React 依赖
│   │   ├── zipReader.ts
│   │   ├── loganDecoder.ts
│   │   ├── eventParser.ts
│   │   └── __tests__/
│   ├── model/
│   │   ├── types.ts
│   │   ├── indexer.ts
│   │   └── traceGrouper.ts
│   ├── state/
│   │   └── store.ts           # Zustand
│   ├── worker/
│   │   ├── decode.worker.ts
│   │   └── protocol.ts
│   ├── ui/
│   │   ├── App.tsx
│   │   ├── TopBar.tsx
│   │   ├── SessionList.tsx
│   │   ├── Timeline.tsx
│   │   ├── TimelineRow.tsx
│   │   ├── TraceGroup.tsx
│   │   ├── FilterBar.tsx
│   │   └── DetailDrawer.tsx
│   ├── keys/
│   │   └── builtin.ts
│   └── styles/globals.css
└── scripts/
    └── generate-fixture.mjs
```

仓库根新增：

- `.github/workflows/logan-viewer-pages.yml`
- `.gitignore` 追加：`tools/logan-viewer/node_modules`、`tools/logan-viewer/dist`、
  `tools/logan-viewer/coverage`、`tools/logan-viewer/public/sample-feedback.zip`

## 模块切分与关键接口

### format/zipReader.ts

```ts
export interface ZipEntries {
  manifest: ManifestJson;
  loganFiles: Array<{ name: string; bytes: Uint8Array }>;
}
export async function readFeedbackZip(input: Blob | ArrayBuffer): Promise<ZipEntries>;
```

依赖 JSZip。缺 manifest / 缺 logan/ 抛带 hint 的 Error。

### format/loganDecoder.ts

1:1 复刻 `tools/logan/LoganLocalDecoder.java` 的字节解析：`0x01 [BE-int32
length] [encrypted]` 循环；遇非 `0x01` 字节 `position++` 跳过；PKCS5 失败回退
NoPadding；gunzip 损坏时截到最后一个 `\n`。

```ts
export interface DecodeOptions {
  key: Uint8Array;   // 16 bytes
  iv: Uint8Array;    // 16 bytes
  onProgress?: (bytesConsumed: number, totalBytes: number) => void;
}
export interface DecodedBlock { text: string; sourceFile: string; blockIndex: number; }
export async function decodeLoganFile(
  bytes: Uint8Array,
  sourceFile: string,
  opts: DecodeOptions,
): Promise<DecodedBlock[]>;
```

gunzip 用 `DecompressionStream('gzip')`；末段截断处理需要 partial bytes →
`fflate.gunzipSync` 作为 fallback。

### format/eventParser.ts

```ts
export interface ParseResult {
  events: RawLogEvent[];
  skipped: Array<{ line: number; raw: string; reason: string }>;
}
export function parseDecodedText(text: string, sourceFile: string): ParseResult;
```

### model/types.ts

```ts
export type LogLevel = 'verbose' | 'debug' | 'info' | 'warn' | 'error';
export type LogCategory =
  | 'APP' | 'PLUGIN' | 'SEARCH' | 'PLAYER' | 'PLAYLIST_IMPORT' | 'FEEDBACK'
  | 'DATA' | 'FILE_IO' | 'DOWNLOAD' | 'SETTINGS' | 'HOME' | 'LYRICS'
  | 'UPDATE' | 'RUNTIME' | string;  // 兜底未知 category

export interface ManifestJson {
  generatedAt: string;
  sessionId: string;
  applicationId: string;
  versionName: string;
  versionCode: number;
  buildType: 'debug' | 'release' | string;
  androidSdk: number;
  androidRelease: string;
  deviceManufacturer: string;
  deviceModel: string;
  supportedAbis: string[];
  logStartLastModified: number | null;
  logEndLastModified: number | null;
  files: Array<{ path: string; sizeBytes: number; lastModified: number }>;
}

export interface RawLogEvent {
  level: LogLevel;
  category: LogCategory;
  event: string;
  timestamp: string;           // ISO_OFFSET_DATE_TIME
  sessionId: string;
  traceId?: string;
  durationMs?: number;
  result?: string;             // 'done' | 'fail' | 自定义
  fields?: Record<string, unknown>;
  errorClass?: string;
  errorMessage?: string;
  stackTrace?: string;
}

export interface ParsedEvent extends RawLogEvent {
  id: string;                  // `${sourceFile}#${blockIndex}#${lineIndex}`
  timestampMs: number;
  groupId: string | null;      // `${sessionId}::${traceId}` 或 null
  sourceFile: string;
}

export interface TraceGroup {
  kind: 'group';
  id: string;
  sessionId: string;
  traceId: string;
  category: LogCategory;
  headEvent: ParsedEvent;
  tailEvent: ParsedEvent;
  startMs: number;
  endMs: number;
  durationMs: number;
  events: ParsedEvent[];
  hasError: boolean;
  result: string | null;
}

export interface SessionSummary {
  sessionId: string;
  startMs: number;
  endMs: number;
  eventCount: number;
  errorCount: number;
  categories: LogCategory[];
}

export interface FilterState {
  levels: Set<LogLevel>;
  categories: Set<LogCategory>;
  eventQuery: string;
  onlyErrors: boolean;
  onlyWithTrace: boolean;
}
```

### model/indexer.ts

```ts
export interface EventIndex {
  events: ParsedEvent[];                // 已按 timestampMs 升序
  bySession: Map<string, ParsedEvent[]>;
  byTrace: Map<string, ParsedEvent[]>;  // key = `${sessionId}::${traceId}`
  byCategory: Map<LogCategory, number>;
  sessionSummaries: SessionSummary[];
}
export function buildIndex(raw: RawLogEvent[]): EventIndex;
```

### model/traceGrouper.ts

```ts
export function groupBySession(
  sessionId: string,
  events: ParsedEvent[],
): Array<TraceGroup | { kind: 'single'; event: ParsedEvent }>;
```

无 traceId 事件保持独立行（初版不合并相邻散件）。`hasError = group.some(e =>
e.errorClass)`。

### state/store.ts（Zustand）

```ts
interface ViewerStore {
  status: 'idle' | 'parsing' | 'ready' | 'error';
  progress: { phase: string; pct: number } | null;
  errorMessage: string | null;
  manifest: ManifestJson | null;
  index: EventIndex | null;
  selectedSessionId: string | null;
  selectedEventId: string | null;
  expandedGroupIds: Set<string>;
  filter: FilterState;
  loadZip(file: File): Promise<void>;
  setSelectedSession(id: string): void;
  toggleGroup(groupId: string): void;
  setFilter(patch: Partial<FilterState>): void;
  reset(): void;
}
```

选 Zustand 的理由：state 形状扁平；selector 订阅避免 Context 全量重渲染；包体 < 2KB。

### worker 消息协议

```ts
type ReqId = number;
export type WorkerRequest =
  | { id: ReqId; kind: 'load'; file: ArrayBuffer };
export type WorkerEvent =
  | { id: ReqId; kind: 'progress'; phase: 'unzip' | 'decrypt' | 'parse' | 'index'; pct: number }
  | { id: ReqId; kind: 'done'; manifest: ManifestJson; index: EventIndex }
  | { id: ReqId; kind: 'error'; message: string; cause?: string };
```

key 选择：worker 收到 zip 后**两个 key 都试**（先 debug 后 release），都失败再
抛错。不按 `buildType` 选——因为 `buildType` 可能是 `releaseSigned` 等自定义值。

进度节流：每 phase 最多 50 次回报。

## 性能策略

- 解密 + 解析全部在 Worker；UI 线程只跑虚拟列表与抽屉。
- 虚拟列表选 **`@tanstack/react-virtual`**：支持动态行高（group header 与展开
  高度不同），React 18 friendly。
- 50k 事件 worst case 在 Worker：解析 ~250ms + 索引 ~150ms + 分组 ~80ms <
  500ms。
- 内存：每 ParsedEvent ~1KB；50k 事件 ~50MB。主线程拿 reference 不深拷。
- 扁平化 row 数组：展开 / 折叠只 O(n) 重建，不触发 worker。

## 部署

`vite.config.ts` 要点：

- `base: '/MusicFreeAndroid/logan-viewer/'`
- `worker: { format: 'es' }`，配合 `import DecodeWorker from
  './worker/decode.worker.ts?worker'`
- key 直接 import from `keys/builtin.ts`（不用 define 注入，便于 code review
  可见）

`.github/workflows/logan-viewer-pages.yml`：在 push 到 main 且
`tools/logan-viewer/**` 变更时构建，然后同步到 `gh-pages/logan-viewer/`
子目录并提交。不向 GitHub Secrets 注入 key（已内置）。该 workflow 与
release manifest、站点发布共用 `concurrency: gh-pages-deploy`；站点发布
workflow 必须保留 `logan-viewer/`，避免同步 `docs/site/` 时删除工具产物。
仓库 Settings → Pages 使用 `gh-pages` 分支根目录作为 source。

## 测试策略

- **format 层（Vitest）**：`scripts/generate-fixture.mjs` 用 Node `crypto` +
  `zlib.gzipSync` 反向生成最小 logan 文件 + manifest 打成 zip 作为 fixture。
  覆盖：解密成功、PKCS5→NoPadding 回退、gzip 末尾损坏 truncate、坏 magic byte
  跳过、未知 key 报错。
- **model 层（Vitest，纯函数）**：手写 `RawLogEvent[]` 输入，断言 index /
  grouper 输出。grouper 必测：单 trace 多事件、混入 errorClass、duration 计算、
  空 traceId 退化为单行。
- **state 层**：Zustand store 直接 import，调 `loadZip` 用 mock worker。
- **UI 层（React Testing Library + Vitest jsdom）**：assert 展开 / 折叠、错误
  自动展开、filter 串联。
- **e2e 不做**（YAGNI）。

## 实现里程碑

| 里程碑 | 内容 | 验证点 |
|---|---|---|
| M0 | 本 spec 落地并 commit | `git log` 含 spec |
| M1 | format 层 + keys/builtin + fixture + CLI | vitest 全绿；CLI 解一个真实反馈包并 diff `tools/logan/decode-logan.sh` 输出 |
| M2 | model 层（types / indexer / traceGrouper）+ 单测 | snapshot 测试 |
| M3 | Vite scaffold + 朴素 UI（无虚拟列表 / 无过滤） | `npm run dev` 拖入 zip 能看见会话与事件 |
| M4 | 虚拟列表 + 过滤 + group 折叠 | 50k 合成事件 dev 模式滚动 60fps |
| M5 | Worker + 进度条 | 解析期间 UI 仍可滚动空状态；progress 单调递增 |
| M6 | GitHub Actions + Pages 部署 | push 后 URL 可访问，拖入真实包工作 |

## 端到端验证

1. 从最近的用户反馈邮件 / issue 拿一个 `musicfree-feedback-*.zip`。
2. 本地 `cd tools/logan-viewer && npm run dev`，浏览器拖入 zip。
3. 验收点：
   - 顶栏正确显示 app 版本 / 设备 / sessionId 数量 / 总事件数 / 错误数
   - 左栏列出所有 session，错误 session 标红角标
   - 时间线按 `timestampMs` 升序，category 上色正确
   - 同 traceId 默认折叠；含 error 的 trace 自动展开 + 红条
   - 过滤栏 category / level / event 关键字三者 AND 联动
   - 右抽屉显示选中事件完整 fields JSON 与 stackTrace
4. 跟 `tools/logan/decode-logan.sh` 解码同一个包，事件总数与字段值需要一致
   （`diff` 比对）。
5. `npm run build` 产物在 `npx vite preview` 下正常工作。
6. CI workflow push 后 Pages URL 可访问，拖入真实包工作。

## 风险与待验证项

1. **WebCrypto AES-CBC + 16 字节 IV**：Safari 16 以下未验证。初版 README 注明
   "建议 Chrome / Edge 最新版"。
2. **`DecompressionStream('gzip')` 支持**：Safari 16.4+ / Chrome 80+ / FF 113+。
   低版本 fallback 引入 `fflate`（~35KB gzipped）。M1 决定是否引入。
3. **JSZip 单 zip > 100MB**：先按 < 100MB 设计；超过迁移 `@zip.js/zip.js` 流式
   API。
4. **Logan 帧字节解析陷阱**：扫描时遇到非 `0x01` 字节 `position++` 跳过
   （`LoganLocalDecoder.java` L64），TS 实现必须 1:1 复刻，否则文件头部 / 损坏
   字节会误抛。
5. **AES NoPadding 回退**：Java 端按 `BadPaddingException` 分支；WebCrypto 失败
   统一 `OperationError`，TS 端按 catch-all 回退。
6. **gzip 末段截断恢复**：`DecompressionStream` 不易截 partial bytes；可能要改
   `fflate.gunzipSync`。若仅用 stream，损坏块直接丢弃并在 `ParseResult.skipped`
   标记。
7. **`buildType` 不一定是 `'debug' | 'release'`**：可能出现 `'releaseSigned'`
   等自定义值。key 选择策略：**两个都试**，不按 buildType 选。
8. **timestamp 排序**：跨会话合并理论上无需（初版只单会话内排序）；时区混用
   风险忽略。

## 引用文件

- `tools/logan/LoganLocalDecoder.java` — 字节级帧解析与解密逻辑的 source of truth
- `tools/logan/decode-logan.sh` — debug / release key 选择策略 + end-to-end 行为基准
- `logging/src/main/java/com/hank/musicfree/logging/FeedbackLogExporter.kt` — zip
  结构 + manifest 字段
- `logging/src/main/java/com/hank/musicfree/logging/LogEventFormatter.kt` — 单行
  JSON schema source of truth
- `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt` — 18 个
  category 枚举
- [Logan Logging System Design](./2026-05-05-logging-system-design.md) — 上游
  日志系统总体设计
