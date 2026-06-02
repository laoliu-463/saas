# Task Routing

## 总规则

任务开始后先判断所属类型和领域，再读取对应文档。不能只凭文件名或报错内容直接下结论。

默认执行环境为本地 `real-pre`。除非用户明确要求 `test`，或专项测试只能在 `test` 中验证，否则后端、前端、全链路和 Harness 变更都应使用 `-Env real-pre`。远端 `real-pre` 部署仍只在用户明确要求时添加 `-DeployRemote true`。

## 任务类型分流

| 任务类型 | 必读文件 | 可选文件 | 默认 Scope | 常用命令 / 脚本 | 验证与产出 |
| --- | --- | --- | --- | --- | --- |
| 后端功能修改 / 修复 | `CLAUDE.md`、`docs/README.md`、对应领域合同、`docs/05-API契约总表.md`、`docs/06-数据模型总表.md`、`harness/runbooks/backend-change.md` | 对应流程、`harness/skills/domain-alignment.skill.md` | `backend` | `agent-do.ps1 -Env real-pre -Scope backend` | Maven 构建、后端健康、相关 API/SQL、evidence、retro |
| 前端功能修改 / 修复 | `CLAUDE.md`、`docs/README.md`、对应流程、`docs/05-API契约总表.md`、`harness/runbooks/frontend-change.md` | `harness/skills/frontend-ux.skill.md`、页面相关 API 文档 | `frontend` | `agent-do.ps1 -Env real-pre -Scope frontend` | 前端构建、前端健康、页面/E2E、截图或日志证据 |
| 数据库结构变更 | `docs/06-数据模型总表.md`、相关领域合同、`harness/runbooks/database-change.md`、`harness/FORBIDDEN_SCOPE.md` | `docs/10-部署运行总览.md` | `backend` 或 `full` | `agent-do.ps1 -Env real-pre -Scope backend`；必要时只读 SQL 取证 | migration 幂等性、历史数据/回滚风险、构建、健康、DB 事实 |
| 接口联调 | `docs/05-API契约总表.md`、对应领域合同、对应流程、`docs/09-测试验收总览.md` | 前端 API 文件、后端 Controller/Service | `full` | `agent-do.ps1 -Env real-pre -Scope full` | 请求/响应、错误码、权限、日志、页面或 API 验证 |
| 第三方 API 联调 | `docs/08-第三方对接总览.md`、`docs/对接/*.md`、`docs/验收/real-pre联调手册.md`、`harness/runbooks/third-party-integration.md` | `harness/skills/real-pre-debug.skill.md` | `full` | `npm run e2e:real-pre:p0:preflight` | 真实 Token/开关/上游响应；缺权限或样本只能记 `BLOCKED`/`PENDING` |
| Docker / 容器 / 服务重启 | `docs/10-部署运行总览.md`、`harness/environment/docker-compose-map.md`、`harness/runbooks/docker-compose-operations.md` | `harness/FORBIDDEN_SCOPE.md` | 按任务 | `restart-compose.ps1`、`verify-local.ps1` | 不执行 `down -v`；记录 compose ps、健康检查 |
| 本地部署 / real-pre 变更 | `docs/10-部署运行总览.md`、`harness/environment/real-pre-env.md`、`harness/runbooks/real-pre-change.md` | `docs/deploy/README.md` | `full` | `agent-do.ps1 -Env real-pre -Scope full` | 构建、重启、健康、preflight、evidence、retro |
| 远端部署 | `harness/environment/remote-real-pre-env.md`、`harness/runbooks/remote-deploy.md`、`docs/deploy/README.md` | `docs/10-部署运行总览.md` | `full` | 仅用户明确要求时加 `-DeployRemote true` | 远端 docker ps、后端健康、前端健康、部署报告 |
| 测试与验收 | `docs/09-测试验收总览.md`、`docs/验收/*.md`、`harness/runbooks/test-validation.md` | 对应 eval | 按任务 | `npm run e2e:v1-p0`、`npm run e2e:real-pre:p0:preflight`、`mvn -f backend/pom.xml test` | 测试报告、失败截图/日志、结论不能夸大 |
| 业务规则修改 | `docs/01-V1交付范围与边界.md`、对应领域合同、对应流程、`docs/决策/ADR-002-V1范围优先级.md` | `harness/DOMAIN_MAP.md` | `full` | 按领域选择 `agent-do.ps1` Scope | 明确业务来源、状态机合法性、历史数据影响、回归测试 |
| Bug 排查 | `harness/CURRENT_STATE.md`、`harness/state/KNOWN_ISSUES.md`、对应领域/流程/API/数据文档 | 相关 skill、日志、最近 diff | 先不定 | 先收集日志/API/DB 证据，再选 Scope | 复现路径、证据链、阶段性结论、修复后验证 |
| 文档 / Harness 调整 | `AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`harness/README.md`、`harness/TASK_ROUTING.md` | `harness/feedback/garbage-collection-policy.md` | `docs` | `agent-do.ps1 -Env real-pre -Scope docs` | safety-check、结构检查、旧内容维护计划、evidence、retro |
| 性能问题 | 现象对应领域、`docs/09-测试验收总览.md`、相关日志/SQL/API 证据 | `docs/10-部署运行总览.md`、历史性能报告 | 先不定 | 先复现与采样，再选 Scope | 响应时间、SQL/日志、前端性能证据、回归风险 |
| 权限 / 数据范围 | `docs/07-权限与数据范围.md`、用户域、对应业务域 | `harness/evals/rbac-scope.evals.md` | `full` | `npm run e2e:real-pre:roles` 或专项 API/SQL | admin/group/self 多账号对比、越权负例 |
| 数据问题 / 数据漂移 | `docs/06-数据模型总表.md`、对应领域合同、`harness/state/KNOWN_ISSUES.md` | 对应 repair/backfill runbook | 先不定 | 先只读 SQL/API 取证；禁止裸 SQL 批量直改 | 数据前置、影响范围、repair dry-run、回滚计划 |
| 任务收尾与复盘 | `harness/AGENT_CONTRACT.md`、`harness/feedback/feedback-loop.md`、`harness/runbooks/closeout-and-gc.md` | `harness/feedback/garbage-collection-policy.md` | 当前 Scope | `collect-evidence.ps1`、`new-retro.ps1`、`retire-content.ps1` | evidence、retro、旧内容候选、状态更新、剩余风险 |

## 领域判断

- 商品、活动、转链、`pick_source_mapping`：商品域。
- 达人资料、标签、地址、跟进：达人域。
- 寄样申请、审批、发货、交作业：寄样域。
- 订单事实、退款事实、同步日志：订单域。
- 最终归属、提成、冲正、汇总：业绩域。
- 用户、角色、菜单、组织、数据范围：用户域。
- 规则参数、配置变更、配置审计：配置域。
- dashboard、报表、导出、汇总展示：分析模块。

## DDD 优化任务路由

当任务涉及 DDD、领域边界、业务规则下沉、跨域依赖治理、Facade 收口、事件解耦或前端领域化时，必须先读取：

1. `harness/AGENT_CONTRACT.md`
2. `harness/FORBIDDEN_SCOPE.md`
3. `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
4. `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
5. 当前领域对应的 `harness/instructions/*.md`
6. 当前领域对应的 `docs/领域/*.md`

DDD 优化顺序固定为：

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
11. E2E 验收与垃圾回收

### DDD 识别规则

| 关键词 / 现象 | 主责领域 | 必读 instruction |
| --- | --- | --- |
| 登录、权限、菜单、角色、组织、数据范围 | 用户域 | `harness/instructions/user-domain.md` |
| 系统配置、规则参数、提成比例、复制模板、`pick_extra` | 配置域 | `harness/instructions/config-domain.md` |
| 订单同步、`pick_source`、`colonel_buyin_id`、默认归因、`raw_payload`、双轨金额输入 | 订单域 | `harness/instructions/order-domain.md` |
| `performance_records`、提成、冲正、最终归属 | 业绩域 | `harness/instructions/performance-domain.md` |
| 看板、汇总表、趋势、排行、对账 | 分析模块 | `harness/instructions/analytics-module.md` |
| 商品库、活动商品、展示规则、转链、`pick_source_mapping` | 商品域 | `harness/instructions/product-domain.md` |
| 达人、认领、保护期、地址、标签 | 达人域 | `harness/instructions/talent-domain.md` |
| 寄样申请、审核、发货、签收、待交作业、交作业完成 | 寄样域 | `harness/instructions/sample-domain.md` |

### DDD 执行口径

- 单个任务只允许推进一个主责领域的一个任务卡；跨域影响必须记录为依赖，不得顺手重构。
- 修改前必须执行 `harness/skills/ddd-boundary-check.skill.md` 的边界检查清单。
- 修改后必须按当前 Scope 执行固定入口；文档任务使用 `Scope=docs`。
- 任务完成后必须更新 `harness/state/DOMAIN_STATUS.md`、相关 state 和 feedback；没有证据不得把任务状态写成完成。

## 执行入口选择

```powershell
# 文档 / Harness 变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope docs -Message "docs: update harness"

# 后端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope backend -Message "fix: update backend logic"

# 前端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope frontend -Message "fix: update frontend flow"

# real-pre 全链路验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "fix: update real-pre flow"

# 远端部署（用户明确要求时）
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote true -Message "deploy: real-pre update"
```

远端部署只有用户明确要求时才加：

```powershell
-DeployRemote true
```

## 直接子命令

```powershell
# 安全检查
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun

# docs-only 验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope docs -DeployRemote false -Message "docs: initialize harness engineering system" -DryRun

# 旧内容维护候选计划
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan -DryRun

# 远端部署子命令
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app -DryRun
```
