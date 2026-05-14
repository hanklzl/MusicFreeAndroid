# docs/parity-audit/

由 `parity-audit-skill` 管理的状态、scenario catalog 与运行产物。

- `state.json` — 机读状态（唯一可信源），由 `scripts/parity-audit/state.py` 读写
- `queue.md` — 人读队列，每轮重写
- `parity-plugins.json` — 双端共享固定插件集
- `scenarios/` — scenario YAML，v0 仅 `home_entry.yaml`
- `runs/<YYYY-MM-DD-HHMM>/` — 每轮运行产物，含截图、events.jsonl、diff.json、issue.md 草稿、REPORT.md

详见 spec：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。
