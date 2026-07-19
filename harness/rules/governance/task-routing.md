# Task Routing

## 总规则

任务开始后先判断所属类型和领域，再读取对应文档。不能只凭文件名或报错内容直接下结论。

默认执行环境为本地 `real-pre`。除非用户明确要求 `test`，或专项测试只能在 `test` 中验证，否则后端、前端、全链路和 Harness 变更都应使用 `-Env real-pre`。远端发布必须经 `main -> release/real-pre -> Jenkins` 唯一队列，普通 Agent 不得直接部署。

所有 `agent-do.ps1` 调用必须提供稳定 `-ReportKey <key>` 和显式 `-OwnedFiles '<path1>;<path2>'`。Evidence 覆盖 `reports/current/latest-<key>.md`；retro 默认内联。

## 任务类型分流

| 任务类型 | 必读文件 | 可选文件 | 默认 Scope | 常用命令 / 脚本 | 验证与产出 |
| --- | --- | --- | --- | --- | --- |
| 后端功能修改 / 修复 | `CLAUDE.md`、`docs/README.md`、对应领域合同、`docs/05-API契约总表.md`、`docs/06-数据模型总表.md`、`harness/rules/runbooks/backend-change.md` | 对应流程、`harness/rules/skills/ddd/domain-alignment.skill.md` | `backend` | `agent-do.ps1 -Env real-pre -Scope backend` | Maven 构建、后端健康、相关 API/SQL、稳定 evidence |
| 前端功能修改 / 修复 | `CLAUDE.md`、`docs/README.md`、对应流程、`docs/05-API契约总表.md`、`harness/rules/runbooks/frontend-change.md` | `harness/rules/skills/workflow/frontend-ux.skill.md`、页面相关 API 文档 | `frontend` | `agent-do.ps1 -Env real-pre -Scope frontend` | 前端构建、前端健康、页面/E2E、截图或日志证据 |
| 数据库结构变更 | `docs/06-数据模型总表.md`、相关领域合同、`harness/rules/runbooks/database-change.md`、`harness/rules/governance/forbidden-scope.md` | `docs/10-部署运行总览.md` | `backend` 或 `full` | `agent-do.ps1 -Env real-pre -Scope backend`；必要时只读 SQL 取证 | migration 幂等性、历史数据/回滚风险、构建、健康、DB 事实 |
| 接口联调 | `docs/05-API契约总表.md`、对应领域合同、对应流程、`docs/09-测试验收总览.md` | 前端 API 文件、后端 Controller/Service | `full` | `agent-do.ps1 -Env real-pre -Scope full` | 请求/响应、错误码、权限、日志、页面或 API 验证 |
| 第三方 API 联调 | `docs/08-第三方对接总览.md`、`docs/对接/*.md`、`docs/验收/real-pre联调手册.md`、`harness/rules/runbooks/third-party-integration.md` | `harness/rules/skills/ddd/real-pre-debug.skill.md` | `full` | `npm run e2e:real-pre:p0:preflight` | 真实 Token/开关/上游响应；缺权限或样本只能记 `BLOCKED`/`PENDING` |
| Docker / 容器 / 服务重启 | `docs/10-部署运行总览.md`、`harness/rules/environment/envs/docker-compose-map.md`、`harness/rules/runbooks/governance/docker-compose-operations.md` | `harness/rules/governance/forbidden-scope.md` | 按任务 | `restart-compose.ps1`、`verify-local.ps1` | 不执行 `down -v`；记录 compose ps、健康检查 |
| 本地部署 / real-pre 变更 | `docs/10-部署运行总览.md`、`harness/rules/environment/envs/real-pre-env.md`、`harness/rules/runbooks/real-pre-change.md` | `docs/deploy/README.md` | `full` | `agent-do.ps1 -Env real-pre -Scope full` | 构建、重启、健康、preflight、稳定 evidence |
| 远端发布 | `harness/rules/environment/envs/remote-real-pre-env.md`、`harness/rules/runbooks/remote-deploy.md`、`harness/rules/cicd-real-pre-policy.md` | `docs/10-部署运行总览.md` | Jenkins | 提升 PR 到 `release/real-pre`，由 `saas-real-pre-cd` 排队 | 完整 SHA、镜像摘要、运行版本、Flyway、发布清单与 Jenkins 报告 |
| 测试与验收 | `docs/09-测试验收总览.md`、`docs/验收/*.md`、`harness/rules/runbooks/governance/test-validation.md` | 对应 eval | 按任务 | `npm run e2e:v1-p0`、`npm run e2e:real-pre:p0:preflight`、`mvn -f backend/pom.xml test` | 测试报告、失败截图/日志、结论不能夸大 |
| 业务规则修改 | `docs/01-V2交付范围与边界.md`、对应领域合同、对应流程、`docs/决策/ADR-002-V1范围优先级.md`、`docs/决策/ADR-010-仓库阶段口径拍板为V2.md` | `harness/rules/governance/domains-map.md` | `full` | 按领域选择 `agent-do.ps1` Scope | 明确业务来源、状态机合法性、历史数据影响、回归测试 |
| Bug 排查 | `harness/rules/state/snapshots/01-当前项目状态.md`、`harness/rules/state/snapshots/KNOWN_ISSUES.md`、对应领域/流程/API/数据文档 | 相关 skill、日志、最近 diff | 先不定 | 先收集日志/API/DB 证据，再选 Scope | 复现路径、证据链、阶段性结论、修复后验证 |
| 文档 / Harness 调整 | `AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`harness/README.md`、`harness/rules/governance/task-routing.md` | `harness/rules/feedback/retire.md` | `docs` | `agent-do.ps1 -Env real-pre -Scope docs` | safety-check、结构检查、旧内容维护计划、稳定 evidence |
| 性能问题 | 现象对应领域、`docs/09-测试验收总览.md`、相关日志/SQL/API 证据 | `docs/10-部署运行总览.md`、历史性能报告 | 先不定 | 先复现与采样，再选 Scope | 响应时间、SQL/日志、前端性能证据、回归风险 |
| 权限 / 数据范围 | `docs/07-权限与数据范围.md`、用户域、对应业务域 | `harness/evals/rbac-scope.evals.md` | `full` | `npm run e2e:real-pre:roles` 或专项 API/SQL | admin/group/self 多账号对比、越权负例 |
| 数据问题 / 数据漂移 | `docs/06-数据模型总表.md`、对应领域合同、`harness/rules/state/snapshots/KNOWN_ISSUES.md` | 对应 repair/backfill runbook | 先不定 | 先只读 SQL/API 取证；禁止裸 SQL 批量直改 | 数据前置、影响范围、repair dry-run、回滚计划 |
| 任务收尾与复盘 | `harness/rules/governance/session-exit-gate.md`、`harness/rules/feedback/iteration.md`、`harness/rules/runbooks/governance/closeout-and-gc.md` | `harness/rules/feedback/retire.md` | 当前 Scope | `agent-do.ps1`、`retire-content.ps1` | 稳定 evidence、内联 retro、旧内容候选、状态更新、剩余风险 |
| Git 工作区治理 / 批次提交 | `harness/rules/skills/git/git-change-control.md`、`harness/rules/skills/git/git-batch-submit.md`、`harness/rules/skills/workflow/post-task-gc.md` | `harness/rules/governance/COMPLETION_GATES.md`、`harness/rules/governance/session-exit-gate.md` | `docs` 或 `git-batch` | `git status`、`git diff --cached --check` | 详细见下表 |

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

1. `harness/rules/policies/agent-contract.md`
2. `harness/rules/governance/forbidden-scope.md`
3. `harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md`
4. `harness/rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md`
5. 当前领域对应的 `harness/rules/instructions/domain/*.md`
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
| 登录、权限、菜单、角色、组织、数据范围 | 用户域 | `harness/rules/instructions/domain/user-domain.md` |
| 系统配置、规则参数、提成比例、复制模板、`pick_extra` | 配置域 | `harness/rules/instructions/domain/config-domain.md` |
| 订单同步、`pick_source`、`colonel_buyin_id`、默认归因、`raw_payload`、双轨金额输入 | 订单域 | `harness/rules/instructions/domain/order-domain.md` |
| `performance_records`、提成、冲正、最终归属 | 业绩域 | `harness/rules/instructions/domain/performance-domain.md` |
| 看板、汇总表、趋势、排行、对账 | 分析模块 | `harness/rules/instructions/domain/analytics-module.md` |
| 商品库、活动商品、展示规则、转链、`pick_source_mapping` | 商品域 | `harness/rules/instructions/domain/product-domain.md` |
| 达人、认领、保护期、地址、标签 | 达人域 | `harness/rules/instructions/domain/talent-domain.md` |
| 寄样申请、审核、发货、签收、待交作业、交作业完成 | 寄样域 | `harness/rules/instructions/domain/sample-domain.md` |

### DDD 执行口径

- 单个任务只允许推进一个主责领域的一个任务卡；跨域影响必须记录为依赖，不得顺手重构。
- 修改前必须执行 `harness/rules/skills/ddd/ddd-boundary-check.skill.md` 的边界检查清单。
- 修改后必须按当前 Scope 执行固定入口；文档任务使用 `Scope=docs`。
- 任务完成后必须更新 `harness/rules/state/snapshots/DOMAIN_STATUS.md`、相关 state 和 feedback；没有证据不得把任务状态写成完成。

## Task -> Completion Gate 路由

Agent 必须在任务开始时声明本次选择的 Gate。如果执行中发现影响范围扩大，必须升级 Gate，不能降级。

| 任务关键词 | 默认 Gate | 说明 |
| --- | --- | --- |
| 文档、计划、报告、提示词、harness 规则 | Gate 0 | 不允许改业务代码 |
| 后端、接口、Service、Mapper、SQL、权限、定时任务 | Gate 1 + Gate 3 | 必须跑后端和领域验证 |
| 前端、页面、按钮、路由、筛选、交互 | Gate 2 + Gate 3 | 必须打开页面验证 |
| 商品、达人、寄样、订单、业绩、配置、分析 | Gate 3 | 必须验证上下游 |
| 归因、订单同步、寄样完成、看板、P0、上线、验收 | Gate 4 | 必须跑 E2E |
| Docker、部署、real-pre、服务器 | Gate 1/2 + 运维验证 | 必须确认容器健康 |
| 修 bug | 至少复现 -> 修复 -> 回归 | 没有复现不能直接宣称修复 |

Gate 定义和验证要求见 `harness/rules/governance/COMPLETION_GATES.md`。

### Gate 选择升级规则

1. 如果任务开始时判断为 Gate 1，但修改后发现影响下游领域，必须升级到 Gate 3。
2. 如果任务涉及订单、寄样、业绩或看板，默认 Gate 4。
3. 如果任务涉及权限 / 数据范围修改且影响多个业务域，默认 Gate 4。
4. Bug 修复必须先复现，再修复，再回归；没有复现步骤不能直接宣称修复。

## Session Exit Gate 路由

所有任务结束后，无论任务类型和 Gate 级别，都必须进入 Session Exit Gate。

执行顺序：

```text
Completion Gate 通过
-> Session Exit Gate 五项检查
-> 生成 Session Exit Report
-> 更新 QUALITY_LEDGER.md（如涉及模块质量变化）
-> 输出最终状态
```

Session Exit Gate 定义和检查模板见 `harness/rules/governance/session-exit-gate.md`。

## Git 任务路由

Git 类任务必须按以下子任务路由执行，先读 `harness/rules/skills/git/git-change-control.md` 和 `harness/rules/skills/git/git-batch-submit.md`。

| 子任务 | 必读 | 触发条件 | 关键命令 | 输出 |
| --- | --- | --- | --- | --- |
| `GIT-INTAKE` | `git-change-control.intake.md` | 每个任务开始前 | `git status --short`、`git diff --name-only`、`git log -1 --oneline`、`git branch --show-current`、`git remote -v` | Intake 报告 + Dirty Classification 表 |
| `GIT-SCOPE` | `git-change-control.md` 第 3 节 | 任务范围变更时 | Allowed Change Set 检查 | scope 隔离清单 |
| `GIT-BATCH` | `git-batch-submit.md` | 多个任务 dirty 同时存在 | 批次划分 + 逐文件 `git add` + Staged Scope Gate | `git-batch-<N>-*.md` 报告 |
| `GIT-CLEANUP` | `git-change-control.md` 第 11 节 + `post-task-gc.md` | dirty 含 unknown 或临时文件 | 删除临时文件、归档报告、状态收口 | 调查 / 归档 / 删除报告 |
| `GIT-DEPLOY-GATE` | `git-change-control.md` 第 8 节 | 任何部署任务前 | 远端 fetch / checkout / pull / rev-parse | 远端 commit 对齐证据 |
| `GIT-EXIT` | `git-change-control.md` 第 9 节 | 每个任务结束前 | `git status --short`、`git diff --name-only` | 终态：DONE_CLEAN / DONE_WITH_REGISTERED_DIRTY / PARTIAL_DIRTY_REMAINING / BLOCKED_DIRTY_UNKNOWN |

### Git 任务与 Completion Gate 对应

| Git 子任务 | 默认 Gate | 验证命令 |
| --- | --- | --- |
| `GIT-INTAKE` | Gate 0 | `git status --short` 输出分析 |
| `GIT-BATCH`（docs） | Gate 0 | `safety-check -Scope docs -DryRun` |
| `GIT-BATCH`（frontend） | Gate 2 | `npm run build`、`vitest run`、`frontend safety-check` |
| `GIT-BATCH`（backend） | Gate 1 | `mvn test`、`backend safety-check` |
| `GIT-BATCH`（cleanup） | Gate 0 | `git diff --cached --check`、`safety-check -Scope docs -DryRun` |
| `GIT-DEPLOY-GATE` | Gate 1/2 + 运维 | `verify-local`、远端 health、commit 对齐 |
| `GIT-EXIT` | Session Exit Gate | 五项硬门禁 |

### Git 任务禁止

- 禁止把 `GIT-INTAKE` / `GIT-SCOPE` 与业务代码 commit 混在一起。
- 禁止 `GIT-BATCH` 中混入 `unknown` 文件。
- 禁止跳过 `GIT-EXIT` 直接进入下一任务。
- 禁止 `GIT-DEPLOY-GATE` 通过后未在远端验证就声明 DONE。

## 执行入口选择

```powershell
# 文档 / Harness 变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope docs -ReportKey task-key -OwnedFiles 'path1;path2' -Message "docs: update harness"

# 后端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope backend -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: update backend logic"

# 前端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope frontend -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: update frontend flow"

# real-pre 全链路验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: update real-pre flow"

# 远端发布前的本地候选验证；本命令不会部署服务器
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "release: validate real-pre candidate"
```

`-DeployRemote true` 已停用。远端发布只能由 Jenkins 从 `release/real-pre` 执行。

## 直接子命令

```powershell
# 安全检查
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun

# docs-only 验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope docs -ReportKey task-key -OwnedFiles 'path1;path2' -DeployRemote false -Message "docs: initialize harness engineering system" -DryRun

# 旧内容维护候选计划
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Plan -DryRun

# 直接 SSH 部署入口已退休；调用会失败并提示进入 Jenkins 发布队列
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\deploy-remote.ps1 -DryRun
```
