# 合并重构 manifest（2026-06-19）

## 移动/删除的文件

| 源（已删除） | 目标（已新建） | 行数 |
| --- | --- | --- |
| `docs/agents/issue-tracker.md` (v1.0, 26 行) | `harness/engineering/issue-tracker.md` (v2.0, 34 行) | +8 |
| `docs/agents/triage-labels.md` (v1.0, 17 行) | `harness/engineering/triage-labels.md` (v2.0, 51 行) | +34 |
| `docs/agents/domain.md` (v1.0, 65 行) | `harness/engineering/context.md` (v2.0, 82 行) | +17 |
| （不存在） | `harness/engineering/issues-index.md` (v2.0, 96 行) | +96（新） |
| （不存在） | `harness/engineering/README.md` (v2.0, 63 行) | +63（新） |

## 修改文件

| 文件 | 变更 |
| --- | --- |
| `AGENTS.md` | `## 11. Agent skills` section 已在 GIT-BATCH-1 更新，指向 `harness/engineering/` |
| `harness/INDEX.md` | 增加 `engineering/` 引用 section |
| `harness/rules/harness-structure-policy.md` | 白名单增加 `engineering/` |
| `harness/scripts/check-harness-limits.ps1` | 允许 `harness/engineering/` 作为一级目录 |

## 新建文件

| 文件 | 内容 |
| --- | --- |
| `harness/engineering/*.md` | 5 个核心 engineering skill 配置 |

## GitHub Issue

- #3 PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100）—— ready-for-agent

## Triage 标签（GitHub 已创建）

- needs-triage (#D93F0B)
- needs-info (#FBCA04)
- ready-for-agent (#0E8A16)
- ready-for-human (#BFD4F2)
- wontfix (#FFFFFF，原已存在)

## 清理步骤

1. 删除 `docs/agents/issue-tracker.md`
2. 删除 `docs/agents/triage-labels.md`
3. 删除 `docs/agents/domain.md`
4. 删除 `docs/agents/` 空目录（如有）
5. 提交 manifest（harness/manifests/2026-06-19-engineering-merge.md）

## 暂不纳入本 manifest 的文件

- `harness/engineering/PHASE-1-DDD-USER-DATASCOPE.md` 是历史阶段手册，内容需要随 DDD 现状单独校正后再提交。
- `docs/决策/PRD-DDD-MIGRATION-100.md` 等 DDD 决策文档属于后续 DDD docs-state 批次。
