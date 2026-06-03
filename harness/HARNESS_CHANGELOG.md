# Harness Changelog

## v0.6.0

- HARNESS-DEBT-GOVERNANCE-ITERATION 完成（2026-06-03 23:03）。本轮为 harness 自身治理的 docs-only 任务。
- **新增文件**：
  - `harness/state/HARNESS_DEBT.md`（harness 自身工程债务登记；25 条 DEBT）。
  - `harness/runbooks/task-lifecycle.md`（任务生命周期七 Gate 模板：Intake / Scope / Implementation / Verification / Evidence / Git / Exit）。
  - `harness/runbooks/scope-command-matrix.md`（Scope → Command 决策表，取代分散在 TASK_ROUTING 和 doc/02-tools 的描述）。
  - `harness/runbooks/debt-governance.md`（债务防回流机制：Intake / Register / Exit / Dirty / Rotation / Prompt Upgrade）。
  - `harness/environment/CHEATSHEET.md`（环境速查表：本地端口 / 健康检查 URL / env 变量 / 远端信息 / 禁止命令）。
  - `harness/feedback/docs-only-template.md`（docs-only 任务最小化 evidence 模板）。
  - `harness/plans/HARNESS_ITERATION_ROADMAP.md`（Harness 自身迭代路线图；区别于业务 DDD 路线图）。
  - `harness/reports/harness-debt-governance-inventory-20260603-230334.md`（现状盘点）。
  - `harness/reports/harness-debt-governance-plan-20260603-230334.md`（治理计划与债务总表）。
  - `harness/reports/evidence-20260603-230334-harness-debt-governance.md`（执行证据）。
  - `harness/reports/retro-20260603-230334-harness-debt-governance.md`（本次复盘）。
- **修改文件**：
  - `harness/CURRENT_STATE.md`：顶部加目录指针。
  - `harness/QUALITY_LEDGER.md`：加与 DOMAIN_STATUS 分工指针；Harness 等级 B → A-。
  - `harness/README.md`：三目录并存说明（主源 = `harness/`，`harness/doc/` 仅做聚合）。
  - `harness/instructions/safety-rules.md`：主源指针 → `FORBIDDEN_SCOPE.md`。
  - `harness/feedback/garbage-collection-policy.md`：reports/*.md 纳入受保护范围。
  - `harness/state/KNOWN_ISSUES.md` / `p0-p1-register.md` / `known-risks.md`：三文件分工互引。
  - `harness/state/DEPLOYMENT_STATE.md` / `VALIDATION_STATE.md`：主源指针。
  - `harness/state/TASK_HISTORY.md`：补 14 行 2026-06-02 / 2026-06-03 任务摘要。
  - `harness/doc/00-HARNESS-README.md`：主源说明。
- **DEBT 状态**：
  - 关闭：DEBT-001/002/003/004/005/006/007/008/009/010/011/012/015/017/018/019/023/025（共 18 条 in-progress → fixed；DEBT-023/025 是新建登记文件）。
  - 保持 deferred：DEBT-013/014/016/020/021/022/024（共 7 条，明确登记为后续任务）。
- **未做**：
  - 未修改任何业务代码。
  - 未修改 backend / frontend / SQL / Docker / env。
  - 未重启容器 / 未部署远端。
  - 未删除任何报告 / 临时 log / 已存在的 evidence。

## v0.5.7

- 完成 GIT-BATCH-C 业务代码批次提交 + 推送 + 远端部署（2026-06-03 22:50–23:00）。
- 生成报告：`harness/reports/git-batch-c-talent-address-deploy-20260603-225500.md`。
- 提交内容：3 个 commit 全部 push 到 gitee + origin + 远端 deploy。
  - `804f96dc` 上游 `feat(sample): implement talent address default save (TALENT-ADDRESS-SAMPLE-DEFAULT)`（7 文件 / 476 行 +）
  - `16b23416` 上游 `docs(harness): TALENT-ADDRESS-SAMPLE-DEFAULT evidence report and state updates`（4 文件 / 130+ 行）
  - `159fa38d` 本会话 `docs(harness): GIT-INTAKE-001 dirty classify + ORDER-ATTRIBUTION-SAMPLE report`（3 文件 / 660 行 +）
- 推送：gitee + origin 同步推送 `49aefbda..159fa38d`（3 commits ahead）。
- 远端部署：`harness/commands/deploy-remote.ps1` 触发 `compose up -d --build backend-real-pre frontend-real-pre`，远端 backend 容器 Created/Started 2026-06-03T13:57:25.833735461Z，新 jar（Jun 3 13:57 UTC）已加载 `TalentClaimMapper` 注入 + `writeBackClaimAddress` 编译产物。
- 健康检查：远端 backend `{"status":"UP"}` + frontend `ok` + 4 容器全部 healthy。
- 业务验证：字符串验证确认 `ProductQuickSampleService` 已注入 `TalentClaimMapper` + `Lcom/colonel/saas/entity/TalentClaim;` 已链接；完整端到端业务验证在本地 real-pre 已有 evidence（`talent-address-sample-default-20260603-224000.md` H1-H9 PASS）。
- 远端 HEAD 对齐：本地 = gitee/feature/auth-system = origin/feature/auth-system = 远端 `/opt/saas/app` HEAD = `159fa38d`。
- 未清库、未使用 `docker compose down -v`、未执行破坏性 SQL。
- Final Status：`DONE_REMOTE_VERIFIED`。Git Exit Gate：`DONE_CLEAN`（本地工作区 clean + 远端容器 healthy + 远端 commit 对齐）。
- 状态：Batch A/B/C 全部完成。下一步：等 RISK-007 商务侧真实抖店订单样本解锁 / 1-2 小时后 P-VERIFY-002 远端商品库数量复核。

## v0.5.5

- 完成 TALENT-ADDRESS-SAMPLE-DEFAULT 达人寄样地址默认保存（2026-06-03）。
- 生成报告：`harness/reports/talent-address-sample-default-20260603-224000.md`。
- 后端修复：`ProductQuickSampleService` 和 `SampleApplicationService` 添加 `writeBackClaimAddress()` 方法，寄样创建成功后自动回写地址到 `talent_claim`。
- 前端修复：`QuickSampleModal.vue` 添加 `watch(talentIds)` 自动加载默认地址；`SampleCreateModal.vue` 添加 `loadDefaultAddress()` 和地址字段传递。
- 无新增字段或 migration，复用 `talent_claim` 已有 `recipient_name/recipient_phone/recipient_address` 字段。
- 测试结果：后端 1708/0/0、前端 QuickSampleModal 5/5、SampleCreateModal 3/3、typecheck ✅、build ✅。
- real-pre 验收：地址回写 PASS、快照不变 PASS、地址更新 PASS、多渠道隔离 PASS（biz_leader 访问 403）。
- 已部署远端 real-pre（2026-06-03 23:00 SSH deploy），远端健康检查 PASS、容器全 healthy、JAR 新构建。
- Final Status：`PASS`。

## v0.5.6

- 完成 GIT-INTAKE-001 会话启动 dirty 校验与 Batch 重分类（2026-06-03 22:30–22:50）。
- 生成报告：`harness/reports/git-intake-001-dirty-classify-20260603-225000.md`。
- 关键发现：会话启动 hook 报告 HEAD = `ab03d729` 与实际 HEAD = `49aefbda docs(harness): record sample remote verification` 偏差 1 个 commit；启动 hook 报告的 6 个 staged 文档（`harness/CURRENT_STATE.md` / `HARNESS_CHANGELOG.md` / `p0-sample-001-remote-verify-20260603-221004.md` / `retro-20260603-223153.md` / `p0-p1-register.md` / `real-pre-evidence-index.md`）已由上游会话 commit 并推送到 gitee + origin，本会话无需再次 commit/push。
- 远端 HEAD 对齐：本地 = gitee/feature/auth-system = origin/feature/auth-system = `49aefbda`。
- Batch 重分类：
  - Batch A（6 文档）已 clean——`49aefbda` 已包含并推送。
  - Batch B（14 文件，dirty）保持脏状态：6 modified 业务代码（`ProductQuickSampleService` / `SampleApplicationService` / `QuickSampleApplyTest` / `QuickSampleModal.test.ts` / `QuickSampleModal.vue` / `SampleCreateModal.vue`）+ 1 untracked 测试（`SampleCreateModal.test.ts`）+ 3 untracked 报告（`talent-address-sample-default-20260603-224000.md` / `git-intake-001-dirty-classify-20260603-225000.md` / `order-attribution-sample-20260603-222120.md`）+ 3 modified 状态文件（`HARNESS_CHANGELOG.md` / `state/DOMAIN_STATUS.md` / `state/KNOWN_ISSUES.md` / `state/p0-p1-register.md`）。
- 上游会话已先于本会话完成 TALENT-ADDRESS-SAMPLE-DEFAULT 任务（`v0.5.5`），含 6 modified 业务代码 + 1 untracked evidence 报告 + 2 modified 状态文件（`DOMAIN_STATUS.md` / `KNOWN_ISSUES.md`），但未 commit / 未 push / 未部署远端。
- 用户授权："等代码批次一起部署"——Batch B 必须包含 TALENT-ADDRESS-SAMPLE-DEFAULT 任务 + GIT-INTAKE-001 报告 + 订单归因样本报告，一并 commit + 推送 + 远端部署对齐。
- 风险登记：`harness/state/p0-p1-register.md` 追加 RISK-007（订单归因样本不足 BLOCKED_BY_SAMPLE）与 RISK-008（Batch B 14 文件 dirty 未提交）。
- 远端部署：本次未触发（仅 docs 状态收口，无代码变更）。
- 未修改业务代码、未写库、未重启容器、未执行数据库写入 SQL。
- 状态：Batch A `DONE_CLEAN`，Batch B `PARTIAL_DIRTY_REMAINING`（Git Exit Gate：`DONE_WITH_REGISTERED_DIRTY`）。

## v0.5.4

- 完成 P0-SAMPLE-001-REMOTE-VERIFY 远端 real-pre 部署验证状态收口（2026-06-03 22:10-22:30）。
- 生成报告：`harness/reports/p0-sample-001-remote-verify-20260603-221004.md`、`harness/reports/retro-20260603-223153.md`。
- 远端 `/opt/saas/app` 从 `77b723b` 快进到 `ab03d72`（目标 `ab03d729`），远端工作区 clean。
- 部署方式：远端用 Maven Docker 镜像构建后端 jar，仅 rebuild/recreate `backend-real-pre` 与 `frontend-real-pre`；未使用 `-v`，未清库，未重建 PostgreSQL / Redis volume，未执行数据库写入 SQL。
- 健康检查：远端 backend/frontend/postgres/redis 均 healthy；`/api/system/health={"status":"UP"}`，`/healthz=ok`。
- 业务验证：商品库快照 `81a5d39b-e661-3b19-96d3-e55b145f15f1` 快速寄样成功生成 `sample_request=9c655738-76b1-4fd0-9676-8f307c694f3f`，`request_no=QS2026060337AF9AAB`，`status=1 / PENDING_AUDIT / 待审核`；`product` 主表已物化；`crawler_talent_info` 缺失时按 `talent.douyin_uid=56723079343` 兜底。
- 审核列表验证：`admin` 可见，`biz_leader` 可见，`biz_staff` 不可见；失败分支返回 `items[].message` 明细（“该达人未在你的私海中...” / “达人不存在”）。
- 限制：远端 `channel_staff` 无私海达人，唯一有私海达人的等价渠道账号“玄同”无法用已知测试口径登录；远端全部 `product_operation_state.assignee_id` 为空，无法严格证明“该商品分配给 biz_leader”前提。
- 额外风险：`channel_staff` 可查到该待审核单，建议另起 RBAC 专项复核，不在本次远端部署验证中修改。
- Final Status：`PARTIAL`。核心修复链路远端通过；指定账号/分配前置数据不足导致完成标准不能写成全量 PASS。

## v0.5.3

- 完成 ORDER-P0-DUAL-SOURCE-SYNC 本地 real-pre 修复与验证（2026-06-03 19:xx-20:23）。
- 生成报告：`harness/reports/evidence-20260603-202253.md`、`harness/reports/evidence-20260603-202320.md`、`harness/reports/retro-20260603-202309.md`、`harness/reports/retro-20260603-202500.md`。
- 修改后端代码：
  - `OrderSyncJob`：新增 `syncInstituteOrdersRecent()` 定时入口，默认每 10 分钟调用 6468。
  - `OrderSyncService`：新增 `syncInstituteOrdersRecentWindow()`，使用独立锁 `ORDER_SYNC_INSTITUTE`、独立 Redis key `order:sync:institute_recent_last_time`、24h 窗口和 `ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel` 日志。
  - `OrderDualTrackAmountResolver` / `OrderSyncPersistenceService`：按来源保护双轨，6468 只写事实/预估轨，2704 保留结算/有效轨，按 `order_id` 幂等合并。
  - `ColonelsettlementOrder` / Mapper：映射 `pay_time`、`order_create_time`，并新增非持久化 `syncSource`。
- 验证：
  - 聚焦测试：37 tests, 0 failures。
  - API/网关/订单测试：43 tests, 0 failures。
  - `mvn -f backend/pom.xml -DskipTests package`：PASS。
  - 本地 backend-real-pre 重启 + health：PASS。
  - real-pre preflight：PASS。
  - 运行态：`ORDER_SYNC_INSTITUTE ... fetched=100 inserted=100 updated=0`；`colonelsettlement_order count(*)=100`；100 条均有 `order_amount/pay_time/estimate_*`，`settle_amount/effective_*` 均未由 6468 写入。
  - 管理员订单列表和未归因列表均返回 `total=100`。
- 未修改 Dashboard summary；另开 `ANALYTICS-DUAL-TRACK-SUMMARY-FIX`。
- 未部署远端；远端验证待用户明确授权。
- 本轮上游样本 `pick_source=0`，渠道 mapping 命中正向验证仍为 PENDING。
- Final Status：`PARTIAL_DIRTY_REMAINING`——本地 real-pre 订单入库和管理员可见通过；远端部署/验证未执行；混合 dirty 工作区下未使用 `git add -A`。

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

## v0.4.5

- 完成 P-DIAG-002 商品库数量不足排查（2026-06-03）。
- 生成报告：`harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md`。
- 纯只读排查，未修改 Java/Vue/SQL 代码，未执行数据库写操作，未重启容器，未部署远端。
- 执行本地 real-pre 只读 SQL 对账（24 活动 / 7278 快照 / 2673 推广中 / 1958 展示中 / 684 PENDING）。
- 执行后端商品库接口排查（API total=1958 与 SQL 完全一致，P-FIX-001C pageSize=100 正确生效，@Max(100) 限制正常拦截）。
- 执行同步链路排查（`ProductActivitySyncJob` 默认每 2 小时，默认禁用；`refreshActivitySnapshots` @Transactional 包裹完整事务；`applyForActivityId` 展示去重失败导致事务回滚）。
- 执行远端服务器只读对账（远端 3601 快照 / 420 展示中 vs 本地 7278 / 1958，远端同步任务禁用、唯一索引冲突、过期活动商品卡 PENDING）。
- 确认 P-FIX-001C 前端分页改造已生效（PAGE_SIZE=100，loadMore 追加）。
- 三个并存根因：A) 远端同步任务禁用；B) 唯一索引冲突导致事务回滚；C) 过期活动商品卡 PENDING。
- 建议下一步：P-FIX-002A 同步链路修复、P-FIX-002B 展示规则重算、P-FIX-002D 远端部署对齐。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`、`state/KNOWN_ISSUES.md`，记录 P-DIAG-002 完成状态和下一步建议。

## v0.4.4

- 完成 P-FIX-001C 商品库分页弱化改造（2026-06-03）。
- 生成任务报告：`harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`；生成 evidence：`harness/reports/evidence-20260603-113632.md`；生成 retro：`harness/reports/retro-20260603-113645.md`。
- 修改前端商品库 `ProductLibrary.vue`：默认 `PAGE_SIZE` 调整为 100；新增 `currentPage`；加载更多按下一页追加；筛选和推广状态变更重置第一页；加载更多增加 loading 锁。
- 新增 `ProductLibrary.test.ts`，覆盖默认 `size=100`、加载更多 append、筛选重置。
- 验证通过：相关前端单测 4 files / 50 tests PASS、typecheck PASS、frontend build PASS、`git diff --check` PASS、frontend safety-check PASS、real-pre frontend restart PASS、verify-local PASS、页面 smoke PASS_WITH_NON_TASK_WARNING。
- 未修改后端或数据库；未部署远端。最终会话状态为 `PARTIAL`，原因是工作区存在任务前遗留 dirty / untracked 且未安全提交/推送。
- 暴露 Harness 差异：任务模板要求的 `frontend-domain-change.md`、`post-task-gc.md` 不存在；`safety-check -Scope code` 与当前脚本 ValidateSet 不一致，已记录到 `KNOWN_ISSUES.md`。

## v0.4.3

- 完成 FUNC-001 商品库卡片默认态与悬浮展开态改造（2026-06-03）。
- 生成任务报告：`harness/reports/func-001-product-card-hover-ui-20260603-111451.md`；生成 evidence：`harness/reports/evidence-20260603-111733.md`。
- 修改前端卡片 `ProductSelectionCard.vue`：修正默认态公开佣金兜底（投放期佣金为 `-` 时回退普通佣金率），调整 hover 详情字段顺序为招商、寄样、时间、团长、店铺、活动、库存、商家评分。
- 补充 `ProductSelectionCard.test.ts` 测试用例，验证佣金回退和 FUNC-001 字段顺序；更新 `tests/e2e/03b-product-library-drawer-fields.spec.ts`，支持 real-pre 通过 `E2E_SKIP_TEST_SEED=true` 跳过测试种子接口。
- 验证通过：前端构建（`vue-tsc -b && vite build`）PASS、ProductSelectionCard 12 tests PASS、相关前端单测 82 tests PASS、real-pre 商品库 hover E2E 2 tests PASS、`git diff --check` PASS、frontend safety-check PASS、verify-local PASS。
- 已执行本地 real-pre 前端 Scope 容器重启；Docker Compose 实际重建/重启了 frontend/backend，最终均 healthy。
- 未修改任何后端代码或数据库表结构；未部署远端。最终会话状态为 `PARTIAL`，原因是工作区存在任务前遗留 dirty 变更且未安全提交/推送。

## v0.4.2

- 完成 TEST-1 全量后端测试 failures 归因与最小修复（2026-06-03）。
- 生成报告：`harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`；生成 evidence：`harness/reports/evidence-20260603-104601.md`。
- 复现指定失败集合时实际为 53 tests / 3 failures / 7 errors；根因为测试夹具/断言未同步当前 `SysUserService.findPage` 4 参数调用、全局 `BusinessException` HTTP 语义，以及 MyBatis-Plus `LambdaQueryWrapper` 表元数据初始化。
- 最小修改 `SysUserServiceTest`、`SysUserControllerTest`、`CommissionRuleServiceTest`、`CommissionRuleControllerTest`，未修改 Java 业务代码、Vue 前端、数据库、Docker/Compose 或部署配置。
- 验证通过：4 个失败测试类单跑、失败集合组合测试、U-2.5-B 定向测试、全量 `mvn -f backend/pom.xml test`（1671 tests / 0 failures / 0 errors）、`mvn -f backend/pom.xml -DskipTests package`、`git diff --check`、Harness backend safety-check 和 verify-local。
- 未执行 real-pre 数据库写操作；未重启容器；未部署远端。U-2.5-B 运行态加载与 real-pre 历史 `dept_type` 写库修复仍需后续独立任务。

## v0.4.1

- 完成用户域 U-2.5-B dept_type 最小修复（2026-06-03）。
- 生成报告：`harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`。
- 统一 Java dept_type 标准为 `department/recruiter_group/channel_group/ops_group`，以 `DeptType.java` 为唯一标准；删除旧 `DeptTypes.java`，迁移 `service.SysDeptService` 调用点。
- 更新 `init-db.sql`、`alter-sys-dept.sql`、`alter-sys-dept-uuid-canonical-20260530.sql` 和 `migrate-sys-dept-dept-type.sql`，避免新环境继续写入 `recruiter/channel/dept` 作为 dept_type 标准值。
- 未新增独立 migration；未执行 real-pre 数据库写操作；未重启容器；未部署远端。real-pre 历史 `dept_type` 写库修复需单独 DB 任务执行。
- 下一步：用户域 U-3 CurrentUser / PermissionContext 统一。

## v0.4.0

- 新增 Session Exit Gate 会话退出门禁系统（2026-06-03）。
- 新增 `harness/SESSION_EXIT_GATE.md`：定义 Clean State 五项硬门禁（Build Clean、Test Clean、Progress Recorded、Artifacts Clean、Startup Path Clean）、最终状态规则（DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED）、退出检查模板和 10 条禁止事项。
- 新增 `harness/QUALITY_LEDGER.md`：初始化 9 个模块质量评分（用户域 C、配置域 B、商品域 C、达人域 C、寄样域 B-、订单域 C、业绩域 B-、分析模块 C、Harness B），定义评分标准和更新规则。
- 修改 `harness/AGENT_CONTRACT.md`：新增"Session Exit Gate"章节，DONE 必须同时满足 Completion Gate + Session Exit Gate。
- 修改 `harness/FORBIDDEN_SCOPE.md`：新增"禁止留下脏状态"章节，列出 10 条禁止行为。
- 修改 `harness/TASK_ROUTING.md`：新增"Session Exit Gate 路由"章节，所有任务结束后必须进入退出门禁。
- 修改 `harness/state/DOMAIN_STATUS.md`：新增"Session Exit 时的领域状态更新"规则。
- 核心约束：Agent 只有在"任务跑通 + 仓库干净 + 状态可交接"三者同时满足时，才允许说 DONE。

## v0.3.0

- 新增 Completion Gate 完成门禁系统（2026-06-03）。
- 新增 `harness/COMPLETION_GATES.md`：定义 Gate 0（Docs Only）、Gate 1（Backend Change）、Gate 2（Frontend Change）、Gate 3（Domain Change）、Gate 4（E2E Business Flow）五个完成门禁，包含统一最终输出模板和 9 条强制规则。
- 修改 `harness/AGENT_CONTRACT.md`：在 Definition of Done 前新增"Completion Gate：禁止提前完成"章节，要求 DONE 必须同时满足 10 项条件；Definition of Done 增加 Gate 选择和统一输出模板要求。
- 修改 `harness/FORBIDDEN_SCOPE.md`：新增"禁止提前完成 / 虚假完成"章节，列出 13 条禁止行为、6 种合法状态（DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED / RISK_ACCEPTED_BY_USER）和 5 种禁止模糊状态。
- 修改 `harness/TASK_ROUTING.md`：新增"Task -> Completion Gate 路由"章节，按任务关键词绑定默认 Gate；新增 Gate 选择升级规则。
- 修改 `harness/state/DOMAIN_STATUS.md`：新增"任务结束状态更新规则"章节，要求每次任务结束前必须更新相关领域状态。
- 核心约束：Agent 不得仅因代码修改、编译通过、单接口通过或单页面打开而声明 DONE；必须按 Gate 验证通过并有证据才能 DONE。

## v0.2.1

- 完成用户域 U-2.5-A dept_type 统一与最小修复方案设计（2026-06-03）。
- 生成报告：`harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`，覆盖常量类、migration / seed、real-pre 只读数据、代码调用点、group 数据范围影响、统一标准、最小修复顺序、migration 需求和 U-2.5-B 建议。
- 核心发现：`DeptType.java` 标准为 `department/recruiter_group/channel_group/ops_group`，`DeptTypes.java` 仍定义旧值 `recruiter/channel/dept`，且 `service.SysDeptService` 仍引用旧常量。
- real-pre 只读查询确认：当前有效 `sys_dept` 共 3 条，`dept_type` 全部为 `department`；`sys_user.dept_id` 当前无孤儿活跃用户；`sys_user.dept_id` 无 FK，`sys_role_menu` 仅有联合主键无 FK。
- 阶段性结论：先执行 U-2.5-B dept_type 最小修复，再进入 U-3；U-2.5-B 应统一到 `DeptType.java`、替换/废弃 `DeptTypes.java`、更新 seed 并新增幂等 migration，FK/CHECK 等 DB hardening 可后置。
- 未修改 Java 业务代码、Vue 前端代码或 SQL migration；未执行数据库写操作；未重启容器；未部署远端。

## v0.2.0

- 完成用户域 U-2 表结构与领域模型对齐任务（2026-06-03）。
- 生成报告：`harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`，覆盖 15 个章节：表清单、DDD 模型映射、一人多角色、数据范围、菜单权限、部门组别、Token/黑名单、操作日志、跨域访问风险、schema 问题清单、迁移建议、结论和下一步。
- 核心发现：7 张表字段与 Entity 完全对齐；一人多角色表结构真正支持（非代码临时拼凑）；P0 问题——`dept_type` 值和两个常量类（`DeptType.java` vs `DeptTypes.java`）严重冲突，三套 migration 脚本设置不同 dept_type 值。
- 识别 12 处跨域 Mapper 直接访问（DDD 越界），PerformanceAccessScope 含硬编码 SQL 子查询 sys_user。
- 建议 U-2.5 最小 migration：统一 dept_type 值、为 sys_user.dept_id 添加 FK 约束、为 sys_role_menu 添加 FK CASCADE。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`，记录 U-2 完成状态和 U-3 下一步。
- 未修改 Java 业务代码、Vue 前端代码或数据库；仅执行只读盘点和 Harness 状态更新。

## v0.1.9

- 完成用户域 U-1 现状盘点任务（2026-06-03）。
- 生成 U-1 报告：`harness/reports/user-domain-u1-inventory-20260603-090000.md`，覆盖认证链路、权限模型、数据范围模型、对外能力、跨域关系、DDD 越界风险、测试覆盖 13 个维度。
- 完成用户域 U-2 表结构与领域模型对齐（2026-06-03）。
- 生成 U-2 报告：`harness/reports/user-domain-u2-model-schema-alignment-20260603-093000.md`，覆盖 8 张表清单、12 个 DDD 模型映射、跨域访问风险、schema 问题分级。
- U-2 核心结论：用户域 schema 无需任何 migration；7 张表（sys_user/sys_role/sys_dept/sys_menu/sys_user_role/sys_role_menu/operation_log）完整对齐 DDD 模型；V1 阶段所有抽象均可纯代码层实现；P1 风险为跨域直接访问 Mapper 和 DEPT scope 不区分 dept_type。
- 更新 `CURRENT_STATE.md`、`state/DOMAIN_STATUS.md`，记录 U-1 + U-2 完成状态和 U-3 下一步。
- 未修改 Java 业务代码、Vue 前端代码或数据库；仅执行只读盘点和 Harness 状态更新。

## v0.1.8

- 增量合并 DDD 优化路线到现有 Harness，不重建 Harness，不覆盖既有入口。
- 新增 `harness/plans/DDD_OPTIMIZATION_ROADMAP.md` 和 `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`，记录领域优化顺序、阶段验收和任务矩阵。
- 新增八个领域 instruction，约束领域职责、不负责事项、V1 规则、禁止越界、测试和 state / feedback 同步。
- 新增 DDD 三个 skill，用于领域优化执行、边界检查和任务后同步。
- 扩展 `TASK_ROUTING.md`、`FORBIDDEN_SCOPE.md`、`AGENT_CONTRACT.md`、`CURRENT_STATE.md` 和 `state/DECISIONS.md`，把 DDD 路由、禁止范围、总规则和决策摘要接入现有主线。

## v0.1.7

- 修正 `CODEX.md` 默认入口为本地 `real-pre`，与 `AGENTS.md`、Harness 命令默认值保持一致。
- 将 `CONTEXT.md` 标题从 V2.2 改为 V1 术语上下文，明确旧 V2.2 仅作历史参考。
- 压缩 `AGENTS.md` 执行入口示例，保持入口文件短小，并把 Scope 细节交给 `harness/TASK_ROUTING.md`。
- 扩展 `TASK_ROUTING.md`，覆盖数据库变更、接口联调、第三方联调、Docker、部署、测试验收、Bug、性能、权限、数据问题和任务收尾。
- 新增环境索引、local dev 环境事实、状态索引和变更类 runbook，补齐 Harness 五子系统可发现性。
- 新增 `harness/feedback/garbage-collection-policy.md`，明确保留、归档、删除、合并和删除前检查规则。

## v0.1.6

- `deploy-remote.ps1` 在远端构建和重启后端前，先启动 `postgres-real-pre` 并执行活动商品依赖的幂等结构迁移 `V20260529_001__alter-colonel-activity-add-recruiter-fields.sql`。
- 新增远端活动商品 schema guard：校验 `colonel_activity` 已存在 `recruiter_user_id`、`recruiter_dept_id`、`assigned_at`、`assigned_by`、`activity_status_code`、`activity_status_text` 6 个字段，否则中止远端部署。
- 暂不将 `scripts/run-real-pre-db-migrations.sh` 的聚合 `migrate-all.sql` 接入每次 Harness 远端部署；该文件仍含历史非幂等 DML，重复执行存在数据漂移风险。

## v0.1.5

- 将 `agent-do.ps1`、`safety-check.ps1`、`restart-compose.ps1`、`verify-local.ps1`、`collect-evidence.ps1` 和 `new-retro.ps1` 的默认环境切换为本地 `real-pre`；`test` 仅作为显式专项环境。
- 调整 `agent-do.ps1` 顺序为安全检查 -> 构建 -> Compose 重建 -> 健康检查 -> 业务验证，避免业务验证失败时跳过重启和健康证据。
- `agent-do.ps1` 成功路径按实际验证状态写入 evidence conclusion：docs / 跳过业务验证 / 待远端部署为 `PARTIAL`，本地完整验证通过为 `PASS`。
- 修复 `Get-HarnessChangedFiles` 和 `collect-evidence.ps1` 对 `git status` 首行前导空格的处理，避免首个 modified 文件名被截断。
- `deploy-remote.ps1` 在远端 `git pull --ff-only` 后通过 `maven:3.9.10-eclipse-temurin-17` Docker 镜像执行 `mvn -f backend/pom.xml -DskipTests package`，适配后端 Dockerfile 需要预构建 `backend/target/*.jar` 且服务器未安装 Maven 的场景。
- 更新 AGENTS、Task Routing、Tools、Runbook 和 Harness 文档中的默认入口示例，明确远端部署仍必须显式传 `-DeployRemote true`。

## v0.1.4

- 修复 `git-push-safe.ps1` 对非 ASCII 文件名的兼容性：`Get-ChangedFiles` 和 `git diff --cached --name-only` 改用 `git -c core.quotepath=false` 输出原始 UTF-8 路径，避免 octal 转义导致 `Test-Path` 报错。
- `Assert-NoPlainSecrets` 对 `Test-Path` 增加 try-catch 容错，跳过无法解析的路径而非中断流程。
- `verify-local.ps1` 后端健康检查从单次尝试改为重试机制（最多 12 次，间隔 10 秒，总计最长 120 秒），适配 Spring Boot 容器启动延迟。
- 新增已知风险：test 环境 E2E auth setup 可能因后端容器初始化未完成而超时。

## v0.1.3

- 新增 `harness/commands/retire-content.ps1`，提供旧内容维护计划、manifest 驱动归档和 manifest 驱动删除能力。
- `agent-do.ps1` 默认在任务后执行 `ContentMaintenance=plan`，生成旧内容候选报告；归档和删除必须显式传 manifest。
- `collect-evidence.ps1` 新增 Content Maintenance Result 字段，用于记录旧内容维护结果。
- 新增旧内容生命周期规则，明确 keep / update / archive / delete 的判断口径和受保护路径。

## v0.1.2

- 新增 `harness/doc/` Harness Engineering 聚合文档入口，按 Instructions、Tools、Environment、State、Feedback 五个子模型组织。
- 将旧文档冲突、real-pre 安全边界、当前项目状态、业务闭环验证标准和任务证据模板集中到 `harness/doc/`，供后续 Agent 快速读取。
- 本次仅做文档层重构；原 `docs/` 和既有 `harness/` 仍作为事实主源与执行入口，不删除旧文档。

## v0.1.0

- 初始化 Instructions / Tools / Environment / State / Feedback 五个子系统。
- 建立 `AGENTS.md` 强制执行协议、`harness/AGENT_CONTRACT.md`、`CURRENT_STATE.md`、`TASK_ROUTING.md`、`FORBIDDEN_SCOPE.md` 和 `DOMAIN_MAP.md`。
- 新增 PowerShell 固定命令入口：`agent-do.ps1`、`safety-check.ps1`、`restart-compose.ps1`、`verify-local.ps1`、`collect-evidence.ps1`、`git-push-safe.ps1`、`deploy-remote.ps1`。
- 新增 skills、evals、runbooks、prompts 和 reports 输出目录。

## v0.1.1

- 补齐五子系统分层目录：`instructions/`、`tools/`、`environment/`、`state/`、`feedback/`。
- 新增 `new-retro.ps1`，将每次任务后的 Harness 复盘纳入默认闭环。
- 升级 docs-only 脚本行为：`restart-compose.ps1` 和 `verify-local.ps1` 支持 `Scope=docs`。
- 将 evidence report 和 retro summary 纳入 `agent-do.ps1` 默认流程。
