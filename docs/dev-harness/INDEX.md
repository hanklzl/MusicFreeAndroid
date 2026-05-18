# Dev Harness — INDEX

> 文档状态：当前规范（Dev Harness 总入口）
> 适用范围：UI / 插件 / 播放器 / 测试 / Runtime State 五域的开发守门、错误库与 AI skills 关联
> 直接执行：是
> 当前入口：[AGENTS](../../AGENTS.md) ｜ [DOCS_STATUS](../DOCS_STATUS.md)
> 设计来源：[Dev Harness 基础设施设计](../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)
> 最后校验：2026-05-09

## 域规则（rules）

| 域 | rules.md | incidents.md |
|---|---|---|
| UI / Compose Screen | [ui/rules.md](./ui/rules.md) | [ui/incidents.md](./ui/incidents.md) |
| 插件系统 | [plugin/rules.md](./plugin/rules.md) | [plugin/incidents.md](./plugin/incidents.md) |
| 播放器 / Media3 | [player/rules.md](./player/rules.md) | [player/incidents.md](./player/incidents.md) |
| 测试 / 测试基建 | [test/rules.md](./test/rules.md) | [test/incidents.md](./test/incidents.md) |
| Runtime State / 持久化恢复 | [runtime/rules.md](./runtime/rules.md) | [runtime/incidents.md](./runtime/incidents.md) |

## 错误库

- 全仓索引：[incidents/index.md](./incidents/index.md)
- 新增条目模板：[incidents/template.md](./incidents/template.md)
- ID 规则：`INC-YYYY-NNNN`，年 + 4 位序号；递增不回收，跨域全局唯一。

## AI skills

- 单一来源：`.agents/skills/<area>-skill/`，软链至 `.claude/skills/`、`.codex/skills/`。
- 5 个 skill：`ui-harness-skill`、`plugin-system-skill`、`media-player-skill`、`test-stability-skill`、`harness-curator-skill`。

## 本地守门

- 本地一键：`bash scripts/dev-harness/check.sh`

## 发布流程

- 发布流程详见根目录 `RELEASE.md` 与 `docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md`。
- 分 ABI 发布与更新链路（双 APK + mapping 归档 + 侧栏检查更新）：详见 [docs/superpowers/specs/2026-05-16-per-abi-release-and-update-design.md](../superpowers/specs/2026-05-16-per-abi-release-and-update-design.md)。

## 项目记忆边界

- 错误库 / 强约束 / AI skills 都进 git，跨 AI 工具与跨开发者生效。
- Claude Code 个人 auto-memory（`~/.claude/projects/.../memory/MEMORY.md`）仅承载个人会话偏好，不放项目级 rule。
- 历史决策快照在 `docs/superpowers/specs/` 与 `plans/`，仅参考。

## Adjacent: Parity Audit Skill

并非 dev-harness 域，但产出物（Issue 草稿、scenario catalog、运行产物）与 dev-harness incidents 互补。详见 `.agents/skills/parity-audit-skill/SKILL.md` 与 `docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。
