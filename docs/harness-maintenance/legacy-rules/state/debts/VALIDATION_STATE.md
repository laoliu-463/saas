# Validation State

> **主源说明**：本文件的"验证入口表"与 `docs/harness-maintenance/legacy-rules/governance/task-routing.md` 的"任务类型分流表"互为对偶：
> - 本文件：列**命令**与适用环境。
> - `TASK_ROUTING.md`：列**任务类型**与必读文件。
> 详细 scope → command 决策以 `docs/harness-maintenance/legacy-rules/runbooks/governance/scope-command-matrix.md` 为权威表。

## 当前验证入口

| 类型 | 命令 | 适用环境 | 说明 |
| --- | --- | --- | --- |
| docs/Harness 安全检查 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs` | real-pre | 不构建、不重启 |
| 后端测试 | `mvn -f backend/pom.xml test` | 本机 / test | 代码修改后按风险执行 |
| 前端构建 | `npm --prefix frontend run build` | 本机 | 前端修改必跑 |
| 前端单测 | `npm --prefix frontend run test` | 本机 | 前端逻辑修改按风险执行 |
| test P0 | `npm run e2e:v1-p0` | test | mock 回归 |
| real-pre 预检 | `npm run e2e:real-pre:p0:preflight` | real-pre | 环境、Token、schema readiness |
| real-pre P0 | `npm run e2e:real-pre:p0` | real-pre | 真实链路；样本不足只能 PENDING/BLOCKED |
| 权限角色 | `npm run e2e:real-pre:roles` | real-pre | 多角色菜单与数据范围 |

## 当前状态口径

- 本轮 docs/Harness 变更只要求 safety-check、结构检查、旧内容维护计划、evidence、retro。
- 代码构建、容器重启和业务 E2E 在 `Scope=docs` 下明确跳过，不能写成业务 PASS。
- 若修改业务代码，必须回到 `agent-do.ps1` 对应 Scope 执行完整闭环。
