# Triage Labels

> **状态**：本目录是 Matt Pocock engineering skills 的配置入口。旧版位于 `docs/agents/triage-labels.md`，已**合并重构到本目录**（DDD-MIGRATION-100 Phase 0）。

Matt Pocock 的 triage 相关 skill 会使用五个 canonical triage roles。当前仓库采用**默认同名映射**（左侧 canonical → 右侧实际标签完全一致）。

## 标签映射表

| Label in Matt Pocock skills | Label in our tracker | Meaning |
| --- | --- | --- |
| `needs-triage` | `needs-triage` | Maintainer needs to evaluate this issue |
| `needs-info` | `needs-info` | Waiting on reporter for more information |
| `ready-for-agent` | `ready-for-agent` | Fully specified, ready for an AFK agent |
| `ready-for-human` | `ready-for-human` | Requires human implementation |
| `wontfix` | `wontfix` | Will not be actioned |

## 标签创建状态

5 个 canonical 标签已通过 `gh label create` 在 GitHub 上创建：

```
needs-triage       #D93F0B  (橘色)
needs-info         #FBCA04  (黄色)
ready-for-agent    #0E8A16  (绿色)
ready-for-human    #BFD4F2  (浅蓝)
wontfix            #FFFFFF  (白色)
```

## 使用约束

- 如果 GitHub 仓库中尚未创建这些标签（首次 setup），skill 可按此词汇创建或提示创建。
- 如果后续仓库已有既定标签体系，**只改右侧 Label 列**，保持左侧 canonical 名称不变。
- 本文件只负责标签映射，不负责项目业务优先级；业务优先级仍由 `AGENTS.md` 和 `docs/决策/ADR-*.md` 决定。

## 标签流转规则

`/triage` skill 处理的标签流转：

```
incoming issue
   ↓
needs-triage          ← 待 maintainer 评估
   ↓
[分流]
   ↓                          ↓                          ↓
needs-info             ready-for-agent          ready-for-human
(等待 reporter 信息)    (AFK agent 可执行)       (需 human 实现)
   ↓                          ↓                          ↓
ready-for-agent        /implement                  手动分配
(信息齐全后)                                       (human)
```

`wontfix` 是终态，任意阶段都可以打。

## 与 `/implement` 的衔接

打 `ready-for-agent` 标签的 issue 会被 `/implement` skill 抓取并在新 session 中实现：
- issue 必须包含完整需求 + 验收标准 + 风险评估
- 实现完成后 issue 关闭 + 引用 PR/commit

## 相关文件

- `harness/engineering/issue-tracker.md` —— issue tracker 配置
- `harness/engineering/context.md` —— 上下文文档
- `harness/engineering/issues-index.md` —— GitHub Issues 镜像（待建立）

## 变更历史

- **v1.0**（初始）：位于 `docs/agents/triage-labels.md`，5 个 canonical 标签
- **v2.0**（2026-06-19）：迁移到 `harness/engineering/triage-labels.md`，新增流转规则文档