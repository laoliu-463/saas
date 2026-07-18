# ADR-014 Harness 跨平台核心目录与渐进迁移

## 状态

- 已批准，待实施。
- 批准日期：2026-07-18。
- 关联设计：`docs/方案/PLAN-006-Harness跨平台验证核心重构设计.md`。

## 背景

当前 Harness 的构建、容器重启、健康检查、报告、内容维护和 Git 收尾集中在 PowerShell 脚本中。开发机使用 Windows，而 CI 和服务器主要使用 Linux；继续扩展 PowerShell 会放大平台差异，也无法自然承载统一 JSON Schema 和结构化证据。

ADR-013 将 `harness/` 一级目录限制为 9 个治理目录。该结构适合现有规则与脚本，但缺少应用源码、机器契约、测试和稳定状态的明确边界。把 Node 核心继续塞入 `scripts/`、把 Schema 塞入 `rules/` 会制造新的职责混淆。

## 决策

采用 Node.js / TypeScript 作为跨平台 Harness 确定性核心，并使用绞杀式迁移保留 PowerShell 兼容入口。

Node.js 运行时基线固定为 20，与现有前端 Docker 构建镜像一致；Harness 使用独立锁文件，不依赖前端的安装状态。

Harness 一级目录白名单由 9 个扩展为 13 个，新增：

- `src/`：Node / TypeScript 核心源码。
- `contracts/`：JSON Schema 与机器可读策略。
- `state/`：稳定发布、架构或验证基线；按需创建，不放运行时临时数据。
- `tests/`：Harness 自身测试。

既有 `rules/`、`tasks/`、`probes/`、`reports/`、`scripts/`、`manifests/`、`archive/`、`templates/`、`engineering/` 职责不变。

## 迁移边界

- 第一阶段由 Node 接管 `backend`、`frontend`、`full` 的本地验证。
- `agent-do.ps1` 保持参数兼容并委托 Node；`docs`、`apifox` 暂不迁移。
- 直接 Node 验证禁止 Git 提交、推送和远端部署。
- 远端部署、回滚、Codex 审查和 DDD 状态计算不属于第一阶段。
- 原始证据继续进入 `runtime/qa/out/<runId>/`，不进入 `state/`。

## 已评估方案

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| Node 与 PowerShell 双跑 | 便于结果对比 | 重复构建和重启，容易产生两套事实 |
| 绞杀式迁移 | 单一事实来源，可逐步回退 | 兼容期仍需维护 PowerShell 壳 |
| 一次性替换 | 最终结构最直接 | 现有入口和脚本回归风险过高 |

选择绞杀式迁移，因为它能在不一次性改写发布链路的前提下建立跨平台核心，同时避免双跑造成证据冲突。

## 状态与证据决策

单项检查使用 `PASS / FAIL / BLOCKED / WARN / SKIPPED / NOT_COLLECTED`；整次运行使用 `PASS / FAIL / BLOCKED / PARTIAL`。机器标识保持英文，所有人类可读内容使用中文。

证据区分 `COMMIT` 与 `WORKTREE`。脏工作区报告必须记录 HEAD 和补丁指纹，不得声称绑定了尚未存在的最终 Commit。

## 实施要求

实施批次必须同步更新：

- `AGENTS.md` 中的 Harness 白名单。
- `harness/rules/harness-structure-policy.md`。
- `harness/scripts/check-harness-limits.ps1` 及其测试。
- `harness/README.md`、`harness/INDEX.md` 和规则变更日志。

新增目录不得使用空占位文件制造表面完成；只有存在真实职责和测试时才创建。

## 后果

- Windows 与 Linux 可以共享同一验证核心。
- PowerShell 从核心编排逐步收缩为兼容入口和尚未迁移的适配器。
- Harness 增加一个独立 Node 依赖边界和锁文件，需要单独维护依赖更新。
- ADR-013 的预算、报告生命周期和基线感知原则继续有效，仅一级目录白名单被本 ADR 扩展。

## 回滚

第一阶段回滚恢复 `agent-do.ps1` 原验证调用并移除 Node 命令入口。由于不修改数据库、远端部署或业务规则，不需要数据回滚。
