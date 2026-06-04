# Current State

> **目录指针**（2026-06-03 HARNESS-DEBT-GOVERNANCE-ITERATION 后）：
> - 状态记录：本文件
> - 业务状态快照：`state/current-business-state.md`
> - P0/P1 风险表：`state/p0-p1-register.md`
> - 已知问题卡片：`state/KNOWN_ISSUES.md`
> - 风险分类：`state/known-risks.md`
> - **Harness 自身债务**：`state/HARNESS_DEBT.md`
> - 任务历史：`state/TASK_HISTORY.md`
> - 部署口径：`state/DEPLOYMENT_STATE.md`
> - 验证入口：`state/VALIDATION_STATE.md`
> - 决策索引：`state/DECISIONS.md`
> - 变更日志：`HARNESS_CHANGELOG.md`

## 当前日期

- 记录日期：2026-06-04
- Harness 版本：v0.6.4（订单明细表字段对齐 real-pre 验证收口完成）

## 当前技术栈

- 后端：Spring Boot 3.2 / Java 17
- 前端：Vue 3 / Vite / Pinia / Naive UI / TypeScript
- 数据库：PostgreSQL
- 缓存：Redis
- 部署：Docker Compose
- 验收：Playwright、Maven、Vitest、PowerShell QA 脚本
- 环境：`test`、`real-pre`、远端 `real-pre`

旧文档中出现的 FastAPI、Celery、Python 爬虫式方案只作为历史背景，不作为当前运行事实。

## 当前环境事实

| 环境 | 前端 | 后端 | Compose | Env 文件 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `test` | `3000` | `8080` | `docker-compose.test.yml` | `.env.test` | mock 回归、P0 基线 |
| `real-pre` | `3001` | `8081` | `docker-compose.real-pre.yml` | `.env.real-pre` | 真实上游、上线前联调 |

real-pre 必须保持：

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- 不清库、不删除 volume、不使用 mock 数据证明真实闭环

## 当前已完成领域

- 用户域：登录、角色、菜单、组织和数据范围主链路已具备。
- 配置域：配置读取、变更和审计主链路已具备。
- 商品域：商品库、活动商品同步、转链和映射主链路已具备，历史推广中入库仍需持续验证。
- 达人域：达人资料、标签、地址和跟进主链路已具备。
- 寄样域：申请、审批、发货和订单事件自动完成链路已具备，real-pre 仍依赖真实归因订单样本。
- 订单域：订单事实、退款事实、同步日志和归因输入已具备；本地与远端 real-pre 已接入 6468 事实订单源并验证订单入库。
- 业绩域：最终归属、提成、冲正和汇总主链路已具备。
- 分析模块：dashboard、报表和只读汇总主链路已具备。

## 当前未闭环点

- 渠道链真实闭环仍依赖真实通过系统转链产生且带 `pick_source` 的订单样本。
- real-pre 已通过 6468 入库真实订单，但本轮样本 `pick_source=0`，不能证明渠道归因闭环。
- 寄样自动完成需要订单归因后的 `channel_id + talent_id + product_id + pay_time`。
- 推广中商品历史数据可能需要 repair / backfill。

## 当前 P0 / P1

- P0：6468 事实订单源本地与远端 real-pre 已入库；真实渠道订单归因样本仍不足，阻塞渠道链真实闭环 PASS。
- P0：performance_records.settle_amount 被回退逻辑污染（DASH-MONEY-P0-001），阻塞结算轨验收。
- P0：旧版 /dashboard/summary 单轨接口（DASH-MONEY-P0-002），字段映射错误。
- P0：V1 不做毛利但前端仍展示 grossProfit（DASH-MONEY-P0-004）。
- P1：寄样自动完成依赖真实归因订单样本。
- P1：推广中商品历史数据可能存在入库漂移。
- P1：权限注解和数据范围覆盖仍需持续审计。
- P1：DataApplicationService talentCommission 计算逻辑错误（DASH-MONEY-P1-002）。

## V1 核心闭环

### 渠道链

认领达人 -> 商品库选品 -> 复制讲解 / 转链 -> 寄样申请 -> 订单同步 -> 渠道业绩 -> 寄样自动完成。

### 招商链

同步活动 -> 活动商品入库 -> 商品上架 -> 审核寄样 -> 订单同步 -> 招商业绩。

### 管理链

用户角色 -> 数据范围 -> 规则配置 -> 各领域读取配置 -> 权限生效。

## 当前关键业务事实

- 管理链基本可闭环。
- 招商链大部分可闭环。
- 渠道链真实闭环仍依赖真实通过系统转链产生的订单样本。
- 当前 real-pre 6468 已入库真实订单，但本轮样本 `pick_source=0`，不能证明渠道归因闭环。
- 寄样自动完成依赖订单归因后的 `channel_id + talent_id + product_id`。
- 寄样自动完成的真实判定还需要 `pay_time` 命中。
- 推广中商品历史数据需要入库 / 重算，避免商品库选品入口不完整。
- 订单进入数据库不等于业务闭环；必须验证 `default_channel_id`、`default_recruiter_id`、`sample_requests` 状态流转和 `performance_records`。
- 真正闭环必须验证 `orders.pick_source`、`orders.default_channel_id`、`orders.default_recruiter_id`、`sample_requests`、`performance_records` 和 dashboard / 业绩归属。
- 商品库漂移优先通过活动商品手动同步或商品域 repair 入口处理，不允许裸 SQL 批量直改。

## 当前代码与文档关系

- `CLAUDE.md` 是仓库地图。
- `docs/README.md` 是文档地图。
- `docs/01-V1交付范围与边界.md` 是 V1 范围主源。
- `docs/02-业务闭环总览.md` 是业务闭环主源。
- `docs/03-领域架构总览.md` 与 `docs/领域/*.md` 是领域边界主源。
- `docs/05-API契约总表.md` 是内部 API 入口。
- `docs/06-数据模型总表.md` 是数据模型入口。
- `docs/07-权限与数据范围.md` 是 RBAC / 数据范围入口。
- `docs/08-第三方对接总览.md` 与 `docs/对接/*.md` 是 SDK / 上游接口入口。
- `docs/09-测试验收总览.md` 与 `docs/验收/*.md` 是验收入口。
- `docs/10-部署运行总览.md` 与 `docs/deploy/README.md` 是部署入口。

## DDD 优化合并状态

- 本次完成：将 DDD 优化计划增量合并进现有 Harness，不新建第二套 Harness。
- 新增主源：`harness/plans/DDD_OPTIMIZATION_ROADMAP.md`、`harness/plans/DDD_DOMAIN_TASK_MATRIX.md`。
- 新增领域执行入口：`harness/instructions/user-domain.md`、`config-domain.md`、`order-domain.md`、`performance-domain.md`、`analytics-module.md`、`product-domain.md`、`talent-domain.md`、`sample-domain.md`。
- 新增 DDD skill：`ddd-domain-optimization.skill.md`、`ddd-boundary-check.skill.md`、`ddd-post-task-sync.skill.md`。
- 状态跟踪入口：`harness/state/DOMAIN_STATUS.md`。
- 已完成：用户域 U-1 现状盘点（2026-06-03），报告路径：`harness/reports/user-domain-u1-inventory-20260603-090000.md`。
- 已完成：用户域 U-2 表结构与领域模型对齐（2026-06-03），报告路径：`harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`。核心结论：7 张用户域表字段与 Entity 对齐；同时确认 P0 风险：`dept_type` 常量类、migration 和 real-pre 数据值冲突。
- 已完成：用户域 U-2.5-A dept_type 统一与最小修复方案设计（2026-06-03），报告路径：`harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`。本次未修改 Java / Vue / SQL，未执行数据库写操作，未重启容器，未部署；仅执行 real-pre 只读 SELECT 和 docs 范围安全检查。
- 已完成：用户域 U-2.5-B dept_type 最小修复（2026-06-03），报告路径：`harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`。统一 Java 标准到 `DeptType.java`，删除 `DeptTypes.java`，更新 seed/init 和既有 dept_type 幂等脚本；未新增独立 migration，未执行 real-pre 数据库写操作，未重启容器，未部署远端。
- 已完成：TEST-1 全量后端测试 6 failures 归因与最小修复（2026-06-03），报告路径：`harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`。指定失败集合复现时实际为 53 tests / 3 failures / 7 errors；修复仅涉及 `SysUserServiceTest`、`SysUserControllerTest`、`CommissionRuleServiceTest`、`CommissionRuleControllerTest` 测试夹具/断言，未修改业务代码、Vue、数据库或容器配置；全量 `mvn -f backend/pom.xml test` 已通过 1671 tests / 0 failures / 0 errors。
- 下一阶段：建议先执行 U-2.5-D 安全拆分提交与状态收口，再进入用户域 U-3 CurrentUser / PermissionContext 统一；real-pre 历史 `sys_dept.dept_type` 如需写库修复，必须另起 DB 任务执行备份、dry-run、审批和回滚方案。
- 重要口径：U-2.5-B 是 dept_type 最小修复，不代表 Java / Vue / SQL 已完成完整 DDD 重构。
- 已完成：Completion Gate 完成门禁系统新增（2026-06-03）。新增 `harness/COMPLETION_GATES.md`，定义 Gate 0-4 五个完成门禁；修改 `AGENT_CONTRACT.md` 增加禁止提前完成强约束；修改 `FORBIDDEN_SCOPE.md` 增加 13 条禁止虚假完成规则和 6 种合法状态；修改 `TASK_ROUTING.md` 增加 Task -> Gate 路由表和升级规则；修改 `state/DOMAIN_STATUS.md` 增加任务结束状态更新规则。
- 已完成：Session Exit Gate 会话退出门禁 + Quality Ledger 质量账本新增（2026-06-03）。新增 `harness/SESSION_EXIT_GATE.md`，定义 Clean State 五项硬门禁（Build Clean、Test Clean、Progress Recorded、Artifacts Clean、Startup Path Clean）和退出检查模板；新增 `harness/QUALITY_LEDGER.md`，初始化 9 个模块质量评分；修改 `AGENT_CONTRACT.md` 增加 Session Exit Gate 强约束；修改 `FORBIDDEN_SCOPE.md` 增加 10 条禁止留下脏状态规则；修改 `TASK_ROUTING.md` 增加 Session Exit Gate 路由；修改 `state/DOMAIN_STATUS.md` 增加 Session Exit 时领域状态更新规则。
- 已完成验证：FUNC-001 商品库卡片默认态与悬浮展开态改造（2026-06-03）。报告路径：`harness/reports/func-001-product-card-hover-ui-20260603-111451.md`，evidence：`harness/reports/evidence-20260603-111733.md`。完成商品库卡片佣金兜底、hover 详情字段顺序和 E2E real-pre 验证；未修改后端或数据库。会话最终状态为 `PARTIAL`，原因是工作区存在任务前遗留 dirty 变更且未安全提交/推送。
- 已完成验证：P-FIX-001C 商品库分页弱化改造（2026-06-03）。报告路径：`harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`，evidence：`harness/reports/evidence-20260603-113632.md`。方案为"加载更多"；商品库默认 `pageSize=100`；已加载商品 append 展示，筛选/推广状态变更重置到第一页；未修改后端或数据库；已执行 frontend build、typecheck、相关 Vitest、real-pre 前端容器重启、健康检查和页面 smoke。会话最终状态为 `PARTIAL`，原因是工作区存在任务前遗留 dirty / untracked 且未安全提交/推送，远端未部署。
- 已完成：P-DIAG-002 商品库数量不足排查（2026-06-03）。报告路径：`harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md`。纯只读排查，未修改代码、未写库、未重启容器、未部署远端。三个并存根因：(A) 远端同步任务禁用（`PRODUCT_ACTIVITY_SYNC_ENABLED` 未设置），(B) 唯一索引 `uk_pos_one_displaying_per_product` 冲突导致同步事务回滚，(C) 过期活动商品卡 PENDING 状态。本地 1958 展示中 vs 远端 420 展示中。下一步建议：P-FIX-002A 同步链路修复 / P-FIX-002B 展示规则重算 / P-FIX-002D 远端部署对齐。
- 已完成：P-FIX-002A 商品活动同步任务启用与 5 分钟周期配置（2026-06-03）。报告路径：`harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`。将 `@Scheduled` 默认 cron 从每 2 小时改为每 5 分钟，新增启动配置日志，compose environment 显式传递同步参数，部署 runbook/脚本新增同步参数检查。未执行数据库写操作、未重启容器、未部署远端。必须先完成 P-FIX-002B 修复唯一索引冲突再实际启用远端同步。
- 已完成配置准备：P-FIX-002 商品库数量不足修复（2026-06-03）。报告路径：`harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`，evidence：`harness/reports/evidence-20260603-122021.md`。包含四阶段：A) 同步周期 5 分钟配置（已完成）；B) 唯一索引 `uk_pos_one_displaying_per_product` 冲突修复——`ProductDisplayRuleService.applyNormalDisplayDedup` 改为三阶段持久化（先降级旧 DISPLAYING，再处理其他非 DISPLAYING，最后升级新 winner），新增/补齐 4 个相关测试；C) 只读对账（本地 7284 快照 / 1963 展示中 / 无重复 DISPLAYING / 716 推广中但未展示）；D) 远端参数对齐（配置已准备，待独立部署任务）。未执行数据库写操作、未重启容器、未部署远端。任务口径 `DONE_CONFIG_READY`；Completion Gate 口径 `PARTIAL`，因为新 jar 尚未加载到运行态。
- 已完成运行态验证：P-FIX-002D 本地 real-pre 重启加载商品同步修复并验证 5 分钟同步任务（2026-06-03）。报告路径：`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`。重启 backend-real-pre 容器，新 jar 已加载（Jun 3 04:15 UTC），同步配置生效（`enabled=true, cron=0 */5 * * * ?`），两个 5 分钟周期正常执行（ok=3+ok=0, fail=0），零唯一索引冲突。同步后本地 7323 快照 / 2377 DISPLAYING / 4575 HIDDEN / 371 PENDING / 无重复。API total=2377 与 SQL 一致。状态 `DONE_RUNTIME_VERIFIED`。未执行手工数据库写操作、未部署远端。可进入远端部署对齐阶段。
- 已完成远端部署验证：P-FIX-002D-REMOTE 远端部署对齐商品同步修复并验证 5 分钟同步任务（2026-06-03）。报告路径：`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md`。远端 commit 对齐 `dea06e4c`（通过 Gitee 推送后拉取），同步参数生效（`enabled=true, cron=0 */5 * * * ?`），两个 5 分钟周期正常执行（ok=5+ok=0, fail=0），零唯一索引冲突。远端 3846 快照 / 604 DISPLAYING / 1114 HIDDEN / 2128 PENDING / 无重复。API total=604 与 SQL 一致。远端 env 手工补齐 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON`。状态 `DONE_REMOTE_VERIFIED`。未执行手工数据库写操作、未清库。下一步：P-VERIFY-002 远端商品库数量复核（建议 1-2 小时后），如有需要再做 P-FIX-002E repair。
- 已完成提交与部署：GIT-BATCH-2 frontend-product-ui（2026-06-03 14:08）。报告路径：`harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md`。commit `5fe6ba23 feat(product-ui): product card hover expand and library load-more pagination`；5 文件：ProductSelectionCard.vue、ProductSelectionCard.test.ts、ProductLibrary.vue、ProductLibrary.test.ts（新建 188 行）、03b-product-library-drawer-fields.spec.ts。typecheck / 18 vitest / build / safety-check 全 PASS。推送 gitee + origin 成功（`49035d3c..5fe6ba23`）。远端 frontend-real-pre rebuild + restart 完成，4 容器全部 healthy；backend-real-pre 同步 recreate 但 jar 来源与远端 commit `5fe6ba23` 一致（无 backend 变更），行为零差异。远端 healthz：backend `{"status":"UP"}` / frontend `ok`；新 ProductLibrary bundle `ProductLibrary-iepQIAKR.js` 已部署。未修改后端、SQL、数据库、`docker compose down -v`、远端数据库写操作、商品同步逻辑、订单归因、业绩计算、寄样状态机。状态 `DONE`。残留 dirty：Batch 3 backend 14 + Batch 4/5 报告 20。下一步：Batch 3 `backend-user-domain-u2_5-test1` 提交与部署。
- 已完成提交与部署：GIT-BATCH-3 backend-user-domain-u2_5-test1（2026-06-03 14:49:36）。报告路径：`harness/reports/git-batch-3-backend-user-domain-u2_5-test1-20260603-144936.md`。commit `c470dc29 fix(user): unify dept type constants and stabilize tests`；14 文件：DeptType.java、DeptTypes.java(D)、SysDeptService.java、alter-sys-dept*.sql、init-db.sql、migrate-sys-dept-dept-type.sql、5 个测试文件 + DeptTypeTest.java。`mvn test` 1675 / 0 / 0；`mvn package` BUILD SUCCESS；`git diff --cached --check` PASS；backend `safety-check` PASS。推送 gitee + origin 成功（`5fe6ba23..c470dc29`）。远端 backend-real-pre 用干净 commit 快照构建的 jar 重新构建并重启；frontend 未重启。backend health PASS。未执行数据库写操作；未执行 migration 命令。状态 `DONE`。残留 dirty：4 modified（M 4）+ 23 untracked（报告 23），与本批次正交。
- 已完成：GIT-HARNESS-001 Git 工作区治理与批次提交门禁强化（2026-06-03）。报告路径：`harness/reports/git-harness-001-worktree-governance-20260603-*.md`。新增 `harness/skills/git-change-control.md`（Git 全套 Gate：Intake / Allowed Change Set / Dirty Classification / Staged Scope / Commit / Push / Deploy / Exit / Unknown / Rollback）、`harness/skills/git-batch-submit.md`（批次提交流程）、`harness/skills/post-task-gc.md`（Git 清理流程）。修改 `AGENT_CONTRACT.md` 新增"Git 工作区治理强约束"12 条；`TASK_ROUTING.md` 新增"Git 任务路由"六个子任务（GIT-INTAKE / GIT-SCOPE / GIT-BATCH / GIT-CLEANUP / GIT-DEPLOY-GATE / GIT-EXIT）；`FORBIDDEN_SCOPE.md` 新增"Git 工作区治理禁止事项"18 条；`COMPLETION_GATES.md` 新增 Gate G0-G4 Git 子门禁；`SESSION_EXIT_GATE.md` 新增"Git 状态 Clean"作为第六项硬门禁和 5 条 Git 禁止事项。更新 `KNOWN_ISSUES.md` 记录"Git 工作区 dirty 膨胀与批次提交门禁缺失"为 fixed；更新 `DECISIONS.md` 新增 2026-06-03 Git 工作区治理决策摘要（6 条）。核心约束：所有任务必须按 Git Intake → Allowed Change Set → Staged Scope → Commit → Push → Deploy Commit → Git Exit 顺序执行；任务终态只能为 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。docs-only 任务，仅修改 harness 文档与状态文件；未修改后端 / 前端 / SQL / Docker / env / 数据库；未重启容器；未部署远端。状态 `DONE`（docs-only）。
- 已完成提交：GIT-BATCH-4-REPORTS 报告批次提交（2026-06-03 15:15）。报告路径：`harness/reports/git-batch-4-reports-20260603-151500.md`。commit `7c69986e docs(harness): sync remaining task reports`；24 文件全部为 `harness/reports/*.md` 报告，覆盖 B3-SCOPE-001 / B3-VERIFY-001 / TEST-1（主报告+evidence+retro）/ U-2.5-B（主报告+evidence）/ FUNC-001（主报告+evidence+retro）/ P-FIX-001C（早期+最终主报告+evidence+retro）/ P-FIX-002（evidence）/ P-FIX-002D（retro）/ GIT-BATCH-2 / GIT-BATCH-3 / content-retire × 3 / 本批次报告。验证：`git diff --check` PASS（仅 CRLF 警告）、`git diff --cached --check` PASS、`safety-check -Scope docs -DryRun` PASS、`verify-local -Scope docs` PASS。推送 `gitee` + `origin` 成功（`ba7f1996..7c69986e`）。未修改后端 / 前端 / SQL / Docker / 数据库；未执行数据库操作；未重启容器；未部署远端。Git Exit Gate 终态 `DONE_WITH_REGISTERED_DIRTY`。残留 dirty：1（`backend/src/main/resources/application-real-pre.yml` P-FIX-002A 同步配置残留，5 行 `product.activity.sync`，已分类为 `previous_partial`，留待 P-FIX-002-CONFIG-RESIDUAL 任务单独处理）。状态 `DONE`。
- 重要口径：本次治理针对 SYNC-PLAN-001 暴露的"多任务 dirty 膨胀 + 提交门禁缺失"问题。Batch 3 提交时已使用 Dirty Classification 和 B3-SCOPE-001 范围隔离作为本任务新规的事前验证，证明强约束可执行。下一步：执行 P-FIX-002-CONFIG-RESIDUAL 单独处理 `application-real-pre.yml`，然后执行 GIT-EXIT-001 最终清洁检查。两者完成后才能进入新业务任务（U-3 / P-VERIFY-002 等）。
- 已完成：P-FIX-002-CONFIG-RESIDUAL 处理 application-real-pre.yml 商品同步配置残留（2026-06-03 15:20）。报告路径：`harness/reports/p-fix-002-config-residual-20260603-152000.md`。state commit `78bdf8fa docs(harness): GIT-BATCH-4-REPORTS record batch 4 state`（CURRENT_STATE + HARNESS_CHANGELOG v0.5.1）已 push 双远端；`application-real-pre.yml` 已 `git restore` 至 HEAD 一致（5 行 `product.activity.sync` 与 `application.yml` + compose env 行为等价，无需提交）。未修改业务代码、未写库、未重启容器、未部署远端。Git Exit Gate 终态 `DONE_CLEAN`。
- 已完成（代码 + 运行态）：P0-ORDER-001 真实订单同步与渠道可见修复（2026-06-03 17:30–18:04）。报告路径：`harness/reports/p0-order-001-real-order-visible-20260603-180450.md`、`harness/reports/p0-order-001-diagnosis-20260603-173500.md`、`harness/reports/p0-order-001-intake-20260603-172923.md`。**根因**：`RealDouyinOrderGateway#listSettlement` 调用 `buyin.colonelMultiSettlementOrders` 时硬编码 `time_type=update` + 10 分钟滚动窗口，刚 PAY_SUCC 订单遇上游 update_time 延迟会丢单（抖音不存在 `time_type=pay`）。**修复**：新增 `OrderSyncService#syncPayRecentWindow()` 6 小时大窗口低频回扫，独立锁 `ORDER_SYNC_PAY_RECENT` + 独立 Redis 水位 `order:sync:pay_recent_last_time`，不覆盖 10 分钟增量同步水位；`OrderSyncJob` 新增 `syncPayRecent()` `@Scheduled` 每 30 分钟；服务层 / job 层同步日志增加 `mode/timeType/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed` 维度；attribution_status 字段已存在无需 migration；admin/channel 数据范围逻辑已正确（admin ALL 见全部，channel PERSONAL 只见自己已归因，`/orders/unattributed` admin 排查 NO_PICK_SOURCE / NO_MAPPING）。**测试**：新建 `OrderSyncServiceTest` 6 用例、`OrderSyncJobTest` +4、`OrderControllerTest` +3，全量 `mvn test` **1688 / 0 / 0** 通过；`mvn package` BUILD SUCCESS；`safety-check -Scope backend -DryRun` PASS；本地 backend-real-pre rebuild + recreate 后 `{"status":"UP"}`，双 scheduler 立即首次执行：INCREMENTAL 窗口 628s（10min + overlap），PAY_RECENT 窗口 21600s（6h），两 Redis key 共存独立。**Final Status**：`PARTIAL_DIRTY_REMAINING`——代码 / 测试 / 构建 / 容器 / 日志 / 双轨调度全部 PASS，真实订单端到端业务验证 BLOCKED_BY_SAMPLE（需等待商务侧真实付款样本）；本任务未执行 git commit/push，dirty 已分类，残留 3 个 untracked 报告（2 个并行会话的 ORDER-API-729-VERIFY + 1 个 P-FIX-002-CONFIG-RESIDUAL）与本任务正交，留待下个会话单独批次处理。未修改寄样 / 达人 / 商品 / 业绩 / 独家 / 前端 / 数据库 schema / docker-compose / env / `application-real-pre.yml`，未 `git add .`，未 `down -v`。
- 已完成远端部署验证：ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY（2026-06-03 20:57）。报告路径：`harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md`。远端 `/opt/saas/app` commit 对齐 `77b723b6`，远端工作区 clean；远端无 Maven，使用本地 `77b723b6` jar（SHA256 `ea6e86bda9684f035338f8e1300b7331bac73076dee00ee35ee7f4ad49142450`）上传后仅重建 `backend-real-pre`；后端 health `{"status":"UP"}`。远端 12:50 UTC 同步日志确认 6468 `ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel fetched=100 inserted=100 updated=0`，2704 `ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders fetched=0` 仍保留；SQL 只读确认 `colonelsettlement_order count=100`，`order_amount/pay_time/estimate_*` 有值，`settle_amount/effective_*` 为空；管理员 `/api/orders total=100`、`/api/orders/unattributed total=100`。渠道正向可见性仍为 `PENDING_BY_SAMPLE`：远端 100 单 `pick_source=0`、`channel_user_id=0`、全部 `COLONEL_MAPPING_NOT_FOUND`。未清库、未执行破坏性 SQL、未部署前端。
- 已完成远端部署验证：P0-SAMPLE-001-REMOTE-VERIFY（2026-06-03 22:10）。报告路径：`harness/reports/p0-sample-001-remote-verify-20260603-221004.md`。远端 `/opt/saas/app` 从 `77b723b` 快进到 `ab03d72`（目标 `ab03d729`），远端工作区 clean；使用 Maven Docker 镜像构建后端 jar，仅 rebuild/recreate `backend-real-pre` 与 `frontend-real-pre`，未使用 `-v`、未清库、未重建 PostgreSQL / Redis volume、未执行数据库写入 SQL。远端 backend/frontend health PASS。商品库快照 `81a5d39b-e661-3b19-96d3-e55b145f15f1` 快速寄样成功生成 `sample_request=9c655738-76b1-4fd0-9676-8f307c694f3f`，`request_no=QS2026060337AF9AAB`，`status=1 / PENDING_AUDIT / 待审核`；`product` 主表已物化；`crawler_talent_info` 缺失时按 `talent.douyin_uid=56723079343` 兜底。审核列表：`admin` 可见、`biz_leader` 可见、`biz_staff` 不可见；失败分支返回 `items[].message` 明细。状态 `PARTIAL`：远端核心修复链路通过，但 `channel_staff` 无私海达人、等价渠道账号“玄同”不可用已知测试口径登录，且远端全部 `product_operation_state.assignee_id` 为空，不能严格证明“该商品分配给 biz_leader”前提。额外观察：`channel_staff` 可查到该待审核单，建议另起 RBAC 专项复核。

## 旧文档冲突处理

| 冲突 | 当前处理 |
| --- | --- |
| 旧 V2.2 完整方案 vs 当前 V1 范围 | 以 `docs/01-V1交付范围与边界.md` 和本文件为准 |
| FastAPI / Celery 旧技术建议 | 标记为历史归档，当前以 Spring Boot 源码为准 |
| 独家达人 / 独家商家 | V1 不启用 |
| 毛利字段设计 | V1 不做毛利口径扩展；不得扩大为财务结算 |
| 寄样 30 天自动关闭 | V1 不自动关闭，按当前寄样状态机和订单事件验证 |
| 个别品负责人覆盖 | V1 不做 |
| 物流 API 自动跟踪 | V1 以手动物流和可证据物流接口为准 |

冲突不得靠 AI 自行裁决；必须补充证据并写入 `docs/决策/ADR-002-V1范围优先级.md`。

- 已完成：SYNC-PLAN-001 本地未推送内容分批同步与部署计划（2026-06-03）。报告路径：`harness/reports/sync-plan-001-batch-sync-deploy-plan-20260603-143000.md`。共清点 110 个 dirty 文件，分为 5 个批次：Batch 1 harness-docs（19 文件）、Batch 2 frontend-product-ui（5 文件）、Batch 3 backend-user-domain-u2_5-test1（15 文件）、Batch 4 任务报告（15 文件）、Batch 5 cleanup-retire（78 文件）。推荐执行顺序：Batch 4 → Batch 1 → Batch 5 → Batch 2 → Batch 3。未提交业务代码、未执行数据库操作、未部署远端。

## 待确认

- 真实渠道订单样本是否已经通过系统转链产生。
- 远端每次部署是否要求同步执行完整 `e2e:real-pre:p0`、`roles` 与 preflight。
- 若远端 SSH alias、目录或仓库 remote 变化，需要更新 `harness/runbooks/remote-deploy.md` 和 `harness/commands/deploy-remote.ps1` 参数默认值。
- 远端部署已纳入活动商品 schema guard，但完整数据库迁移治理仍未闭环；`migrate-all.sql` 含历史非幂等 DML，不允许在 Harness 中无条件重复执行。

## 状态子系统

细分状态见：

- `harness/state/current-business-state.md`
- `harness/state/p0-p1-register.md`
- `harness/state/real-pre-evidence-index.md`
- `harness/state/known-risks.md`

## Harness 自身治理（HARNESS-DEBT-GOVERNANCE-ITERATION，2026-06-03 23:03）

- **任务**：对 harness 自身进行系统盘点、债务登记、五子系统整理、任务生命周期 / 债务防回流机制建立。docs-only 任务；未修改业务代码。
- **范围**：仅修改 / 新增 `harness/` 下的 docs / state / runbook / plans / feedback / environment；`docs/` 与业务代码、SQL、Docker、env、远端部署均不动。
- **新增文件（7 份）**：
  - `harness/state/HARNESS_DEBT.md`（harness 自身 25 条 DEBT 登记；分 P0/P1/P2/P3 + 关闭条件）。
  - `harness/runbooks/task-lifecycle.md`（七 Gate：Intake / Scope / Implementation / Verification / Evidence / Git / Exit）。
  - `harness/runbooks/scope-command-matrix.md`（scope → command 单一决策表）。
  - `harness/runbooks/debt-governance.md`（债务防回流 6 段机制）。
  - `harness/environment/CHEATSHEET.md`（环境速查表：端口 / health / env / 远端 / 禁止命令）。
  - `harness/feedback/docs-only-template.md`（docs-only 任务最小化 evidence 模板）。
  - `harness/plans/HARNESS_ITERATION_ROADMAP.md`（Harness 自身迭代路线图）。
- **修改文件（10 份）**：
  - `HARNESS_CHANGELOG.md`：v0.6.0 条目。
  - `CURRENT_STATE.md`：顶部目录指针；版本 v0.4.0 → v0.6.0；本治理段。
  - `README.md`：三目录并存说明（主源 = `harness/`，`harness/doc/` 仅聚合）。
  - `QUALITY_LEDGER.md`：与 DOMAIN_STATUS 分工指针；Harness 等级 B → A-。
  - `instructions/safety-rules.md`：主源指针 → FORBIDDEN_SCOPE。
  - `feedback/garbage-collection-policy.md`：reports/*.md 纳入受保护。
  - `state/KNOWN_ISSUES.md` / `p0-p1-register.md` / `known-risks.md`：三文件分工互引。
  - `state/DEPLOYMENT_STATE.md` / `VALIDATION_STATE.md`：主源指针。
  - `state/TASK_HISTORY.md`：补 14 行任务摘要。
  - `doc/00-HARNESS-README.md`：主源说明。
- **债务收敛**：
  - 25 条 DEBT 中关闭 18 条（DEBT-001/002/003/004/005/006/007/008/009/010/011/012/015/017/018/019 + 023/025 视为 in-progress → fixed；其中 023/025 是新建登记文件即视为关闭）。
  - 7 条保留 deferred：DEBT-013（ad-hoc log）/ 014（reports 归档）/ 016（DDD 矩阵）/ 020/022（agent-do / safety-check 扩展）/ 021（未变更文件检测）/ 024（.env.real-pre 引用统一）。
- **本任务收益**：
  - 后续 Agent 可按 `runbooks/task-lifecycle.md` 七 Gate 顺序执行；不允许"做一半直接交付"。
  - harness 自身债务集中到 `state/HARNESS_DEBT.md`；不允许"债务只写在聊天里"。
  - 命令决策从 TASK_ROUTING 表中独立到 `runbooks/scope-command-matrix.md`；Agent 不必再翻两份文档。
  - 环境速查从 6 份 environment/*.md 收敛到 1 份 `CHEATSHEET.md`。
- **状态**：`DONE`（docs-only）。
- **下一步**：
  - HARNESS-DEBT-GC-001（清理 12 个 ad-hoc log + .gitignore 增强）。
  - HARNESS-AGENT-DO-HARDEN（agent-do.ps1 增 `-Scope harness` + safety-check 扩展 scope）。
  - 业务侧：等待真实渠道订单样本后做 RISK-001 / RISK-007 渠道归因正向可见性验证。

## Harness 清理（HARNESS-DEBT-GC-001，2026-06-04 00:14）

- **任务**：对 harness 自身做一次安全清理、归档、瘦身；不破坏证据链、状态文件、业务代码。
- **范围**：docs-only / harness-cleanup；新增 `harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json`、`harness/reports/harness-debt-gc-001-inventory-20260604-001052.md`、`harness/reports/content-retire-20260604-001401.md`、`harness/reports/evidence-20260604-001401-harness-debt-gc-001.md`、`harness/reports/retro-20260604-001401-harness-debt-gc-001.md`；修改 `harness/state/HARNESS_DEBT.md`、`harness/QUALITY_LEDGER.md`、`harness/HARNESS_CHANGELOG.md`。
- **清理对象（3 项，全部已执行删除）**：
  - `nul`（Windows 设备文件残留 0 bytes，`.gitignore` 已覆盖，工作区层删除）。
  - `test-results/`（playwright 测试结果目录，`.gitignore` 已覆盖，可再生）。
  - `playwright-report/`（playwright HTML 报告 528 KB，`.gitignore` 已覆盖，可再生）。
- **归档对象（0 项）**：盘点 76 份 `harness/reports/*.md` 全部被其他 evidence/retro/git-batch/state 文件交叉引用（最少 1 处、最多 9 处）；3 对"重复"早期报告（u1 inventory 120000 / u2 schema 093000 / p-fix-001c 112740）均被主源/批次报告显式引用；7 份 content-retire Plan 报告全部被 git-batch / sync-plan / evidence 引用。**结论：reports/ 目录在当前证据链结构下没有可安全归档的对象**。
- **Manifest 路径**：`harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json`。
- **脚本**：`harness/commands/retire-content.ps1 -Action Delete -Manifest ...` 显式 manifest 删除，DryRun 验证保护检查通过。
- **DEBT 收敛**：
  - DEBT-013（12 个 ad-hoc log 未 .gitignore）deferred → **fixed**（实际只有 1 个 `nul` 设备文件 + 2 个 playwright 目录，全部已 gitignore + 全部已删除）。
  - DEBT-014（reports/ 72 份未触发归档）deferred → **wontfix**（76 份全部被证据链引用；归档会破坏引用；建议保留为受 GC 政策保护状态）。
- **本任务收益**：
  - 工作区释放 `playwright-report/index.html` 528 KB + `test-results/playwright/` 测试结果 + 1 个 0-byte `nul`。
  - DEBT-013 关闭；DEBT-014 重新分类为 wontfix（语义明确：reports 目录受保护、不可随意归档）。
  - GC 政策不变：受保护 reports 全部 keep；生成型临时目录明确可删。
- **状态**：`DONE`（docs-only）。
- **下一步**：
  - HARNESS-AGENT-DO-HARDEN（agent-do.ps1 增 `-Scope harness` + safety-check 扩展 scope）。
  - 业务侧：等待真实渠道订单样本后做 RISK-001 / RISK-007 渠道归因正向可见性验证。

## DASHBOARD-MONEY-AUDIT-001 数据看板资金口径审查（2026-06-04 13:19）

- **任务**：对数据看板 / 资金指标进行只读审查，核查双轨金额、服务费收益、提成、字段映射、SQL 汇总口径是否符合 V1 需求。
- **范围**：docs-only / 只读审查；未修改 Java / Vue / SQL / Docker / .env。
- **审查维度**：8 个维度（A-H）全部完成：字段链路、双轨计算、看板指标、时间口径、权限数据范围、SQL 对账、前端展示、测试覆盖。
- **结论**：**FAIL**。发现 4 个 P0 + 4 个 P1 + 2 个 P2 问题。
- **P0 问题**：
  - DASH-MONEY-P0-001：PerformanceCalculationService:113 settle_amount 回退逻辑污染业绩表（404 条 settle_amount=pay_amount，应=0）。
  - DASH-MONEY-P0-002：旧版 /dashboard/summary 是单轨接口，无双轨结构。
  - DASH-MONEY-P0-003：aggregateDashboardSummary() 被 P0-001 污染（下游效应）。
  - DASH-MONEY-P0-004：V1 不做毛利但前端仍展示 grossProfit。
- **P1 问题**：
  - DASH-MONEY-P1-001：旧版 DashboardService 回退用 settle_colonel_commission 当服务费。
  - DASH-MONEY-P1-002：DataApplicationService.buildMetrics talentCommission 计算=0。
  - DASH-MONEY-P1-003：estimate 轨使用 create_time 而非 pay_time。
  - DASH-MONEY-P1-004：旧版 DashboardController 无 time_filter_type 参数。
- **SQL 对账**：订单表 settle_amount=0 vs 业绩表 settle_amount=771125（确认 P0-001）。
- **报告路径**：`harness/reports/dashboard-money-audit-001-20260604-131908.md`、`harness/reports/evidence-20260604-131908-dashboard-money-audit-001.md`、`harness/reports/retro-20260604-131908-dashboard-money-audit-001.md`。
- **状态更新**：KNOWN_ISSUES.md 新增 3 条 P0 问题卡片；p0-p1-register.md 新增 RISK-009/010/011/012；QUALITY_LEDGER.md 分析模块 C → D、业绩域 B- → C。
- **状态**：`DONE`（docs-only / 只读审查）。
- **下一步**：
  - DASHBOARD-MONEY-FIX-001（P0 修复：settle_amount 回退 + 毛利隐藏 + talentCommission）。
  - DASHBOARD-MONEY-FIX-002（旧版接口治理：废弃 or 修复）。
  - DASHBOARD-MONEY-TEST-001（测试补齐：双轨隔离 + settle=0 + 对账）。

## 订单明细表复刻与前后端字段对齐（2026-06-04 18:15）

- **任务**：在数据平台/订单页面新增"订单明细"Tab，展示逐笔订单的完整 16 列明细信息（含双轨金额）。
- **范围**：full（后端 + 前端 + 测试 + 容器）。
- **新增文件**：
  - `backend/.../vo/data/OrderDetailVO.java`（16 列订单明细 VO）
  - `frontend/src/views/data/OrderDetailTab.vue`（明细 Tab 子组件，16 列 + 分页 + 自定义表头 + 导出按钮）
  - `harness/reports/evidence-20260604-181500-order-detail-tab.md`（执行证据）
- **后端变更**：
  - `PerformanceRecordMapper` 新增 `findByOrderIds` 批量查询 + 空列表守卫
  - `DataApplicationService` 新增 `getOrderDetailPage` + `exportOrderDetail` + 辅助方法（loadPerformanceMap / loadActivityNameMap / loadUserNameMap / toOrderDetailVO / safeCentToYuan / safeAdd / safeSubtract）
  - `DataController` 新增 `/data/orders/detail` + `/orders/exports/detail` 端点
  - `DataControllerTest` 新增 5 个测试（40/40 PASS）
  - `PerformanceRecordMapperTest` 新增 3 个集成测试
- **前端变更**：
  - `api/data.ts` 新增 `getOrderDetailPage` + `exportOrderDetail`
  - `order-list-query.ts` 新增 `buildOrderDetailPageParams`
  - `OrderList.vue` 新增 Tab 切换器（汇总 / 订单明细）+ 条件渲染 + Tab 感知导出调度
  - `OrderList.test.ts` 新增 4 个测试（8/8 PASS）
- **构建验证**：mvn compile/test/package PASS, vitest 8/8 PASS, vite build PASS, docker real-pre rebuild+restart 4 容器 healthy
- **关键设计**：批量关联避免 N+1；活动名称用 `selectNamesByActivityIds`（纯 MyBatis）；双轨 null → "-"；服务费双轨分别算
- **状态**：`PARTIAL`（代码完成 + 构建通过 + 容器重启 healthy；smoke 受沙箱网络限制未验证，需浏览器打开 `http://localhost:3001/data/orders` 验证 Tab 切换和明细表渲染）。
- **未做**：浏览器 smoke test、E2E 测试、远端部署。

## 订单明细表字段对齐 real-pre 验证收口（2026-06-04 19:17）

- **任务**：补齐订单明细表复刻任务的前后端字段对齐、筛选导出、real-pre 运行态和页面 smoke 证据。
- **代码提交**：`abf3f9eb fix: align order detail table fields`，已推送 `feature/auth-system` 上游。
- **后端收口**：
  - `/api/data/orders/detail` 与 `/api/orders/exports/detail` 支持订单级明细 16 列口径。
  - 新增兼容筛选：`activityName`、`partnerId`、`partnerName`、`recruiterName`；旧 `merchantId/shopName/colonelName` 保留兼容。
  - 订单明细导出表头调整为：订单ID、活动信息、商品信息、合作方信息、推广者、渠道、招商、订单状态、订单额、服务费收入、技术服务费、服务费支出、服务费收益、招商提成、渠道提成、订单时间。
  - 服务费收益按 `服务费收入 - 技术服务费` 展示口径计算；服务费支出按 `招商提成 + 渠道提成` 计算；未结算 effective/settle 字段不回退到 estimate/pay。
- **前端收口**：
  - `OrderDetailTab.vue` 保留自定义表头和导出按钮，订单明细表 `scroll-x=2800`，左侧固定订单ID，商品信息使用图片 + 商品名 + 商品ID。
  - `OrderList.vue` 保留汇总模块，新增订单明细 Tab；汇总默认不再展示 V1 不做的毛利。
  - “媒介”文案已从前端活跃源码、后端活跃源码和测试断言中移除，统一为“渠道”。
- **验证**：
  - 后端 targeted：`mvn -f .\backend\pom.xml "-Dtest=DataControllerTest,OrderControllerTest,OrderServiceTest,PerformanceRecordMapperTest" test`，99 tests PASS。
  - 前端 targeted：`npm run test -- OrderDetailTab.test.ts OrderList.test.ts order-list-query.test.ts orders/index.test.ts`，43 tests PASS。
  - 后端 package：`mvn -f .\backend\pom.xml -DskipTests package`，BUILD SUCCESS。
  - 前端 build：`npm run build`，PASS；存在既有 Vite chunk size warning。
  - Harness：`agent-do.ps1 -Env real-pre -Scope full`，后端/前端 build PASS，backend-real-pre 与 frontend-real-pre rebuild/recreate 后 healthy，`/api/system/health=UP`，`/healthz=ok`，`e2e:real-pre:p0:preflight` PASS，evidence `harness/reports/evidence-20260604-191102.md`。
  - 页面 smoke：Playwright 登录 admin 后访问 `http://127.0.0.1:3001/data/orders` 并切换“订单明细”，`/api/data/orders/detail` 返回 200、total=738、首屏 records=20；16 个目标列均可见；商品信息可见；页面不含“媒介”和“毛利”；导出按钮和自定义表头存在；未结算样本 `6953418877360936032` 显示 `结算：-`。结果：`runtime/qa/out/order-detail-page-smoke-20260604-191711/result.json`，截图：`runtime/qa/out/order-detail-page-smoke-20260604-191711/order-detail-table.png`。
- **非阻塞噪声**：页面 smoke 记录到 Google Fonts 被 CSP 拦截，归类为外部字体噪声；关键业务接口无失败请求。
- **状态**：`DONE_CLEAN`（本地 real-pre 已验证，远端部署未请求）。
