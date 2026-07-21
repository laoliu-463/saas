# Issue Tracker: GitHub

> **状态**：本目录是 Matt Pocock engineering skills 的配置入口。旧版位于 `docs/agents/issue-tracker.md`，已**合并重构到本目录**（DDD-MIGRATION-100 Phase 0）。

## GitHub Issues 作为请求面

本仓库的 issue tracker 默认是 GitHub Issues，对应 remote 为 `https://github.com/laoliu-463/saas.git`。涉及 issue、PRD、triage、implement 的 skill 默认使用 `gh` CLI 在当前仓库上下文中执行。

`gitee`（`https://gitee.com/cao-jianing463/saas.git`）是只读镜像，**不作为 triage 源**（不接受外部 PR/Issue）。

## Harness 索引与 GitHub Issue 同步

为了兼顾 harness 本地化与 GitHub 协作，本仓库采用 **混合模式**：

- **权威源**：GitHub Issues（创建、追踪、讨论）
- **harness 索引**：`docs/harness-maintenance/engineering/issues-index.md` 维护 issue 列表镜像（每周自动更新或手动 sync）
- **本地草稿**：复杂 PRD（如本仓库的 `DDD-MIGRATION-100`）先写到 `docs/决策/PRD-*.md`，再发布到 GitHub

## gh CLI 命令约定

| 操作 | 命令 |
|---|---|
| 创建 issue | `gh issue create --title "..." --body-file <file> --label "ready-for-agent"` |
| 查看 issue | `gh issue view <number> --comments` |
| 列出 issue | `gh issue list --state open` |
| 评论 issue | `gh issue comment <number> --body "..."` |
| 增删标签 | `gh issue edit <number> --add-label "..."` / `--remove-label "..."` |
| 关闭 issue | `gh issue close <number> --comment "..."` |

## 约束

- 在当前仓库内运行 `gh` 命令，默认让 CLI 从 git remote 推断仓库，不手写别的 repo。
- 若 skill 产出的是计划、PRD 或拆分结果，**默认发布为 GitHub issue**；除非用户明确要求只落本地文档。
- 若仓库后续改用 Jira、Linear 或本地 markdown issue，**更新本文件 + docs/harness-maintenance/engineering/issues-index.md**，而不是让 skill 自行猜测。

## PR 策略

**外部 PR 不作为 triage 源**。本仓库不接受外部 PR（gitee 是只读镜像），所有需求/缺陷通过 Issue 流程。Collaborators 的 in-flight PR 不在此规则范围。

## 相关文件

- `docs/harness-maintenance/engineering/triage-labels.md` —— 5 个 canonical 标签映射
- `docs/harness-maintenance/engineering/context.md` —— 上下文文档消费规则
- `docs/harness-maintenance/engineering/issues-index.md` —— GitHub Issues 的本地镜像（待建立）
- `docs/决策/ADR-*.md` —— 架构决策
- `CONTEXT.md`（根）—— 项目领域词汇

## 变更历史

- **v1.0**（初始）：位于 `docs/agents/issue-tracker.md`，GitHub Issues 默认
- **v2.0**（2026-06-19）：迁移到 `docs/harness-maintenance/engineering/issue-tracker.md`，改为 GitHub + harness 索引混合模式