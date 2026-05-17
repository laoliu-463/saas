# Triage Labels

Matt Pocock 的 triage 相关 skill 会使用五个 canonical triage roles。当前仓库暂未声明不同的标签词汇，因此先采用默认映射。

| Label in mattpocock/skills | Label in our tracker | Meaning |
| --- | --- | --- |
| `needs-triage` | `needs-triage` | Maintainer needs to evaluate this issue |
| `needs-info` | `needs-info` | Waiting on reporter for more information |
| `ready-for-agent` | `ready-for-agent` | Fully specified, ready for an AFK agent |
| `ready-for-human` | `ready-for-human` | Requires human implementation |
| `wontfix` | `wontfix` | Will not be actioned |

## 使用约束

- 如果 GitHub 仓库中尚未创建这些标签，skill 可以按此词汇创建或提示创建，但不要私自发明第六套命名。
- 如果后续仓库已有既定标签体系，只改右侧 `Label in our tracker` 一列，保持左侧 canonical 名称不变。
- 本文件只负责标签映射，不负责项目业务优先级；业务优先级仍由 `AGENTS.md` 和 `docs/*.md` 决定。
