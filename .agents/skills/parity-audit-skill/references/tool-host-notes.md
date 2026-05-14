# Tool Host Notes

## Claude Code

- 用 Bash 工具执行 `scripts/parity-audit/audit.sh`
- 任务跟踪用 TaskCreate / TaskUpdate
- 长时间步骤（构建、Maestro 跑 flow）建议 `run_in_background=true`

## Codex CLI

- 同样直接 `bash scripts/parity-audit/audit.sh`
- 任务跟踪走 Codex 自带 TODO 机制
- 长时间步骤目前没有原生后台机制，用 `nohup ... &` + `wait`

无论哪个工具，决策全部走 `scripts/parity-audit/*` 的 CLI；LLM 只需读 stdout JSON。
