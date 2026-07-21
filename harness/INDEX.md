# Harness Index

## 一级目录白名单（13 个）
- [rules/](./rules/)
- [tasks/](./tasks/)
- [probes/](./probes/)
- [reports/](./reports/)
- [scripts/](./scripts/)
- [manifests/](./manifests/)
- [archive/](./archive/)
- [templates/](./templates/)
- [engineering/](./engineering/)   ← **Matt Pocock engineering skill 配置**（v2.0, 2026-06-19）
- `src/`：Node / TypeScript Harness 核心源码，有真实实现时创建。
- `contracts/`：JSON Schema 与机器可读策略，有真实契约时创建。
- `state/`：稳定发布、架构或验证基线，按需创建；禁止放入运行时产物。
- `tests/`：Harness 自身测试，有真实测试时创建。

新增目录不得用空目录或占位 README 预建；原始运行产物继续进入 `runtime/qa/out/<run-id>/`。

## 最新报告
- [reports/current/](./reports/current/)
- [reports/current/latest-harness-limits-check.md](./reports/current/latest-harness-limits-check.md)
- [reports/current/latest-content-retire.md](./reports/current/latest-content-retire.md)

## 清理计划
- [manifests/harness-gc-plan-20260612.md](./manifests/harness-gc-plan-20260612.md)

## 规则
- [rules/harness-structure-policy.md](./rules/harness-structure-policy.md)
- [rules/file-retention-policy.md](./rules/file-retention-policy.md)
- [rules/report-style-policy.md](./rules/report-style-policy.md)
- [rules/cicd-real-pre-policy.md](./rules/cicd-real-pre-policy.md)

## 工程 Skill 配置（engineering/）

按 Matt Pocock skill 约定收敛于本目录。`AGENTS.md` 的 `## 11. Agent skills` section 指向此处。

- [engineering/issue-tracker.md](./engineering/issue-tracker.md) —— Issue tracker 配置（GitHub）
- [engineering/triage-labels.md](./engineering/triage-labels.md) —— Triage 标签映射
- [engineering/context.md](./engineering/context.md) —— Domain doc 消费规则
- [engineering/issues-index.md](./engineering/issues-index.md) —— GitHub Issues 本地镜像

**历史位置**：`docs/agents/` 已合并重构到 `engineering/`。不再单独维护 `docs/agents/`。
