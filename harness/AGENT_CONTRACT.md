# Harness Agent Contract

## 目的

本文件定义后续 AI Agent 在本仓库执行工程任务时的强制协议。业务事实仍以 `CLAUDE.md`、`docs/README.md` 和 `docs/*.md` 为主源；本 Harness 只把读取、修改、构建、重启、验证、取证、提交和部署流程固定下来。

## 必读入口

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/README.md`
4. `harness/CURRENT_STATE.md`
5. `harness/TASK_ROUTING.md`
6. 当前任务对应的领域、流程、接口、数据、权限、验收和部署文档

## DDD 优化总规则

本项目采用 V1 模块化单体 + DDD 方式持续优化。DDD 优化只约束领域边界、任务顺序、执行证据和反馈同步，不改变当前 Spring Boot / PostgreSQL / Redis / Docker Compose 技术事实。

领域优化顺序固定为：

1. 用户域
2. 配置域
3. 订单域
4. 业绩域
5. 分析模块
6. 商品域
7. 达人域
8. 寄样域
9. Outbox 事件
10. 前端领域化
11. E2E 验收
12. 垃圾回收

所有 DDD 领域优化必须遵守：

- 先读 `harness/plans/DDD_OPTIMIZATION_ROADMAP.md` 和 `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`。
- 再读对应 `harness/instructions/*.md` 与 `docs/领域/*.md`。
- 修改前必须确认 `harness/FORBIDDEN_SCOPE.md`，不得扩大 V1 范围。
- 一次任务只推进一个主责领域任务卡；跨域影响只记录依赖和验证点。
- 修改后必须执行当前 Scope 的固定验证；docs-only 使用 `Scope=docs`。
- 测试或验证后必须更新 state；失败、阻塞或新风险必须写入 feedback 或 evidence report。
- 业务规则、架构边界或旧文档冲突不得由 AI 自行裁决，必须补证据并回到 ADR 或领域主源。

## 执行主线

任何工程任务必须按以下主线推进：

```text
明确任务
-> 读取上下文
-> 判断领域和 V1 边界
-> 收集证据
-> 最小修改
-> 构建
-> 重启对应 Docker 服务
-> 健康检查
-> 业务验证
-> 旧内容维护计划 / 归档 / 删除候选报告
-> 生成 evidence report
-> Git 提交与推送
-> 按用户明确要求部署远端
-> 生成 retro summary
-> 必要时升级 Harness 并更新 HARNESS_CHANGELOG.md
-> 输出结论和剩余风险
```

## 统一命令入口

默认入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "说明本次修改"
```

后续 Agent 不允许临时发明构建、重启、部署流程。除非用户明确要求 `test`，默认使用本地 `real-pre`；远端部署仍必须由用户明确要求后再传 `-DeployRemote true`。除非用户明确要求，否则必须优先调用 `harness/commands/agent-do.ps1` 或其中的子脚本。

## Definition of Done

只有同时满足以下条件，才允许声明任务完成：

- 已说明修改范围和影响范围。
- 代码或文档已按任务要求修改。
- 构建通过，或 `Scope=docs` 明确跳过构建。
- 对应 Docker 服务已重启，或 `Scope=docs` 明确不需要重启。
- 健康检查通过，或明确记录阻塞原因。
- 相关业务验证通过，或明确记录 `BLOCKED` / `PENDING` / `FAIL` 证据。
- 已生成旧内容维护计划，或明确说明本轮无需整理归档删除。
- 已生成 `harness/reports/evidence-*.md`。
- Git commit 已生成并 push 到当前分支上游，或明确说明本轮被用户要求不提交 / 推送。
- 若用户明确要求远端部署，远端部署完成并记录远端健康检查。
- 已生成 `harness/reports/retro-*.md`，或明确说明本次无需 Harness 升级。
- 剩余风险已列出。

## 状态结论口径

| 状态 | 含义 |
| --- | --- |
| `PASS` | 验证已执行且证据完整 |
| `PARTIAL` | 部分验证通过，但仍有明确未验证项或阻塞项 |
| `BLOCKED` | 外部 Token、权限包、真实样本、远端权限等阻塞 |
| `PENDING` | 未执行或样本不足，不能写成通过 |
| `FAIL` | 已复现失败，需要继续修复或回滚 |

## 证据优先级

1. 自动化测试报告
2. API 响应
3. SQL 查询结果
4. 容器日志 / 后端日志
5. 页面截图 / 浏览器 Network
6. 人工描述

不得用人工描述替代可运行脚本、SQL/API 或日志证据。

## 旧内容维护

每次任务完成前必须判断是否产生旧内容、重复内容、临时产物或过时文档。默认通过以下命令生成候选报告：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan
```

归档或删除必须使用 manifest，不允许凭自然语言直接删除：

- `Plan`：只生成候选报告，不移动、不删除。
- `Archive`：按 manifest 移动到 `harness/archive/retired-content/<timestamp>/`。
- `Delete`：按 manifest 删除；目录删除必须在 manifest 中写 `allowRecursive=true`。

源码、脚本、Docker 配置、数据库 migration、env、密钥和 Agent 入口文档默认受保护。确需处理源码类路径，必须显式传 `-AllowSourceCode` 并完成对应构建、重启、健康检查和业务验证。

## 与现有内容的关系

- `docs/`：事实主源，保留。
- `.claude/`：Claude 工作台和历史 Agent 工作流，保留。
- `scripts/`：已有启动、QA 和部署辅助脚本，保留。
- `harness/`：新增统一执行系统，负责把规则沉淀为脚本、清单、模板和报告入口。
