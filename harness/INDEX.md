# Harness Index

> 本文件是 Harness 体系的总索引。所有当前可执行文档均可从此处查找。

## Core - 核心规则

| 文件 | 说明 |
|---|---|
| [AGENT_CONTRACT.md](AGENT_CONTRACT.md) | Agent 执行总合同 |
| [COMPLETION_GATES.md](COMPLETION_GATES.md) | 完成门禁（Gate 0-4） |
| [SESSION_EXIT_GATE.md](SESSION_EXIT_GATE.md) | 会话退出门禁 |
| [TASK_ROUTING.md](TASK_ROUTING.md) | 任务路由与必读文档 |
| [FORBIDDEN_SCOPE.md](FORBIDDEN_SCOPE.md) | 禁止范围 |
| [CURRENT_STATE.md](CURRENT_STATE.md) | 项目当前状态 |
| [DOMAIN_MAP.md](DOMAIN_MAP.md) | 领域地图 |
| [QUALITY_LEDGER.md](QUALITY_LEDGER.md) | 质量台账 |
| [HARNESS_CHANGELOG.md](HARNESS_CHANGELOG.md) | 变更日志 |
| [core/04-doc-style-guide.md](core/04-doc-style-guide.md) | 文档风格指南 |

## Gate 子文件

| 文件 | 说明 |
|---|---|
| [completion-gates-detail.md](completion-gates-detail.md) | Gate 0-4 详细定义 |
| [completion-gates-git.md](completion-gates-git.md) | Git 子门禁 (G0-G4) |

## Skills - 可复用技能

| 文件 | 说明 |
|---|---|
| [skills/git-change-control.md](skills/git-change-control.md) | Git 变更控制（含子文件） |
| [skills/git-batch-submit.md](skills/git-batch-submit.md) | 批次提交流程 |
| [skills/post-task-gc.md](skills/post-task-gc.md) | 任务后清理 |
| [skills/code-review.skill.md](skills/code-review.skill.md) | 代码审查 |
| [skills/ddd-domain-optimization.skill.md](skills/ddd-domain-optimization.skill.md) | DDD 领域优化 |
| [skills/evidence-report.skill.md](skills/evidence-report.skill.md) | 证据报告 |
| [skills/real-pre-debug.skill.md](skills/real-pre-debug.skill.md) | Real-pre 调试 |
| [skills/order-attribution.skill.md](skills/order-attribution.skill.md) | 订单归因 |
| [skills/performance-dashboard.skill.md](skills/performance-dashboard.skill.md) | 业绩看板 |
| [skills/product-library.skill.md](skills/product-library.skill.md) | 商品库 |
| [skills/sample-lifecycle.skill.md](skills/sample-lifecycle.skill.md) | 寄样生命周期 |
| [skills/frontend-ux.skill.md](skills/frontend-ux.skill.md) | 前端 UX |
| [skills/domain-alignment.skill.md](skills/domain-alignment.skill.md) | 领域对齐 |

## Instructions - 领域指令

| 文件 | 说明 |
|---|---|
| [instructions/multi-agent-ddd-prompts.md](instructions/multi-agent-ddd-prompts.md) | 多 Agent DDD 索引 |
| [instructions/order-domain.md](instructions/order-domain.md) | 订单域 |
| [instructions/user-domain.md](instructions/user-domain.md) | 用户域 |
| [instructions/product-domain.md](instructions/product-domain.md) | 商品域 |
| [instructions/sample-domain.md](instructions/sample-domain.md) | 寄样域 |
| [instructions/talent-domain.md](instructions/talent-domain.md) | 达人域 |
| [instructions/config-domain.md](instructions/config-domain.md) | 配置域 |
| [instructions/performance-domain.md](instructions/performance-domain.md) | 业绩域 |
| [instructions/analytics-module.md](instructions/analytics-module.md) | 分析模块 |

## State - 状态文件

| 文件 | 说明 |
|---|---|
| [state/DOMAIN_STATUS.md](state/DOMAIN_STATUS.md) | 领域优化状态 |
| [state/KNOWN_ISSUES.md](state/KNOWN_ISSUES.md) | 已知问题 |
| [state/DECISIONS.md](state/DECISIONS.md) | 架构决策 |
| [state/HARNESS_DEBT.md](state/HARNESS_DEBT.md) | Harness 债务 |
| [state/DEPLOYMENT_STATE.md](state/DEPLOYMENT_STATE.md) | 部署状态 |

## Plans

| 文件 | 说明 |
|---|---|
| [plans/DDD_OPTIMIZATION_ROADMAP.md](plans/DDD_OPTIMIZATION_ROADMAP.md) | DDD 优化路线图 |
| [plans/DDD_DOMAIN_TASK_MATRIX.md](plans/DDD_DOMAIN_TASK_MATRIX.md) | DDD 领域任务矩阵 |

## Reports

- 当前报告：[reports/current/](reports/current/)
- 历史归档：[reports/archive/](reports/archive/)
- GC Manifest：[manifests/gc/](manifests/gc/)

## Archive Policy

- 当前可执行文档 <= 200 行，超出需精简或拆分
- 历史报告和证据移到 `reports/archive/YYYYMMDD/`
- 删除前必须生成 Manifest
- 详见 [core/04-doc-style-guide.md](core/04-doc-style-guide.md)
