# DDD-PRODUCT-001 ProductDomainFacade

Date: 2026-06-10
Env: local real-pre worktree `D:\Projects\SAAS\.worktrees\ddd-product-001`
Branch: `codex/ddd-product-001`

## Task Scope

新增商品域只读门面，供后续订单域、寄样域、业绩域和 BFF 迁移商品上下文读取使用。本任务只建立 Facade / DTO / Legacy implementation，不迁移任何消费者，不改变 Controller、旧 Service 业务路径、数据库或公网 API。

## Facade

- `ProductDomainFacade`
- `LegacyProductDomainFacade`

当前实现委派：

- `ProductService`
- `ProductSnapshotMapper`
- `ProductOperationStateMapper`
- `ColonelsettlementActivityMapper`
- `ColonelPartnerMapper`
- `ColonelPartnerSyncService`

## Methods

- `getActivityProductForSample(UUID relationId)`
- `getRecruiterForActivityProduct(String activityId, String productId)`
- `getProductBrief(String productId)`
- `getActivityBrief(String activityId)`
- `getPartnerBrief(String partnerId)`
- `batchGetProductBrief(Collection<String> productIds)`
- `listPartners(String keyword)`
- `checkProductVisibleForSample(UUID relationId)`
- `getProductOwner(UUID relationId)`
- `getProductDisplayInfo(UUID relationId)`

Note: 任务文本示例使用 `Long relationId`，但当前项目事实为 `product_snapshot.id`，类型是 UUID；Controller、Service、测试和 `BaseEntity.id` 均使用 UUID。本任务按当前仓库事实实现 UUID 版，避免新增不可用合同。

## DTOs

- `ProductBriefDTO`
- `ActivityBriefDTO`
- `PartnerBriefDTO`
- `ActivityProductForSampleDTO`
- `ProductOwnerDTO`
- `ProductDisplayInfoDTO`

## Behavior Compatibility

- 未修改商品库展示规则。
- 未修改活动商品同步逻辑。
- 未修改复制讲解接口。
- 未修改快速寄样接口。
- 未删除或替换旧 `ProductService`。
- `checkProductVisibleForSample` 使用当前商品库/快速寄样只读条件：上游商品推广中、`display_status=DISPLAYING`、`selected_to_library=true`、未本地拒绝、未手动暂停。

## Baseline Test Repair

本 worktree 基线在进入 Product 测试前存在前序 DDD-CONFIG-004 测试编译/运行问题：

- `SysConfigServiceEventTest` import 了不存在的 `com.colonel.saas.BaseIntegrationTest`。
- `SysConfigServiceTest` 未补新增的 `ConfigDomainEventPublisher` 构造参数。
- `LegacyConfigDomainFacadeTest` 未按三参构造创建 `LegacyConfigDomainFacade`，且两个断言未与当前生产默认值/JSON 字段对齐。
- `BaseIntegrationTest` 的复用 Testcontainers schema 未补 `system_config` 新列。

为恢复后端 targeted/full test 可执行性，本任务包含上述测试侧最小兼容修复；不涉及生产配置逻辑。

## Verification

1. Baseline main compile before Product change: `mvn -q -DskipTests compile` PASS.
2. Baseline test compile before repair: FAIL on pre-existing config tests; repaired as above.
3. TDD RED: `mvn -q -Dtest=LegacyProductDomainFacadeTest test` failed because Product Facade / DTO 不存在.
4. Facade targeted test: `mvn -q -Dtest=LegacyProductDomainFacadeTest test` PASS.
5. Product targeted tests: `mvn -q "-Dtest=LegacyProductDomainFacadeTest,ProductServiceFilterTest,ProductServiceLibraryViewTest,ProductServiceActivityAssignTest,QuickSampleApplyTest,ColonelPartnerMasterDataServiceTest" test` PASS.
6. DDD dependency guard: `mvn -q "-Dtest=DddCrossDomainMapperGuardTest" test` PASS.
7. Config baseline repair regression: `mvn -q "-Dtest=LegacyConfigDomainFacadeTest,SysConfigServiceEventTest" test` PASS.
8. Backend full tests: `mvn -q test` PASS (rerun at 2026-06-10 15:02-15:09).
9. Backend package: `mvn -q -DskipTests package` PASS (rerun at 2026-06-10 15:09).
10. Local real-pre backend restart: `restart-compose.ps1 -Env real-pre -Scope backend` PASS.
11. Local backend health: `verify-local.ps1 -Env real-pre -Scope backend` PASS, `/api/system/health` returned `{"status":"UP"}`.

Evidence:

- `harness/reports/evidence-20260610-151108.md`
- `harness/reports/retro-20260610-145540.md`

## Dependency Map

Updated `harness/reports/ddd-dependency-map.md` with DDD-PRODUCT-001 migration entry and follow-up migration order.

## Risk

- No runtime consumer uses `ProductDomainFacade` yet; behavior remains unchanged until later migration tasks.
- `ProductService` remains a large legacy service and still owns display/sync/link/sample-adjacent logic.
- The UUID `relationId` decision is based on current code and API docs; any external plan still expecting Long must be reconciled before consumer migration.
- This task includes test-side repairs for preceding config event changes so full backend tests can run; production Product behavior is unaffected.

## Rollback

Revert this task commit. Rollback removes Product Facade/DTO/test/report/state/dependency-map updates and restores test-side baseline repairs. Because no consumer was migrated and no database migration was added, rollback has no runtime data impact.
