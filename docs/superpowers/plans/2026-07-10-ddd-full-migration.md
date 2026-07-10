# DDD Full Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成代码侧 DDD 未完成项，并按行为等价、兼容委派和证据门禁将 Legacy 核心服务渐进迁移到领域应用层。

**Architecture:** 保留现有 API 和 Legacy façade 作为兼容入口；新代码进入既有 `domain/<context>/application|policy|facade|port|event` 结构。迁移顺序遵循用户/配置边界、订单事实、业绩归属、商品/达人/寄样、事件、前端和 E2E 的依赖关系。

**Tech Stack:** Spring Boot、Java 17、MyBatis-Plus、PostgreSQL、Redis、Docker Compose、Vue 3、TypeScript、Playwright、Maven。

---

## 当前基线

- 当前分支：`codex/ddd-user-role-application`。
- 当前 HEAD：`6598d623`。
- 当前工作区存在用户已有 dirty/untracked 改动，不执行 reset、checkout、清库或覆盖式回滚。
- 批准后重新执行宽口径 DDD 命令：363 tests、0 failures、0 errors、1 skipped。
- `cross-domain-mapper-legacy-whitelist.txt` 和 `architecture-redline-legacy-whitelist.txt` 有效项均为 0。
- real-pre 真实业务闭环仍依赖有效抖音 access token 和真实订单/寄样样本；外部条件不足时保持 BLOCKED。

### Task 1: 固化设计、计划和基线证据

**Files:**
- Create: `docs/superpowers/specs/2026-07-10-ddd-full-migration-design.md`
- Create: `docs/superpowers/plans/2026-07-10-ddd-full-migration.md`
- Modify: `docs/ddd-completion-evidence-matrix.md`
- Modify: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Create/Update: `harness/reports/evidence-20260710-ddd-migration-baseline.md`

- [x] **Step 1: 保存已批准设计和计划**

  设计记录迁移边界、方案选择、外部阻塞和每批验收门禁；计划记录具体代码入口、测试命令和迁移顺序。

- [x] **Step 2: 执行基线测试**

  Run from `backend`:

  ```powershell
  mvn -q -DforkCount=0 test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"
  ```

  Expected current baseline: exit code 0; 363 tests, 0 failures, 0 errors, 1 skipped.

- [ ] **Step 3: 写入基线 evidence 和 retro**

  只记录实际命令输出、dirty 文件计数、图谱覆盖、Docker/real-pre 状态；未采集项写明原因，不把历史报告结果覆盖为当前结果。

- [ ] **Step 4: 仅提交本任务新增 docs**

  ```powershell
  git diff --check
  powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1
  git add -- docs/superpowers/specs/2026-07-10-ddd-full-migration-design.md docs/superpowers/plans/2026-07-10-ddd-full-migration.md
  git commit -m "docs: define ddd migration rollout"
  ```

### Task 2: 修复 E-7 转链事件幂等键透传

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddProductPromotionLinkGeneratedEventContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/ProductDomainEventPublisherTest.java`

- [ ] **Step 1: 加强失败合同测试**

  在 `DddProductPromotionLinkGeneratedEventContractTest` 中同时断言：带 `idempotencyKey` 的 `generatePromotionLink` 路径把该值传入 `generatePromotionLinkInternal`，重新转链和首次转链两个成功分支都把该值传入 `publishPromotionLinkGenerated`；无幂等键的旧入口仍传 `null`。

- [ ] **Step 2: 运行红测**

  ```powershell
  mvn -q -Dtest="DddProductPromotionLinkGeneratedEventContractTest,ProductDomainEventPublisherTest,PromotionLinkCopyIntegrationTest" test
  ```

  Expected before implementation: 新增的 key 透传断言失败。

- [ ] **Step 3: 做最小实现**

  给 `generatePromotionLinkInternal` 增加保留旧签名的委派重载，并在带幂等键的入口调用带 key 的实现；在两个成功分支调用 `publishPromotionLinkGenerated(..., idempotencyKey)`。不改变 HTTP 参数、幂等 scope、outbox event id 或 `pick_source` 语义。

- [ ] **Step 4: 验证并更新 E-7 证据**

  ```powershell
  mvn -q -Dtest="DddProductPromotionLinkGeneratedEventContractTest,ProductDomainEventPublisherTest,PromotionLinkCopyIntegrationTest" test
  mvn -q -DskipTests compile
  mvn -q -Dtest="DddArchitectureRedlineGuardTest" test
  ```

  通过后更新 E-7 为 DONE；若真实 outbox/跨进程证据仍缺失，只在对应卡保留 PARTIAL。

### Task 3: 关闭可本地验证的用户、订单、业绩和配置缺口

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/CommissionRuleService.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/PerformanceController.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAccessScopeClosureContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceCommissionRuleVersionContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceConfigConsumptionContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceUnitClosureContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/user/application/CurrentUserPasswordAuditIntegrationTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncFailureEvidenceMatrixTest.java`

- [ ] **Step 1: 按失败断言建立任务清单**

  只处理本次复跑实际失败的断言；若当前测试已通过，则补充证据矩阵和测试覆盖说明，不为制造改动而改生产代码。

- [ ] **Step 2: 修复提成规则乐观锁**

  `CommissionRuleService.update` 必须检查带 version 条件的 update 影响行数；影响行数为 0 时抛出已有并发冲突异常，不能返回看似成功的对象。补充成功、版本冲突和缺失记录测试。

- [ ] **Step 3: 收口业绩访问上下文**

  `PerformanceController` 只组装 `PerformanceAccessContext`，访问范围解释留在 `PerformanceAccessScope`；补列表、导出、重算、逐条访问和缺失上下文 fail-closed 测试。

- [ ] **Step 4: 验证配置和订单失败证据**

  配置消费只经 `ConfigDomainFacade`；订单同步失败必须记录失败计数、错误码和不产生错误副作用。对应合同测试通过后才更新矩阵状态。

- [ ] **Step 5: 执行组合验证**

  ```powershell
  mvn -q -DforkCount=0 test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"
  mvn -q -DforkCount=0 test "-Dtest=*Order*Test,*Performance*Test"
  ```

### Task 4: ProductService 商品库查询迁移

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/controller/ProductController.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/ProductLibraryPageQueryService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/ProductLibraryApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/dto/ProductLibraryPageQuery.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/product/application/ProductLibraryPageQueryServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductApplicationRoutingTest.java`

- [ ] **Step 1: 固化 Controller → Application 查询入口红测**

  断言商品库分页入口只依赖 `ProductLibraryPageQueryService` / `ProductLibraryApplicationService`，不新增 Controller 到 Mapper、Gateway 或 `ProductService` 的直连。

- [ ] **Step 2: 迁移查询编排**

  将分页参数、筛选语义、total/items/cursor/productId 返回映射保留在现有 query DTO 和 application service；`ProductService` 仅作为兼容委派，不新增查询规则。

- [ ] **Step 3: 执行商品域回归**

  ```powershell
  mvn -q -Dtest="ProductLibraryPageQueryServiceTest,ProductLibraryApplicationServiceTest,DddProduct003ProductApplicationRoutingTest,*Product*Test" test
  ```

### Task 5: ProductService 转链、展示和活动商品剩余迁移

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/ProductDisplayPolicy.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/facade/PromotionLinkRecordFacade.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/ProductServicePromotionPortArchitectureTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddProductPromotionLinkFacadeBoundaryTest.java`

- [ ] **Step 1: 以已有 Port/Façade 为迁移边界**

  转链复制走 `CopyPromotionApplicationService`，推广链接事实读写走 `PromotionLinkRecordFacade`，展示状态解释走 `ProductDisplayPolicy`；禁止把订单归因、业绩归属或寄样状态机放入商品域。

- [ ] **Step 2: 保持旧入口行为等价**

  保留 API、幂等键、`pick_source_mapping` 写入和状态码语义；Legacy `ProductService` 只委派新 application service 或 port implementation。

- [ ] **Step 3: 逐条移除旧调用方**

  用架构守卫和调用方扫描确认 Controller、跨域 service 不再直接依赖商品 Mapper；每清理一个入口即运行商品域定向测试和 redline guard。

### Task 6: SampleApplicationService 读写与状态机迁移

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleStateMachine.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddSampleStateMachineIntegrationClosureContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleStateMachineTest.java`

- [ ] **Step 1: 先迁移查询路径**

  列表、详情、看板和导出由 `SampleQueryApplicationService` 承担；数据范围解释消费用户域稳定出口，寄样域保留负责人、归属部门和状态可见性业务语义。

  - [x] 2026-07-10：列表分页新增 `SamplePageQueryPort` 与 `LegacySamplePageQueryAdapter`，完整筛选和简化分页入口均由 `SampleQueryApplicationService` 统一编排；后续替换适配器内部读模型。

- [ ] **Step 2: 再迁移命令路径**

  申请、删除、审核、发货、物流推进和订单已同步完成判断由 `SampleCommandApplicationService`、`SampleStateMachine` 和 `SampleActionPermissionPolicy` 承担；状态流转非法时保持原异常。

- [ ] **Step 3: 保留 Legacy 委派壳并验证**

  ```powershell
  mvn -q -Dtest="*Sample*Test,DddSampleStateMachineIntegrationClosureContractTest,DddSamplePermissionOverreachNegativeContractTest,DddSampleOrderEventConsumptionClosureContractTest" test
  ```

### Task 7: TalentService 与 OrderSyncService 迁移

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/application/TalentQueryApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentCheckApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/application/OrderSyncApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolver.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/application/OrderAttributionRouter.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddTalentOrderFacadeBoundaryTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncIntegrationClosureContractTest.java`

- [ ] **Step 1: 达人查询、认领和独家评估逐路径委派**

  `TalentService` 保留兼容 API；查询、认领、保护期和独家评估分别委派已有 application service，最终归属仍由业绩域负责。

- [ ] **Step 2: 订单同步拆分编排和持久化**

  `OrderSyncApplicationService` 负责入口、模式和失败边界；`OrderSyncPersistenceService` 负责订单事实、退款事实、去重 claim/bind 和事件发布；不把提成、业绩或寄样规则放回订单域。

- [ ] **Step 3: 运行订单/达人组合回归**

  ```powershell
  mvn -q -Dtest="*Talent*Test,*Order*Test,DddTalentOrderFacadeBoundaryTest,DddOrderSyncIntegrationClosureContractTest" test
  ```

### Task 8: Performance、Analytics、Outbox 和前端收口

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/DashboardService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java`
- Modify: `backend/src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java`
- Modify: `frontend/src/architecture/frontend-business-rule-boundary.test.ts`
- Modify: `frontend/src/services/**`、`frontend/src/stores/**`、相关领域页面
- Modify: `runtime/qa/role-page-cases.json`

- [ ] **Step 1: 分析模块只读 Facade**

  `DataApplicationService` / `DashboardService` 只通过订单、商品、达人、业绩的稳定只读 facade 获取事实或汇总；不重算订单归因和业绩归属。

- [ ] **Step 2: Outbox 完成持久化状态和幂等证据**

  继续保持 PENDING、PROCESSING lock、PUBLISHED、FAILED/retry、DEAD/replay 的状态证据；验证跨进程幂等前不删除本地兼容路由。

- [ ] **Step 3: 前端和 E2E 按领域收口**

  前端禁止第三方 API 直连、SQL 持久化和核心状态机复制；补 full Vitest、admin/group/self 页面、API/SQL 行级数据范围对账和主要业务 E2E。

### Task 9: real-pre 真实闭环与最终清理

**Files:**
- Modify: `docs/ddd-completion-evidence-matrix.md`
- Modify: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Create: `harness/reports/latest-evidence-YYYYMMDD.md`
- Create: `harness/reports/retro-YYYYMMDD.md`
- Modify: `harness/rules/changelog.md`

- [ ] **Step 1: 恢复有效授权并取得真实样本**

  仅在用户提供有效 access token、真实转链/订单/寄样命中条件后执行 real-pre；不启用 mock，不修改真实环境开关绕过验证。

- [ ] **Step 2: 执行三条主链对账**

  验证渠道链、招商链和管理链的 API、SQL、日志、页面结果；订单归因、寄样自动完成、业绩明细/汇总和 Dashboard 必须可追溯到同一批真实事实。

- [ ] **Step 3: 删除旧代码前做零调用方检查**

  只有当新入口测试、宽口径架构守卫、运行态验证和调用方扫描全部通过，才删除对应 Legacy 方法；否则保留兼容壳并明确剩余风险。

- [ ] **Step 4: 最终门禁**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -Message "ddd: final migration acceptance"
  powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -RequireRedlineZero
  powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1
  git diff --check
  ```

  任何 Docker、授权、真实样本或 E2E 缺失项均写成 BLOCKED/PARTIAL；只有证据齐全后才允许声明完成。
