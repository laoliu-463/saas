# Harness Changelog 3

Source: ../harness-changelog-full.md (split archive index)

## v0.5.2

- 完成 P0-ORDER-001 真实订单同步与渠道可见修复（2026-06-03 17:30–18:04）。
- 生成报告：`harness/reports/p0-order-001-real-order-visible-20260603-180450.md`、`harness/reports/p0-order-001-diagnosis-20260603-173500.md`、`harness/reports/p0-order-001-intake-20260603-172923.md`。
- 修改后端代码：
  - `backend/src/main/java/com/colonel/saas/job/JobLockKeys.java`：新增 `ORDER_SYNC_PAY_RECENT` 锁常量。
  - `backend/src/main/java/com/colonel/saas/job/OrderSyncJob.java`：新增 `syncPayRecent()` `@Scheduled`（cron `0 */30 * * * ?`） + 独立 `payRecentEnabled` 开关 + 增强 mode/inserted/updated/attributed/unattributed/failed 日志。
  - `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`：新增 `syncPayRecentWindow()` 6h 窗口 + 独立 Redis key `order:sync:pay_recent_last_time` + 独立锁；`syncRange` → `syncRangeWithMode` 透传 mode；`syncItems` 增加 `noPickSourceCount` / `noMappingCount` 计数；日志格式升级 `mode/timeType/range/pages/fetched/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed` 全字段。
  - `backend/src/main/resources/application.yml`：新增 `order.sync.cron` + `order.sync.pay-recent.{enabled,cron}` 默认值。
- 新增/增强测试 13 用例：
  - 新建 `backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java`（6 用例，验证 PAY_RECENT 独立锁/key/6h 窗口/双轨水位隔离）。
  - 增强 `backend/src/test/java/com/colonel/saas/job/OrderSyncJobTest.java` +4 用例（syncPayRecent 调用/disabled/locked/exception）。
  - 增强 `backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java` +3 用例（admin ALL 不加业务过滤、channel PERSONAL 按 userId 过滤、admin /orders/unattributed 强制 UNATTRIBUTED）。
- 验证：
  - `mvn test`：**1688 tests, 0 failures, 0 errors**, total 6:53。
  - `mvn -DskipTests package`：BUILD SUCCESS, repackage 13.8s。
  - `safety-check.ps1 -Env real-pre -Scope backend -DryRun`：PASS。
  - `restart-compose.ps1 -Env real-pre -Scope backend`：image rebuilt, container Recreated, 4/4 healthy。
  - Backend health：`{"status":"UP"}`。
  - 启动日志：Started in 30.458s, 无异常。
  - 双轨 Scheduled 立即首次执行：INCREMENTAL 窗口 628s ≈ 10min+overlap，PAY_RECENT 窗口 21600s = 6h；scheduler-2/scheduler-4 独立线程。
  - Redis 双 key 独立：`order:sync:last_time=1780480740` + `order:sync:pay_recent_last_time=1780480740` 共存。
- 状态文件更新：
  - `harness/CURRENT_STATE.md`：追加 P0-ORDER-001 完成段。
  - `harness/state/DOMAIN_STATUS.md`：订单域条目改写，含 PAY_RECENT 修复、运行态对账、下一步任务。
  - `harness/state/KNOWN_ISSUES.md`：新增"真实付款订单 10 分钟 update 窗口可能丢单"为 fixed-code,blocked-by-sample。
- 未修改寄样 / 达人 / 商品 / 业绩 / 独家 / 前端 / 数据库 schema / docker-compose / env / `application-real-pre.yml`。
- 未 `git add .`、未 `down -v`、未清库、未部署远端。
- Final Status：`PARTIAL_DIRTY_REMAINING`——代码 / 测试 / 构建 / 容器 / 日志 / 双轨调度全部 PASS；真实订单端到端业务验证 BLOCKED_BY_SAMPLE（需等待商务侧真实付款样本）；本任务未执行 git commit/push，dirty 已分类（6 modified 本任务 backend + 4 untracked 本任务 reports/test + 3 untracked 与本任务无关），留待下个会话单独批次提交。

## v0.5.1

- 完成 GIT-BATCH-4-REPORTS 报告批次提交（2026-06-03）。
- 生成报告：`harness/reports/git-batch-4-reports-20260603-151500.md`。
- commit：`7c69986e docs(harness): sync remaining task reports`。
- 24 文件全部为 `harness/reports/*.md`，覆盖：B3-SCOPE-001 / B3-VERIFY-001 / TEST-1（主报告+evidence+retro）/ U-2.5-B（主报告+evidence）/ FUNC-001（主报告+evidence+retro）/ P-FIX-001C（早期+最终主报告+evidence+retro）/ P-FIX-002（evidence）/ P-FIX-002D（retro）/ GIT-BATCH-2 / GIT-BATCH-3 / content-retire × 3 / 本批次报告。
- 验证：typecheck N/A、staged check N/A、`safety-check -Scope docs -DryRun` PASS、`verify-local -Scope docs` PASS、`git diff --check` PASS（仅 CRLF 警告）、`git diff --cached --check` PASS。
- 推送：`gitee` + `origin` 同步推送 `ba7f1996..7c69986e`。
- 未修改后端 / SQL / Docker / 业务代码 / 数据库。
- 未执行数据库操作。
- 未重启容器。
- 未部署远端。
- 状态 `DONE`。
- 残留 dirty：1（`backend/src/main/resources/application-real-pre.yml` P-FIX-002A 配置残留，`previous_partial` 已登记到 P-FIX-002-CONFIG-RESIDUAL 任务）。

## v0.5.0

- 完成 GIT-HARNESS-001 Git 工作区治理与批次提交门禁强化（2026-06-03）。
- 生成报告：`harness/reports/git-harness-001-worktree-governance-20260603-*.md`。
- 新增 `harness/skills/git-change-control.md`：定义 Git Intake Gate、Allowed Change Set、Dirty Classification、Staged Scope Gate、Commit Gate、Push Gate、Deploy Commit Gate、Git Exit Gate、Unknown Dirty Policy、Rollback Policy 十项强约束。
- 新增 `harness/skills/git-batch-submit.md`：定义批次划分原则、提交步骤、文件归属分类、9 项审查规则、commit message 规范、Gitee/origin 推送规则和部署前 commit 对齐规则。
- 新增 `harness/skills/post-task-gc.md`：定义任务后清理流程，含临时文件清理、报告提交、状态文件检查、Dirty 归属登记和未提交项进入下一任务队列的流程。
- 修改 `harness/AGENT_CONTRACT.md`：新增"Git 工作区治理强约束"章节，要求所有 Agent 任务按 `git-change-control.md` 执行 12 条强制 Gate。
- 修改 `harness/TASK_ROUTING.md`：新增"Git 任务路由"章节，定义 `GIT-INTAKE` / `GIT-SCOPE` / `GIT-BATCH` / `GIT-CLEANUP` / `GIT-DEPLOY-GATE` / `GIT-EXIT` 六个子任务路由。
- 修改 `harness/FORBIDDEN_SCOPE.md`：新增"Git 工作区治理禁止事项"章节，列出 18 条 Git 禁止行为（`git add .`、混合提交、dirty 部署、PARTIAL 写成 DONE 等）。
- 修改 `harness/COMPLETION_GATES.md`：新增"Git Gate（G0-G4 内部子门禁）"章节，定义 Gate G0（Docs-only clean）、Gate G1（Frontend clean）、Gate G2（Backend clean）、Gate G3（Deploy clean）、Gate G4（Session clean）五个 Git 子门禁。
- 修改 `harness/SESSION_EXIT_GATE.md`：新增"Git 状态 Clean（Git Exit Gate 强约束）"作为 Session Exit Gate 第六项硬门禁；退出检查模板新增"Git State Clean"行；新增 5 条 Git 禁止事项（11-15）。
- 修改 `harness/state/KNOWN_ISSUES.md`：记录"Git 工作区 dirty 膨胀与批次提交门禁缺失"为 fixed，状态"通过 GIT-HARNESS-001 治理"。
- 修改 `harness/state/DECISIONS.md`：新增 2026-06-03 Git 工作区治理决策摘要（6 条决策）；新增决策索引条目。
- 核心约束：所有任务必须按 Git Intake Gate → Allowed Change Set → Staged Scope Gate → Commit Gate → Push Gate → Deploy Commit Gate → Git Exit Gate 顺序执行；任务终态只能为 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。
- 未修改后端 / 前端 / SQL / Docker / env。
- 未执行数据库操作。
- 未重启容器。
- 未部署远端。

## v0.4.11

- 完成 GIT-BATCH-2 frontend-product-ui 提交与远端 frontend 部署（2026-06-03）。
- 生成报告：`harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md`。
- commit：`5fe6ba23 feat(product-ui): product card hover expand and library load-more pagination`。
- 5 文件变更：`ProductSelectionCard.vue`、`ProductSelectionCard.test.ts`、`ProductLibrary.vue`、`ProductLibrary.test.ts`（新建 188 行）、`tests/e2e/03b-product-library-drawer-fields.spec.ts`。
- 验证：typecheck PASS、ProductLibrary + ProductSelectionCard vitest 18 tests / 3 files PASS、frontend build PASS（`vue-tsc -b && vite build`，1.58s，ProductLibrary bundle 37.86 kB / gzip 12.22 kB）、`git diff --check` PASS、frontend safety-check PASS。
- 推送：`gitee` + `origin` 同步推送 `49035d3c..5fe6ba23`。
- 远端部署：`docker compose up -d --build frontend-real-pre` → frontend-real-pre Recreated + Healthy；backend-real-pre 同时被 recreate 但 jar 来自同一 commit `5fe6ba23`（无 backend 变更，行为零差异），4 容器全部 healthy。
- 远端 healthz：backend `{"status":"UP"}` / frontend `ok`。
- 新 bundle `ProductLibrary-iepQIAKR.js` 已部署到容器，nginx 入口 200。
- 未修改后端 / SQL / 数据库 / `docker compose down -v` / 远端数据库写操作 / 商品同步逻辑 / 订单归因 / 业绩计算 / 寄样状态机。
- 残留 dirty：34 个文件（Batch 3 backend 14 + Batch 4 / 5 报告 20），与本批次正交。
- 状态 `DONE`。下一步：Batch 3 `backend-user-domain-u2_5-test1`（15 文件，commit + 部署 backend）。

## v0.4.10

- 完成 SYNC-PLAN-001 本地未推送内容分批同步与部署计划（2026-06-03）。
- 生成报告：`harness/reports/sync-plan-001-batch-sync-deploy-plan-20260603-143000.md`。
- 共清点 110 个 dirty / untracked 文件，分为 5 个批次：
  - Batch 1：harness-docs（19 文件，Harness 规则 + 状态 + 新门禁系统）
  - Batch 2：frontend-product-ui（5 文件，FUNC-001 + P-FIX-001C）
  - Batch 3：backend-user-domain-u2_5-test1（15 文件，U-2.5-B + TEST-1）
  - Batch 4：p-fix-002d-remote-report（15 文件，任务报告）
  - Batch 5：cleanup-retire（78 文件，历史报告归档 + .gitignore）
- 推荐执行顺序：Batch 4 → Batch 1 → Batch 5 → Batch 2 → Batch 3。
- 审查确认 `.gitignore` 变更安全（仅添加 `/nul` 忽略 Windows 设备文件）。
- 审查确认 `application-real-pre.yml` 变更为 P-FIX-002A 同步配置残留，随 Batch 3 一起提交。
- 未提交业务代码、未执行数据库操作、未部署远端。状态 `DONE_PLAN_GENERATED`。
- 下一步：用户确认后按计划执行 Batch 4（任务报告）开始分批提交。

## v0.4.9

- 完成 P-FIX-002D-REMOTE 远端部署对齐商品同步修复并验证 5 分钟同步任务（2026-06-03）。
- 生成报告：`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md`。
- 远端 commit 对齐 `dea06e4c`（通过 Gitee 推送后拉取，远端服务器从 Gitee 拉取而非 GitHub）。
- 远端 env 补齐：追加 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` 到 `/opt/saas/env/.env.real-pre`。
- 远端 Docker Maven 构建（77MB jar, Jun 3 05:17 UTC）+ 容器重启，4 个均 healthy。
- 同步配置生效（`enabled=true, cron=0 */5 * * * ?, batchSize=20`）。
- 两个 5 分钟周期正常执行：ok=5+ok=0, fail=0，零唯一索引冲突。
- 远端对账：3846 快照 / 604 DISPLAYING（从 420 增长 +184）/ 1114 HIDDEN / 2128 PENDING / 无重复。
- API total=604 与 SQL DISPLAYING=604 完全一致。
- 未执行手工数据库写操作；未清库。状态 `DONE_REMOTE_VERIFIED`。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`、`state/KNOWN_ISSUES.md`。

## v0.4.8

- 完成 P-FIX-002D 本地 real-pre 运行态验证（2026-06-03）。
- 生成报告：`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`。
- 重启 backend-real-pre 容器，新 jar 已加载（Jun 3 04:15 UTC），同步配置生效（`enabled=true, cron=0 */5 * * * ?`）。
- 两个 5 分钟周期正常执行：ok=3+ok=0, fail=0，零唯一索引冲突。
- 同步后本地对账：7323 快照 / 2377 DISPLAYING / 4575 HIDDEN / 371 PENDING / 无重复。
- API total=2377 与 SQL DISPLAYING=2377 完全一致。
- 未执行手工数据库写操作；未部署远端。状态 `DONE_RUNTIME_VERIFIED`。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`、`state/KNOWN_ISSUES.md`。

## v0.4.7

- 完成 P-FIX-002 商品库数量不足修复的代码与配置准备（2026-06-03），包含 A/B/C/D 四阶段；运行态仍待重启 / 部署验证。
- 生成报告：`harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`。
- P-FIX-002B 核心修复：`ProductDisplayRuleService.applyNormalDisplayDedup` 从单遍处理改为三阶段持久化（先降级旧 DISPLAYING→HIDDEN，再处理其他非 DISPLAYING，最后升级新 winner→DISPLAYING），避免 `uk_pos_one_displaying_per_product` partial unique index 冲突。
- 修改 `ProductDisplayRuleService.java`：新增 typed `DisplayDecision record`；`applyNormalDisplayDedup` 改为计算决策 + 三阶段持久化；未删除唯一索引，未改变 winner 选择规则。
- 修改 `ProductDisplayRuleServiceTest.java`：新增/补齐 4 个相关测试（严格调用顺序、切换顺序、winner 已 DISPLAYING 的幂等性、多候选唯一 DISPLAYING）。
- P-FIX-002C 只读对账：本地 7284 快照 / 1963 展示中 / 无重复 DISPLAYING / 716 推广中但未展示；商品库 API total=1963 与 SQL DISPLAYING 一致。
- P-FIX-002D 确认：远端参数已在 P-FIX-002A 中完成配置准备。
- 验证通过：`ProductDisplayRuleServiceTest` 31 tests / 0 failures、商品相关定向测试 49 tests / 0 failures、全量后端测试 1675 tests / 0 failures、Maven package BUILD SUCCESS、`git diff --check` PASS、`safety-check -Scope full -DryRun` PASS、docker compose config 正确。
- `safety-check -Scope code -DryRun` 因当前脚本 ValidateSet 仅支持 `backend/frontend/full/docs` 而失败，作为 Harness 口径缺口记录。
- 未执行数据库写操作；未重启容器；未部署远端。任务口径最终状态 `DONE_CONFIG_READY`；Completion Gate 口径 `PARTIAL`。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`、`state/KNOWN_ISSUES.md`。

## v0.4.6

- 完成 P-FIX-002A 商品活动同步任务启用与 5 分钟周期配置（2026-06-03）。
- 生成报告：`harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`。
- 修改 `ProductActivitySyncJob.java`：`@Scheduled` 默认 cron 从 `0 0 */2 * * ?`（每 2 小时）改为 `0 */5 * * * ?`（每 5 分钟）；新增 `@PostConstruct logStartupConfig()` 启动时记录 enabled/cron/batchSize/whitelist；新增 `cronExpression` `@Value` 字段；disabled 日志级别从 info 降为 debug。
- 修改 `application.yml`：默认 cron 从 `0 0 */2 * * ?` 改为 `0 */5 * * * ?`。
- 修改 `docker-compose.real-pre.yml`：`backend-real-pre` environment 块新增 `PRODUCT_ACTIVITY_SYNC_ENABLED: ${PRODUCT_ACTIVITY_SYNC_ENABLED:-true}` 和 `PRODUCT_ACTIVITY_SYNC_CRON`。
- 修改 `.env.real-pre.example`：新增 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?`。
- 修改 `harness/runbooks/remote-deploy.md`：新增部署前/后同步参数检查章节。
- 修改 `harness/commands/deploy-remote.ps1`：新增远端 env 同步参数检查和部署后日志验证。
- 验证通过：Maven package BUILD SUCCESS、`git diff --check` PASS、safety-check PASS、docker compose config 正确解析同步参数。
- 未执行数据库写操作；未重启容器；未部署远端。必须先完成 P-FIX-002B 修复唯一索引冲突再实际启用远端同步。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`、`state/KNOWN_ISSUES.md`。

