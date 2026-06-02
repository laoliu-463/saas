# Harness Engineering

本目录是抖音团长 SaaS 的 AI Agent 工程执行系统。它不替代 `docs/` 的业务事实，而是把后续 Agent 修改代码后的固定流程沉淀为脚本、清单、模板和证据入口。

## 目录

| 路径 | 作用 |
| --- | --- |
| `AGENT_CONTRACT.md` | Agent 执行合同和 DoD |
| `CURRENT_STATE.md` | 当前状态、V1 闭环、旧文档冲突 |
| `TASK_ROUTING.md` | 任务类型到领域、skill、验证的分流 |
| `FORBIDDEN_SCOPE.md` | V1、real-pre、Git、密钥和模块边界禁止项 |
| `DOMAIN_MAP.md` | 七域 + 分析模块职责地图 |
| `commands/` | PowerShell 固定执行入口 |
| `skills/` | AI Agent 可执行技能规范 |
| `evals/` | 验收目标、步骤、通过标准和证据要求 |
| `runbooks/` | real-pre、远端部署、回滚、归因等执行手册 |
| `prompts/` | 可复用提示词 |
| `reports/` | evidence report 输出目录 |
| `doc/` | 历史 Harness 聚合文档；保留为兼容参考，不作为新的主事实源 |

## 五个子系统

| 子系统 | 路径 | 作用 |
| --- | --- | --- |
| Instructions | `instructions/`、`AGENT_CONTRACT.md`、`FORBIDDEN_SCOPE.md` | 项目规则、V1 边界、禁止事项、完成标准 |
| Tools | `commands/`、`tools/README.md` | 构建、重启、验证、部署、报告、复盘 |
| Environment | `environment/` | test、real-pre、remote real-pre 和 compose 边界 |
| State | `CURRENT_STATE.md`、`state/`、`HARNESS_CHANGELOG.md` | 当前状态、风险、证据、版本 |
| Feedback | `feedback/`、`evals/`、`reports/` | 验收、证据报告、复盘和 Harness 升级闭环 |

## Harness 与 DDD 的分工

- `docs/03-领域架构总览.md` 和 `docs/领域/*.md` 负责定义 DDD 边界、领域职责和禁止越界项。
- `harness/TASK_ROUTING.md` 负责把任务路由到对应领域、Scope、runbook 和验证入口。
- `harness/skills/domain-alignment.skill.md` 负责在修改前确认主责领域、关联领域、V1 边界和证据要求。
- `harness/feedback/*.md` 负责把失败经验、重复问题和旧内容债务回收到可执行规则。
- Harness 不新增业务规则；发现业务冲突时，必须回到 `docs/决策/ADR-002-V1范围优先级.md` 或对应领域主源处理。

## 任务入口索引

| 任务 | 入口 |
| --- | --- |
| 后端变更 | `TASK_ROUTING.md`、`runbooks/backend-change.md` |
| 前端变更 | `TASK_ROUTING.md`、`runbooks/frontend-change.md` |
| 数据库变更 | `TASK_ROUTING.md`、`runbooks/database-change.md` |
| Docker 操作 | `environment/docker-compose-map.md`、`runbooks/docker-compose-operations.md` |
| 测试验收 | `runbooks/test-validation.md`、`evals/` |
| 第三方联调 | `runbooks/third-party-integration.md`、`skills/real-pre-debug.skill.md` |
| 任务收尾 / GC | `runbooks/closeout-and-gc.md`、`feedback/garbage-collection-policy.md` |

## 总入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "说明本次修改"
```

`agent-do.ps1` 和主要子命令默认 `Env=real-pre`。`test` 只作为显式指定的专项验证环境；远端部署仍必须显式传 `-DeployRemote true`。

## 最小验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope docs -DeployRemote false -Message "docs: initialize harness engineering system" -DryRun
```

## 旧内容维护

每次任务后默认生成旧内容维护计划：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan
```

归档或删除必须提供 manifest；默认归档目录为 `harness/archive/retired-content/`。

## 远端部署

远端部署只有用户明确要求时执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app
```
