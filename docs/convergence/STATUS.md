# MusicFree 收敛状态

## 功能覆盖率
- 页面: 16/19
- PluginApi: 14/14
- 面板/弹窗: 5/23（原版 panel 18 + 业务 dialog 5）

## 迭代历史
| 轮次 | 日期 | 完成项 | 页面覆盖率变化 | 备注 |
|------|------|--------|---------------|------|
| 1 | 2026-03-21 | 完成分析、实现与验证（PluginApi 扩展 + 首页结构收敛 + TopList 链路） | 5/19 → 7/19 | TopList 真实数据链路待“可用插件”环境复验 |
| 2 | 2026-03-21 | 完成分析、实现与验证（`getMusicSheetInfo` + `recommendSheets` + `pluginSheetDetail`） | 7/19 → 9/19 | 详情页真实插件数据态受插件环境/模拟器稳定性影响，待补验收 |
| 3 | 2026-03-21 | 完成分析、实现与验证（PluginApi 补齐 4 方法 + History 页面） | 9/19 → 10/19 | History 空态链路与截图完成；模拟器存在间歇性 `System UI` ANR |
| 4 | 2026-03-21 | 完成分析、实现与验证（PluginApi 剩余 3 方法 + 模型/解析补齐） | 10/19 → 10/19 | PluginApi 覆盖率提升至 14/14；待后续页面消费与端到端验收 |
| 5 | 2026-03-21 | 完成分析、实现与验证（新增 MusicDetail 页面 + 详情入口接线 + API 消费） | 10/19 → 11/19 | MusicDetail 端上截图与真实插件数据态待补验收 |
| 6 | 2026-03-21 | 完成分析、实现与验证（新增 AlbumDetail/ArtistDetail 页面 + MusicDetail 导航入口） | 11/19 → 13/19 | 新增页面链路已接线；端上真实数据态截图待补 |
| 7 | 2026-03-21 | 完成分析、实现与验证（新增 Permissions 页面 + 首页 Drawer/UI 收敛 + 默认订阅导入与搜索默认插件） | 13/19 → 14/19 | 真实端上“订阅导入->搜索->播放”截图与稳定复验待补 |
| 8 | 2026-03-21 | 完成分析、实现与验证（插件运行时 `require` 兼容层补齐：`crypto-js/qs/he/dayjs/big-integer` + `axios.default`） | 14/19 → 14/19 | 已覆盖 logcat 根因；待端上复验搜索与播放链路 |
| 9 | 2026-03-21 | 完成分析、实现与验证（允许明文流量 + `AxiosShim` POST/form/gzip 兼容 + 真实插件集成测试） | 14/19 → 14/19 | `KW/WY` 搜索已在 connected instrumentation 通过；`KG` 上游接口可返回 0 条但无运行时异常 |
| 10 | 2026-03-21 | 完成分析、实现与验证（`MusicItem` 扩展字段透传，修复 `getMediaSource` 点击播放失败 + 集成测试覆盖 `search -> getMediaSource`） | 14/19 → 14/19 | 已确认根因为 `songmid` 等字段在桥接链路丢失；WY 真插件与本地 runtime-shim 集成测试通过 |
| 11 | 2026-03-21 | 完成分析、实现与验证（搜索页播放兜底：主插件失败回退 `元力WY` + 匹配算法与单测） | 14/19 → 14/19 | “搜索可用但点击失败”场景可自动回退；仍需端上真实点击链路截图验收 |
| 12 | 2026-03-21 | 完成分析、实现与验证（默认订阅真实集成测试：`installFromSubscriptionUrl -> WY search -> getMediaSource`） | 14/19 → 14/19 | 默认订阅导入与 WY 搜索/解析链路已在 connected instrumentation 通过 |
| 13 | 2026-03-21 | 完成分析、实现与验证（`searchMusicList` 页面落地 + 启动导航修复 + `musicListEditor-lite` 落地 + iteration-13 文档/截图基线刷新） | 14/19 → 16/19 | `searchMusicList` 与 `musicListEditor-lite` 已不再属于缺页；但 UI fidelity、能力密度与 plugin-backed 详情流仍未收敛 |

## 当前 Backlog（按综合分降序）
| # | 差异项 | 粒度 | 综合分 | 来源 |
|---|--------|------|--------|------|
| 1 | 剩余页面仍缺失（`fileSelector/downloading/setCustomTheme`）；其中 `fileSelector` 是当前最高优先级缺页和子系统 unlock | 粗 | 5.8 | 迭代13分析 |
| 2 | Plugin-backed 详情流已从“不可触达”推进到“可触达”，但仍缺完整端上强验证（`pluginSheetDetail`、`topListDetail`、`musicDetail -> albumDetail/artistDetail`） | 中 | 5.5 | 迭代13分析/验证 |
| 3 | 首页 / Drawer / History 已有 iteration-13 当前截图，但与用户提供原版相比 UI fidelity 与能力密度差距仍大 | 细 | 5.1 | 迭代13截图对比 |
| 4 | `searchMusicList` 已实现，但仍只覆盖 `playlist/history`，尚未扩展到 local music / generic sheet source | 中 | 4.7 | 迭代13验证 |
| 5 | 真实订阅导入 -> 搜索 -> 播放端上全链路已推进到带元数据的播放器页，但仍缺更完整的播放控制/截图自动化收口 | 中 | 4.5 | 迭代13验证 |
| 6 | `musicListEditor-lite` 已实现为 playlist-first MVP，但尚未扩展到 local music / history / shared collection source | 中 | 4.0 | 迭代13实现/复核 |
| 7 | 数据模型不完整（IMusicItem 字段与媒体实体缺口） | 中 | 3.5 | 迭代1分析 |
