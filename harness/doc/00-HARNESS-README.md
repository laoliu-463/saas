# Harness Engineering 文档入口

本目录按 Harness Engineering 五个子模型重构，用于让后续 AI Agent 快速确认项目规则、执行入口、环境边界、当前状态和验证方式。

> **主源说明**（2026-06-03 HARNESS-DEBT-GOVERNANCE-ITERATION）：
> 本目录（`harness/doc/`）是**聚合阅读入口**，仅做"快速浏览"。
> 所有事实 / 命令 / 状态 / 报告 / runbook 的**权威主源**在 `harness/` 根目录的平铺文件（`AGENT_CONTRACT.md` / `CURRENT_STATE.md` / `TASK_ROUTING.md` / `FORBIDDEN_SCOPE.md` / `COMPLETION_GATES.md` / `SESSION_EXIT_GATE.md` / `state/*.md` / `runbooks/*.md` / `feedback/*.md` / `environment/*.md` / `commands/*.ps1`）。
> 本目录与主源冲突时，**以 `harness/` 根目录为准**。本目录内容必须由主源同步，不在本目录独立裁决事实。

本目录不替代 `docs/` 的业务事实主源，也不替代 `harness/commands/` 的脚本入口。涉及业务规则、接口、数据模型、验收和部署事实时，仍以仓库当前源码、运行配置、`docs/` 与既有 `harness/` 为准。

## 阅读顺序

1. `01-instructions/01-项目执行协议.md`
2. `01-instructions/02-V1交付合同.md`
3. `04-state/01-当前项目状态.md`
4. `04-state/02-业务闭环状态.md`
5. `03-environment/02-real-pre环境说明.md`
6. `02-tools/02-后续Agent默认执行流程.md`
7. `05-feedback/02-业务闭环验证清单.md`

## 五个子模型

| 子模型 | 路径 | 作用 |
| --- | --- | --- |
| Instructions | `01-instructions/` | 告诉 AI 项目规则、V1 边界、禁止范围和文档优先级。 |
| Tools | `02-tools/` | 告诉 AI 已有哪些命令、脚本和默认执行流程。 |
| Environment | `03-environment/` | 告诉 AI test、real-pre、remote real-pre 的边界和安全规则。 |
| State | `04-state/` | 告诉 AI 当前真实状态、阻塞点、证据索引和文档债务。 |
| Feedback | `05-feedback/` | 告诉 AI 如何验收、取证、复盘并迭代 Harness。 |

## 事实来源

- 仓库地图：`CLAUDE.md`
- 文档地图：`docs/README.md`
- 执行合同：`harness/AGENT_CONTRACT.md`
- 当前状态：`harness/CURRENT_STATE.md`
- 任务路由：`harness/TASK_ROUTING.md`
- 禁止范围：`harness/FORBIDDEN_SCOPE.md`
- 领域主源：`docs/领域/*.md`
- 流程主源：`docs/流程/*.md`
- real-pre 手册：`docs/验收/real-pre联调手册.md`
- 部署主源：`docs/10-部署运行总览.md`

## 本次重构口径

- 只新增 `harness/doc/` 下的聚合文档。
- 不修改业务源码。
- 不修改数据库 migration。
- 不修改 Docker Compose。
- 不执行构建、重启或远端部署。
- 旧文档不直接删除；冲突内容登记到 `04-state/05-文档债务与冲突台账.md`。
- 后续任务结束时通过 `harness/commands/retire-content.ps1` 生成旧内容维护计划；归档或删除必须有 manifest 和 evidence。

## 最小验证

完成文档变更后至少检查：

```powershell
Test-Path .\harness\doc\00-HARNESS-README.md
Get-ChildItem .\harness\doc -Directory
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan -DryRun
```
