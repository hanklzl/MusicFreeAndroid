# Memory Promotion

读取 `~/.claude/projects/-Users-zili-code-android-MusicFreeAndroid/memory/MEMORY.md`：

- **项目级 rule 候选**：与代码 / 架构 / 测试约束直接相关，应跨工具生效（例：DB schema during dev、UI 设计原则）。
- **个人会话偏好**：交互习惯、回答语气、输出长度等，仅本机 Claude Code 使用，留原位。

判别启发式：

- 出现 entity / migration / build / test / API / domain 名称 → 大概率项目级。
- 出现 "user prefers" / "我喜欢" / 回答风格 → 大概率个人级。

提议形式（写入 REPORT 的 memory promotion 小节）：

- `<条目标题>` → 提议 promote 到 `docs/dev-harness/<area>/rules.md` 的 `<section>` 段，并在 user memory 中删除原条目。

不允许：本 skill 直接修改 `~/.claude/projects/.../MEMORY.md` 或 repo 文件；用户确认后再 apply。
