# GC Manifest - HARNESS-DOC-GC-OPTIMIZE-001

> 生成时间：2026-06-10T20:17:00+08:00
> 任务：Harness 文档体系整体瘦身、拆分、过期清理

## Archive

所有历史报告、证据、retro、content-retire 文件（388 个）从 `harness/reports/` 归档到按日期的子目录。

| Source Pattern | Target | Reason |
|---|---|---|
| harness/reports/evidence-20260603-*.md | harness/reports/archive/20260603/ | 历史证据，不是当前工作文档 |
| harness/reports/evidence-20260604-*.md | harness/reports/archive/20260604/ | 历史证据 |
| harness/reports/evidence-20260605-*.md | harness/reports/archive/20260605/ | 历史证据 |
| harness/reports/evidence-20260606-*.md | harness/reports/archive/20260606/ | 历史证据 |
| harness/reports/evidence-20260607-*.md | harness/reports/archive/20260607/ | 历史证据 |
| harness/reports/evidence-20260608-*.md | harness/reports/archive/20260608/ | 历史证据 |
| harness/reports/evidence-20260609-*.md | harness/reports/archive/20260609/ | 历史证据 |
| harness/reports/evidence-20260610-*.md | harness/reports/archive/20260610/ | 历史证据 |
| harness/reports/retro-*.md | harness/reports/archive/YYYYMMDD/ | 历史复盘 |
| harness/reports/content-retire-*.md | harness/reports/archive/YYYYMMDD/ | 历史退役记录 |
| harness/reports/ddd-*.md | harness/reports/archive/YYYYMMDD/ | DDD 审计报告 |
| harness/reports/order-*.md | harness/reports/archive/YYYYMMDD/ | 订单域报告 |
| harness/reports/dashboard-*.md | harness/reports/archive/YYYYMMDD/ | 看板报告 |
| harness/reports/user-domain-*.md | harness/reports/archive/YYYYMMDD/ | 用户域报告 |
| harness/reports/p-fix-*.md, p0-*.md | harness/reports/archive/YYYYMMDD/ | P0/修复报告 |
| harness/reports/git-*.md | harness/reports/archive/YYYYMMDD/ | Git 批次报告 |
| harness/reports/settlement-*.md | harness/reports/archive/YYYYMMDD/ | 结算报告 |
| harness/reports/func-*.md | harness/reports/archive/YYYYMMDD/ | 功能报告 |
| harness/reports/SECURITY-INCIDENT-*.md | harness/reports/archive/20260607/ | 安全事件报告 |
| harness/instructions/multi-agent-ddd-prompts.md (1471行) | harness/reports/archive/20260610/multi-agent-ddd-prompts-full.md | 已拆分到 prompts/agents/ |

## Split

| Source | Targets | Reason |
|---|---|---|
| COMPLETION_GATES.md (399行) | COMPLETION_GATES.md + completion-gates-detail.md + completion-gates-git.md | 超过 200 行限制 |
| skills/git-change-control.md (405行) | git-change-control.md + .intake.md + .commit.md + .exit.md | 超过 200 行限制 |

## Trim

| Source | Before | After | Reason |
|---|---|---|---|
| SESSION_EXIT_GATE.md | 277行 | ~100行 | 精简冗余说明 |
| skills/git-batch-submit.md | 303行 | ~90行 | 去除重复内容 |
| instructions/multi-agent-ddd-prompts.md | 1471行 | ~50行 | 替换为索引文件 |

## New

| Path | Reason |
|---|---|
| harness/INDEX.md | 总索引，替代分散查找 |
| harness/core/04-doc-style-guide.md | 文档规范 |
| harness/completion-gates-detail.md | Gate 详细定义（从 COMPLETION_GATES.md 拆出） |
| harness/completion-gates-git.md | Git 子门禁（从 COMPLETION_GATES.md 拆出） |
| harness/skills/git-change-control.intake.md | Git Intake Gate（从 git-change-control.md 拆出） |
| harness/skills/git-change-control.commit.md | Commit/Push/Deploy Gate（拆出） |
| harness/skills/git-change-control.exit.md | Exit Gate/Unknown/Rollback（拆出） |
| harness/scripts/check-doc-lines.ps1 | 自动化行数检查脚本 |

## Delete

无直接删除。所有内容均通过归档保留。

## Keep

| Path | Reason |
|---|---|
| harness/commands/*.ps1 | 执行脚本，不动 |
| harness/state/*.md | 活跃状态文件 |
| harness/environment/*.md | 环境文档 |
| harness/evals/*.md | 评估文档 |
| harness/runbooks/*.md | 运行手册 |
| harness/feedback/*.md | 反馈模板 |
| harness/plans/*.md | 计划文档 |
| harness/prompts/agents/*.md | Agent 提示词 |
| harness/doc/**/*.md | 知识库文档 |
| harness/tasks/*.md | 任务文件 |
| harness/agent-locks/*.md | Agent 锁文件 |
